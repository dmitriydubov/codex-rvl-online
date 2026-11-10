package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Тогда;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.jdbc.core.simple.JdbcClient;

public class PaymentPrivacySteps {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Map<String, String> STORED_TEXTS = Map.of(
            "журнал входящих сообщений",
            "select count(*) from inbox_messages"
                    + " where position(:value in concat_ws('|', payload::text, error_message)) > 0",
            "журнал исходящих сообщений",
            "select count(*) from outbox_messages"
                    + " where position(:value in concat_ws('|', payload::text, error_message)) > 0",
            "записи акцепта",
            "select count(*) from contract_acceptances a where position(:value in row_to_json(a)::text) > 0");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final String managementBase;
    private final ServiceJournal journal;
    private final JdbcClient jdbc;

    public PaymentPrivacySteps(@LocalManagementPort int managementPort, ServiceJournal journal, JdbcClient jdbc) {
        this.managementBase = "http://localhost:" + managementPort;
        this.journal = journal;
        this.jdbc = jdbc;
    }

    @Тогда("ссылка на оплату {string} не попала в журнал Договоров")
    public void paymentLinkStaysOutOfTheJournal(String link) {
        assertThat(link).as("ссылка на оплату").isNotBlank();
        assertThat(journal.events()).as("журнал за сценарий").isNotEmpty();
        assertThat(journal.mentioning(link)).as("записи журнала со ссылкой на оплату").isEmpty();
    }

    @Тогда("адрес плательщика {string} не попал ни в журнал Договоров, ни в метрики, ни в здоровье, ни в журналы входящих и исходящих сообщений, ни в запись акцепта")
    public void payerAddressStaysInTheOrder(String address) throws IOException, InterruptedException {
        assertThat(address).as("адрес плательщика").isNotBlank();
        assertThat(journal.events()).as("журнал за сценарий").isNotEmpty();
        assertThat(journal.mentioning(address)).as("записи журнала с адресом плательщика").isEmpty();
        assertThat(get("/actuator/prometheus")).as("выдача метрик").isNotBlank().doesNotContain(address);
        assertThat(get("/actuator/health")).as("ответ о здоровье").isNotBlank().doesNotContain(address);
        STORED_TEXTS.forEach((where, query) -> assertThat(jdbc.sql(query).param("value", address).query(Long.class).single())
                .as("%s с адресом плательщика", where).isZero());
    }

    private String get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(managementBase + path)).timeout(TIMEOUT).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }
}
