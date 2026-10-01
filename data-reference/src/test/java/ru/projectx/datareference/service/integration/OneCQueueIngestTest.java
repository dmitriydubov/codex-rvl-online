package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static ru.projectx.datareference.support.OneCMessages.mkb10Message;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.datareference.config.OneCQueueProperties;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.repository.InboxRepository;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.support.AmqpIntegrationTest;
import ru.projectx.datareference.support.DatabaseCleaner;
import tools.jackson.databind.ObjectMapper;

/**
 * Путь «очередь → слушатель → таблица сырья» на настоящем брокере и настоящей базе.
 *
 * <p>Проверяется именно связка: что имя очереди из конфигурации совпадает с тем,
 * на что подписан слушатель, что тело доезжает до таблицы неискажённым и что поток
 * определяется очередью, из которой сообщение пришло. Ни одно из этого не видно
 * ни на моках, ни в тесте разбора.
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

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    /**
     * Без работающих слушателей всё ниже — ожидание, которое ничем не кончится:
     * сообщение ляжет в очередь, и забирать его будет некому. Тогда тест падает
     * через 15 секунд с «условие не выполнено», не говоря, что не так. Проверка
     * заранее называет причину — контейнеров нет или они не запущены.
     */
    @BeforeEach
    void listenersAreRunning() {
        assertThat(listeners.getListenerContainerIds())
                .as("контейнеры слушателей очередей 1С")
                .isNotEmpty()
                .allSatisfy(id -> assertThat(listeners.getListenerContainer(id).isRunning())
                        .as("слушатель %s не запущен: auto-startup выключен или контейнер остановлен", id)
                        .isTrue());
    }

    @Test
    void messageFromQueue_isStoredAsRawWithItsStream() {
        UUID messageId = UUID.randomUUID();
        String body = mkb10Message(messageId);

        rabbitTemplate.convertAndSend(queues.mkb10(), body);

        InboundMessage stored = awaitStored(messageId);
        assertThat(stored.stream()).isEqualTo(OneCStream.MKB10);
        assertThat(stored.sourceQueue()).isEqualTo(queues.mkb10());
        assertThat(stored.status()).isEqualTo(InboundStatus.NEW);
        assertThat(inboxRepository.findById(messageId)).isPresent();
    }

    /**
     * Тело сохраняется целиком, а не разобранным: починить маппер и переобработать
     * сообщение можно только по нему. Сравнение деревьями, а не байтами, — свойство
     * колонки {@code jsonb}: она хранит документ, а не текст, и пробелы с порядком
     * ключей при чтении не сохраняются.
     */
    @Test
    void storedPayload_isTheWholeMessage() {
        UUID messageId = UUID.randomUUID();
        String body = mkb10Message(messageId);

        rabbitTemplate.convertAndSend(queues.mkb10(), body);

        assertThat(json.readTree(awaitStored(messageId).payload())).isEqualTo(json.readTree(body));
    }

    /**
     * Доставка из RabbitMQ — at-least-once, и повтор здесь не гипотетический: любое
     * сообщение, по которому мы не успели подтвердить получение, придёт снова.
     *
     * <p>Главное здесь — что очередь <b>поехала дальше</b>. Второй строки под тем же
     * ключом не появится и без дедупликации: {@code message_id} — первичный ключ, вторая
     * вставка упала бы на нём. А вот последствия у этого падения ровно те, которые
     * проверяет ожидание третьего сообщения: слушатель вернул бы дубль в очередь,
     * получил бы его снова, и следующее сообщение не приехало бы никогда.
     */
    @Test
    void redeliveredMessage_isStoredOnce() {
        UUID repeated = UUID.randomUUID();
        UUID next = UUID.randomUUID();

        rabbitTemplate.convertAndSend(queues.mkb10(), mkb10Message(repeated));
        rabbitTemplate.convertAndSend(queues.mkb10(), mkb10Message(repeated));
        rabbitTemplate.convertAndSend(queues.mkb10(), mkb10Message(next));

        // Потребитель у очереди один, порядок сохраняется: дождались последнего —
        // значит, оба повтора уже разобраны.
        awaitStored(next);
        assertThat(inboundMessageRepository.findById(repeated)).isPresent();
        assertThat(inboxRepository.findById(repeated)).isPresent();
    }

    /**
     * Договоры страхования — очередь, чьё имя дольше других расходилось с брокером:
     * в ТЗ через {@code rvl_online}, на стенде через дефис. Тест проверяет всю цепочку
     * разом: свойство прочиталось, слушатель подписался, поток проставился тот.
     */
    @Test
    void insuranceContractQueue_isBoundToItsOwnStream() {
        UUID messageId = UUID.randomUUID();

        rabbitTemplate.convertAndSend(queues.insuranceContracts(), mkb10Message(messageId));

        assertThat(awaitStored(messageId))
                .extracting(InboundMessage::stream, InboundMessage::sourceQueue)
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

        rabbitTemplate.convertAndSend(queues.priceLists(), mkb10Message(batch));
        rabbitTemplate.convertAndSend(queues.priceListsFinal(), mkb10Message(last));

        assertThat(awaitStored(batch))
                .extracting(InboundMessage::stream, InboundMessage::sourceQueue)
                .containsExactly(OneCStream.PRICE_LISTS, queues.priceLists());
        assertThat(awaitStored(last))
                .extracting(InboundMessage::stream, InboundMessage::sourceQueue)
                .containsExactly(OneCStream.PRICE_LISTS, queues.priceListsFinal());
    }

    private InboundMessage awaitStored(UUID messageId) {
        await().atMost(DELIVERY_TIMEOUT)
                .until(() -> inboundMessageRepository.findById(messageId).isPresent());
        return inboundMessageRepository.findById(messageId).orElseThrow();
    }
}
