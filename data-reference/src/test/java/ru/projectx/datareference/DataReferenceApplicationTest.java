package ru.projectx.datareference;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * Контекст поднимается и Liquibase накатывает схему.
 *
 * <p>Тест выглядит бессодержательным, но ловит ровно тот класс поломок, который иначе
 * обнаруживается только на стенде: несогласованные бины, невалидная конфигурация,
 * changeSet, который не применяется.
 */
@IntegrationTest
class DataReferenceApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvcTester mvc;

    @Test
    void contextLoads() {
        // Достаточно самого факта старта: падение произойдёт до тела теста.
    }

    /**
     * В контексте без брокера машинерии AMQP быть не должно — и проверяется это здесь,
     * а не читается в аннотации, потому что цена ошибки высокая и отложенная.
     *
     * <p>Свойством {@code auto-startup=false} слушателей не остановить: реестр стартует
     * контейнер при {@code contextRefreshed || isAutoStartup()}, а флаг взводится сразу
     * после первого refresh. Spring перезапускает кэшированные контексты между тестовыми
     * классами, и на перезапуске слушатели поднимались вопреки свойству — шли на брокер
     * по умолчанию и роняли чужой тестовый класс сообщением про несуществующую очередь.
     * Нет фабрики соединений — нечему стартовать ни при каком перезапуске.
     */
    @Test
    void contextWithoutBroker_hasNoAmqpMachinery() {
        assertThat(context.getBeanNamesForType(ConnectionFactory.class)).isEmpty();
        assertThat(context.getBeanNamesForType(RabbitListenerEndpointRegistry.class)).isEmpty();
    }

    /**
     * Пробы Kubernetes отвечают на том же порту, что и API, и говорят о состоянии
     * самого приложения. Открыт только health: остальные ручки актуатора наружу
     * не публикуются, и это проверяется, потому что открыть их — одна строка конфигурации.
     */
    @Test
    void kubernetesProbes_answerUp_andOtherActuatorEndpointsAreNotExposed() {
        assertThat(mvc.get().uri("/actuator/health/liveness").exchange())
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("UP");
        assertThat(mvc.get().uri("/actuator/health/readiness").exchange())
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("UP");
        assertThat(mvc.get().uri("/actuator/env").exchange()).hasStatus(404);
    }

    /**
     * Prometheus забирает метрики приёма и разбора с той же ручки, что и метрики самого Spring Boot. Ряды
     * приёма и разбора есть сразу после старта, ещё до первого сообщения: нулевой ряд для правил оповещения
     * однозначнее отсутствующего. Ряд переходов взят у потока видов обращения: контекст общий с другими
     * тестами, а сообщения этого потока они не отклоняют. Метрика слушателей очередей Spring AMQP здесь не
     * проверяется: в этом контексте брокера нет.
     */
    @Test
    void prometheusEndpoint_exposesIngestAndParsingMetrics() {
        assertThat(mvc.get().uri("/actuator/prometheus").exchange())
                .hasStatusOk()
                .bodyText()
                .contains("onec_ingest_deliveries_total{outcome=\"unidentified\",queue=\"1c-usk_to_rvl-online_policies\","
                        + "stream=\"POLICIES\"} 0.0")
                .contains("onec_ingest_pauses_total 0.0")
                .contains("onec_ingest_paused 0.0")
                .contains("onec_inbound_transitions_total{status=\"FAILED\",stream=\"APPEAL_TYPES\"} 0.0")
                .contains("onec_inbound_messages{status=\"NEW\",stream=\"POLICIES\"} 0.0")
                .contains("onec_inbound_pending_oldest_age_seconds{stream=\"PRICE_LISTS\"} 0.0")
                .contains("onec_price_transfers_stuck 0.0")
                .contains("onec_retention_overdue_policy_payloads 0.0")
                .contains("onec_retention_overdue_quarantine_bodies 0.0")
                .contains("# TYPE onec_inbound_backlog_staleness_seconds gauge")
                .contains("# HELP onec_inbound_messages Сырые сообщения 1С по потоку и статусу")
                .contains("hikaricp_connections");
    }
}
