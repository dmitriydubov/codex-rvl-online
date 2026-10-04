package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.service.onec.OneCGuid;
import ru.projectx.datareference.service.schedulers.PersonalDataRetention;

/**
 * Шаги о карантине сообщений 1С:УСК и его сроке хранения.
 *
 * <p>Срок хранения — настройка сервиса; сценарий закрепляет границу: запись на час старше срока стирается,
 * на час моложе — нет. Проход стирания — тот же, что стирает тела полисов, вызванный явно.
 */
public class QuarantineSteps {

    /** Запас на доставку через брокер и запись карантина; ожидание по условию, а не сон. */
    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Map<String, UnaryOperator<Duration>> AGE_AROUND_TTL = Map.of(
            "больше", ttl -> ttl.plus(HOUR),
            "меньше", ttl -> ttl.minus(HOUR));
    private static final Map<String, Boolean> BODY_STATE = Map.of("стёрто", true, "сохранено", false);

    private final QuarantineRows quarantine;
    private final OneCBroker broker;
    private final PersonalDataRetention retention;
    private final ImportProperties properties;

    public QuarantineSteps(
            QuarantineRows quarantine, OneCBroker broker, PersonalDataRetention retention, ImportProperties properties) {

        this.quarantine = quarantine;
        this.broker = broker;
        this.retention = retention;
        this.properties = properties;
    }

    /**
     * Причина сравнивается дословно: в ней ровно код SQLSTATE и нет текста ошибки базы. Запись появляется после
     * доставки через брокер, поэтому шаг ждёт её, а не читает немедленно.
     */
    @Тогда("сообщение {string} лежит в карантине очереди {string} с причиной {string}")
    public void quarantinedWithReason(String messageId, String stream, String reason) {
        await().atMost(DELIVERY_TIMEOUT).untilAsserted(
                () -> assertThat(quarantine.rowsOf(canonical(messageId))).as("карантинная запись %s", messageId)
                        .singleElement()
                        .satisfies(row -> {
                            assertThat(row.sourceQueue()).as("очередь-источник").isEqualTo(broker.queueOf(stream));
                            assertThat(row.reason()).as("причина").isEqualTo(reason);
                        }));
    }

    @Тогда("тело карантинной записи {string} — опубликованное тело байт в байт")
    public void bodyIsThePublishedOne(String messageId) {
        UUID id = canonical(messageId);
        assertThat(quarantine.rowsOf(id)).singleElement()
                .satisfies(row -> assertThat(row.body()).as("тело карантинной записи").isEqualTo(broker.publishedBody(id)));
    }

    @Тогда("тело карантинной записи {string} {word}")
    public void bodyState(String messageId, String state) {
        assertThat(BODY_STATE).as("состояния тела, которые знает шаг").containsKey(state);
        assertThat(quarantine.rowsOf(canonical(messageId))).singleElement()
                .satisfies(row -> assertThat(row.body() == null).as("тело стёрто").isEqualTo(BODY_STATE.get(state)));
    }

    @Тогда("в карантине ровно одна запись")
    public void exactlyOneRow() {
        assertThat(quarantine.count()).as("записей в карантине").isEqualTo(1);
    }

    @Тогда("в карантине нет записей")
    public void noRows() {
        assertThat(quarantine.count()).as("записей в карантине").isZero();
    }

    @Дано("с момента приёма карантинной записи {string} прошло на час {word} срока хранения")
    public void agedAroundTtl(String messageId, String moreOrLess) {
        assertThat(AGE_AROUND_TTL).as("давность, которую умеет изображать шаг").containsKey(moreOrLess);
        quarantine.age(canonical(messageId), AGE_AROUND_TTL.get(moreOrLess).apply(properties.quarantineTtl()));
    }

    @Когда("Дата-референс выполняет проход стирания персональных данных")
    public void retentionPass() {
        retention.forgetExpiredPayloads();
    }

    private static UUID canonical(String messageId) {
        return OneCGuid.parse(messageId).orElseThrow(
                () -> new IllegalArgumentException("message-id в сценарии — не канонический GUID: " + messageId));
    }
}
