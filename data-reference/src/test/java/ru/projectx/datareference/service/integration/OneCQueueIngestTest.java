package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static ru.projectx.datareference.support.OneCMessages.messageWithId;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.datareference.config.OneCQueueProperties;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.repository.InboxRepository;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.support.AmqpIntegrationTest;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.RunningListeners;
import tools.jackson.databind.ObjectMapper;

/**
 * Защищает путь «очередь → слушатель → таблица сырья» на настоящих брокере и базе: имя очереди из конфигурации
 * совпадает с подпиской слушателя, тело доезжает неискажённым, а поток определяется очередью. Ничего из этого не
 * видно ни на моках, ни в тесте разбора.
 */
@AmqpIntegrationTest
class OneCQueueIngestTest {

    /**
     * Запас на доставку и запись. Ожидание по условию, а не сон: сколько именно займёт
     * круг «публикация — доставка — коммит», не знает никто, а фиксированная пауза
     * либо тормозит прогон, либо однажды не дожидается.
     */
    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OneCQueueProperties queues;

    @Autowired
    private InboundMessageRepository inboundMessageRepository;

    @Autowired
    private InboxRepository inboxRepository;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private RabbitListenerEndpointRegistry listeners;

    @Autowired
    private OneCQueueConsumers consumers;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    /**
     * Без работающих слушателей тест упал бы через 15 секунд с «условие не выполнено», не сказав, что не так.
     * Проверка заранее называет причину: контейнеров нет или они не запущены.
     */
    @BeforeEach
    void listenersAreRunning() {
        RunningListeners.assertAllRunning(listeners);
    }

    /**
     * Контейнеров ровно столько, сколько очередей в конфигурации, и их идентификаторы — имена очередей. Лишний
     * контейнер — это второй потребитель какой-то очереди (например, случайный {@code @RabbitListener}), и он
     * перемешал бы порядок потока; недостающий — очередь, которую никто не читает.
     */
    @Test
    void listenerContainers_areExactlyTheElevenQueues() {
        assertThat(listeners.getListenerContainerIds()).containsExactlyInAnyOrder(
                queues.policies(), queues.insuranceContracts(), queues.contragents(), queues.mkb10(),
                queues.lpuContracts(), queues.programs(), queues.appealTypes(), queues.lpuContacts(),
                queues.priceLists(), queues.priceListsFinal(), queues.contragentServices());
    }

    /**
     * Компонент здоровья приёма узнаёт о подписке потребителей из подтверждений брокера, а об их остановке и замене —
     * из известий простого контейнера слушателя: другой тип контейнера их не публикует. Поэтому проверяется, что
     * контейнеры именно такие и что на настоящем брокере подписка каждой очереди подтверждена.
     */
    @Test
    void everyQueueConsumer_isSubscribedThroughSimpleContainer() {
        assertThat(listeners.getListenerContainers()).allSatisfy(
                container -> assertThat(container).isInstanceOf(SimpleMessageListenerContainer.class));
        await().atMost(DELIVERY_TIMEOUT).untilAsserted(
                () -> assertThat(consumers.queuesWithoutConsumer()).as("очереди без подписанного потребителя").isEmpty());
    }

    @Test
    void messageFromQueue_isStoredAsRawWithItsStream() {
        UUID messageId = UUID.randomUUID();
        String body = messageWithId(messageId);

        rabbitTemplate.convertAndSend(queues.mkb10(), body);

        InboundMessage stored = awaitStored(messageId);
        assertThat(stored.receipt().stream()).isEqualTo(OneCStream.MKB10);
        assertThat(stored.receipt().sourceQueue()).isEqualTo(queues.mkb10());
        assertThat(stored.outcome().status()).isEqualTo(InboundStatus.NEW);
        assertThat(inboxRepository.findById(messageId)).isPresent();
    }

    /**
     * Тело сохраняется целиком, потому что только по нему можно починить маппер и переобработать сообщение.
     * Сравнение идёт деревьями, а не байтами: {@code jsonb} хранит документ, а не текст, и не сохраняет пробелы
     * и порядок ключей.
     */
    @Test
    void storedPayload_isTheWholeMessage() {
        UUID messageId = UUID.randomUUID();
        String body = messageWithId(messageId);

        rabbitTemplate.convertAndSend(queues.mkb10(), body);

        assertThat(json.readTree(awaitStored(messageId).payload())).isEqualTo(json.readTree(body));
    }

    /**
     * RabbitMQ доставляет «хотя бы один раз», и неподтверждённое сообщение придёт снова. Главное здесь, что очередь
     * поехала дальше: без дедупликации вторая вставка упала бы на первичном ключе, слушатель вернул бы дубль в очередь,
     * и следующее сообщение не приехало бы никогда.
     */
    @Test
    void redeliveredMessage_isStoredOnce() {
        UUID repeated = UUID.randomUUID();
        UUID next = UUID.randomUUID();

        rabbitTemplate.convertAndSend(queues.mkb10(), messageWithId(repeated));
        rabbitTemplate.convertAndSend(queues.mkb10(), messageWithId(repeated));
        rabbitTemplate.convertAndSend(queues.mkb10(), messageWithId(next));

        // Потребитель у очереди один, порядок сохраняется: дождались последнего —
        // значит, оба повтора уже разобраны.
        awaitStored(next);
        assertThat(inboundMessageRepository.findById(repeated)).isPresent();
        assertThat(inboxRepository.findById(repeated)).isPresent();
    }

    /**
     * Имя очереди договоров страхования на брокере пишется через дефис, а не через {@code rvl_online}, как в
     * документах 1С. Тест проверяет всю цепочку разом: свойство прочиталось, слушатель подписался, поток верный.
     */
    @Test
    void insuranceContractQueue_isBoundToItsOwnStream() {
        UUID messageId = UUID.randomUUID();

        rabbitTemplate.convertAndSend(queues.insuranceContracts(), messageWithId(messageId));

        assertThat(awaitStored(messageId))
                .extracting(inbound -> inbound.receipt().stream(), inbound -> inbound.receipt().sourceQueue())
                .containsExactly(OneCStream.INSURANCE_CONTRACTS, queues.insuranceContracts());
    }

    /**
     * Прайс приходит двумя очередями — пакеты строк и финальное сообщение со счётчиками.
     * Поток у них общий, различает их только очередь, и сборка передачи (ТЗ-9) опирается
     * именно на неё.
     */
    @Test
    void priceListQueues_shareStreamButKeepTheirOwnQueue() {
        UUID batch = UUID.randomUUID();
        UUID last = UUID.randomUUID();

        rabbitTemplate.convertAndSend(queues.priceLists(), messageWithId(batch));
        rabbitTemplate.convertAndSend(queues.priceListsFinal(), messageWithId(last));

        assertThat(awaitStored(batch))
                .extracting(inbound -> inbound.receipt().stream(), inbound -> inbound.receipt().sourceQueue())
                .containsExactly(OneCStream.PRICE_LISTS, queues.priceLists());
        assertThat(awaitStored(last))
                .extracting(inbound -> inbound.receipt().stream(), inbound -> inbound.receipt().sourceQueue())
                .containsExactly(OneCStream.PRICE_LISTS, queues.priceListsFinal());
    }

    private InboundMessage awaitStored(UUID messageId) {
        await().atMost(DELIVERY_TIMEOUT)
                .until(() -> inboundMessageRepository.findById(messageId).isPresent());
        return inboundMessageRepository.findById(messageId).orElseThrow();
    }
}
