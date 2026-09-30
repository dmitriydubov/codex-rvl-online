package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.schedulers.ReferenceImportScheduler;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;
import ru.projectx.datareference.support.StandExample;
import ru.projectx.datareference.support.StandPlaceholders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Стенд потребителя для сервиса Договоров ({@code docker-compose.yml}, профиль {@code stand})
 * на тех же файлах, что и сам стенд: закладка сырья {@code stand/seed.sql} и примеры
 * запросов {@code stand/examples}.
 *
 * <p>Путь данных — как на стенде: сырьё ложится в {@code inbound_message} со статусом NEW,
 * разбирает его штатный проход планировщика, ответы идут через настоящий стек MVC.
 * Пример, разошедшийся с ответом сервиса хоть одним полем, роняет тест — так примеры
 * остаются контрактом, а не устаревшей документацией: шаг, меняющий ответ, правит
 * и пример.
 */
@IntegrationTest
class ConsumerStandTest {

    private static final String SEED = "stand/seed.sql";
    private static final JsonMapper JSON = JsonMapper.shared();

    private static final String SANATORIUM_MESSAGE = "404fa95b-18c4-4ca2-b57a-083c42204acd";
    private static final String INSURER_MESSAGE = "9d180fa9-eefc-45e2-bf18-f6f275710a84";
    private static final String CONTRACT_MESSAGE = "16136d1e-39fe-4b1f-9eca-bb90d7f2c5db";
    private static final String PROGRAM_MESSAGE = "a1960bab-85db-47b4-bc2b-0cc4beb976a1";
    private static final String POLICY_MESSAGE = "fb9a7691-a49f-40a7-a675-715a4ba94617";

    private static final List<String> EXAMPLES = List.of(
            "01-policy-by-external-id.json",
            "02-insurance-contract-by-external-id.json",
            "03-policy.json",
            "04-insurance-contract.json",
            "05-program.json",
            "06-policy-not-found.json",
            "07-malformed-id.json",
            "08-insurance-contract-without-key.json",
            "09-policy-contract-view-by-external-id.json",
            "10-policy-contract-view.json");
    private static final List<String> REQUIRED_SECTIONS = List.of(
            "/description", "/placeholders", "/request/method", "/request/path", "/request/headers",
            "/response/status", "/response/headers", "/response/body");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ReferenceImportScheduler scheduler;

    @Autowired
    private InboundMessageRepository inboundMessages;

    @BeforeEach
    void seedAndImport() {
        databaseCleaner.clear();
        seed();
        scheduler.importPendingMessages();
    }

    /**
     * Страхователь — пропуск с причиной, а не ошибка: так ТЗ-3 отличает «санаторием
     * не стал» от «санатория не присылали» (contract-service/CLAUDE.md), строка
     * в {@code insurers} при этом создана.
     */
    @Test
    void seed_isImportedByScheduler() {
        assertThat(inboundMessages.findAll())
                .extracting(message -> message.messageId().toString(), InboundMessage::status)
                .containsExactlyInAnyOrder(
                        tuple(SANATORIUM_MESSAGE, InboundStatus.PROCESSED),
                        tuple(INSURER_MESSAGE, InboundStatus.SKIPPED),
                        tuple(CONTRACT_MESSAGE, InboundStatus.PROCESSED),
                        tuple(PROGRAM_MESSAGE, InboundStatus.PROCESSED),
                        tuple(POLICY_MESSAGE, InboundStatus.PROCESSED));
        assertThat(inboundMessages.findAll())
                .filteredOn(message -> message.status() == InboundStatus.SKIPPED)
                .singleElement()
                .extracting(InboundMessage::lastError)
                .asString()
                .contains("справочник страхователей");
    }

    @Test
    void reseeding_neitherDuplicatesNorReopensMessages() {
        seed();

        assertThat(inboundMessages.findAll())
                .hasSize(5)
                .noneMatch(message -> message.status() == InboundStatus.NEW);
    }

    /**
     * Набор примеров закреплён поимённо: пропавший или переименованный пример иначе
     * просто перестал бы проверяться, а стенд — показывать то, что обещано Договорам.
     */
    @Test
    void examples_coverTheConsumerContract() {
        assertThat(StandExample.loadAll())
                .extracting(StandExample::name)
                .containsExactlyElementsOf(EXAMPLES);
    }

    /**
     * Пример без запроса или без ответа сравнивать не с чем: отсутствующее тело сошлось бы
     * с пустым ответом. Раздел {@code placeholders} — описание для читателя, и оно обязано
     * быть полным.
     */
    @Test
    void examples_areComplete() {
        assertThat(StandExample.loadAll()).allSatisfy(example -> {
            assertThat(REQUIRED_SECTIONS)
                    .as(example.name())
                    .noneMatch(section -> example.root().at(section).isMissingNode());
            assertThat(example.describedPlaceholders())
                    .as(example.name())
                    .containsExactlyInAnyOrderElementsOf(StandPlaceholders.namesIn(example.text()));
        });
    }

    @TestFactory
    Stream<DynamicTest> examples_matchLiveResponses() {
        StandPlaceholders placeholders = new StandPlaceholders();
        return StandExample.loadAll().stream()
                .map(example -> dynamicTest(example.name(), () -> verify(example, placeholders)));
    }

    private void verify(StandExample example, StandPlaceholders placeholders) throws UnsupportedEncodingException {
        MvcTestResult result = mvc.method(example.method())
                .uri(placeholders.resolve(example.path()))
                .headers(headers -> example.requestHeaders().forEach(headers::add))
                .exchange();

        assertThat(result).hasStatus(example.status());
        example.responseHeaders().forEach((name, expected) -> {
            String actual = result.getResponse().getHeader(name);
            placeholders.bindValue(expected, String.valueOf(actual));
            assertThat(actual).as("заголовок %s", name).isEqualTo(placeholders.resolve(expected));
        });

        JsonNode body = JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        placeholders.bind(example.body(), body);
        assertThat(body).isEqualTo(placeholders.resolve(example.body()));
    }

    private void seed() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(SEED));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.execute(dataSource);
    }
}
