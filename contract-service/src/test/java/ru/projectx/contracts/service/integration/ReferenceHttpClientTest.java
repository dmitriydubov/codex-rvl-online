package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestClient;
import ru.projectx.contracts.config.ReferenceProperties;
import ru.projectx.contracts.dto.ReferenceInsuranceContract;
import ru.projectx.contracts.dto.ReferencePolicy;
import ru.projectx.contracts.dto.ReferenceProgram;
import ru.projectx.contracts.exception.ReferenceArchivedException;
import ru.projectx.contracts.exception.ReferenceKind;
import ru.projectx.contracts.exception.ReferenceNotFoundException;
import ru.projectx.contracts.exception.ReferenceRejectedException;
import ru.projectx.contracts.exception.ReferenceUnavailableException;
import ru.projectx.contracts.exception.ReferenceUnavailableException.Reason;
import ru.projectx.contracts.support.StandExample;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Клиент читает справочники по контракту сервиса справочных данных на его же примерах ответов: поля разбираются как
 * есть, 404 с кодом — окончательный ответ без повтора, 503 и чужие ответы повторяются ограниченно, а полис по
 * идентификатору 1С:УСК ищется повторно и после «не найден». Сети нет: ответы отдаёт MockRestServiceServer.
 */
class ReferenceHttpClientTest {

    private static final String BASE = "http://reference.example/internal/v1";
    private static final int RETRIES = 2;
    private static final int ATTEMPTS = RETRIES + 1;

    private static final UUID PROGRAM_ID = UUID.fromString("7d1f1a26-3a0f-4a4e-8d7f-5a2b3c4d5e6f");
    private static final UUID LPU_ID = UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
    private static final UUID CONTRACT_ID = UUID.fromString("c0ffee00-1111-4222-8333-444455556666");
    private static final UUID INSURER_ID = UUID.fromString("5f6e7d8c-9b0a-4b1c-8d2e-3f4a5b6c7d8e");
    private static final UUID POLICY_ID = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
    private static final UUID POLICY_EXTERNAL_ID = UUID.fromString("9e6ee30d-2275-417d-a44c-039d3b8c2ac5");

    private static final JsonMapper JSON = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    private final ReferenceProperties settings = new ReferenceProperties(BASE, Duration.ofSeconds(2),
            Duration.ofSeconds(7), new ReferenceProperties.Retry(RETRIES, Duration.ZERO));
    private final RestClient.Builder builder = ReferenceRestClients.builder(settings, JSON);
    private final MockRestServiceServer reference = MockRestServiceServer.bindTo(builder).build();
    private final ReferenceHttpClient client =
            new ReferenceHttpClient(builder.build(), ReferenceRetries.of(settings.retry()));

    @Test
    void program_isReadWithNameExternalIdAndServices() {
        reference.expect(once(), requestTo(BASE + "/programs/" + PROGRAM_ID)).andExpect(method(HttpMethod.GET))
                .andRespond(example("05-program.json", Map.of("program_id", PROGRAM_ID.toString(),
                        "lpu_id", LPU_ID.toString())));

        ReferenceProgram program = client.program(PROGRAM_ID);

        reference.verify();
        assertThat(program.id()).isEqualTo(PROGRAM_ID);
        assertThat(program.externalId()).isEqualTo(UUID.fromString("995448e7-dd5b-499a-8a4e-97b363d06dd3"));
        assertThat(program.name()).isEqualTo("Стенд: восстановительное лечение");
        assertThat(program.typeProgramm()).isEqualTo("СКЛ");
        assertThat(program.isArchive()).isFalse();
        assertThat(program.services()).singleElement().satisfies(service -> {
            assertThat(service.lpuId()).isEqualTo(LPU_ID);
            assertThat(service.lpuExternalId()).isEqualTo(UUID.fromString("b515b8fc-d13c-4b0b-aae3-33e0ca86ff1c"));
            assertThat(service.lpuName()).isEqualTo("Санаторий «Стенд»");
            assertThat(service.appealTypeExternalId())
                    .isEqualTo(UUID.fromString("95ca15cf-daed-4c11-9b70-8e5366a97295"));
            assertThat(service.beginDate()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(service.endDate()).isNull();
            assertThat(service.layerNumber()).isEqualTo(1);
            assertThat(service.directAttachment()).isTrue();
            assertThat(service.percentCopayment()).isEqualByComparingTo(new BigDecimal("10.00"));
            assertThat(service.coverageType()).isEqualTo("R");
        });
    }

    @Test
    void insuranceContract_isReadWithNumberExternalIdAndInsurer() {
        reference.expect(once(), requestTo(BASE + "/insurance-contracts/" + CONTRACT_ID))
                .andRespond(example("02-insurance-contract-by-external-id.json",
                        Map.of("insurance_contract_id", CONTRACT_ID.toString(), "insurer_id", INSURER_ID.toString())));

        ReferenceInsuranceContract contract = client.insuranceContract(CONTRACT_ID);

        reference.verify();
        assertThat(contract.id()).isEqualTo(CONTRACT_ID);
        assertThat(contract.externalId()).isEqualTo(UUID.fromString("77ff17b5-e659-4720-b57f-e8a19c00fcea"));
        assertThat(contract.number()).isEqualTo("СТЕНД-ДМС-0001");
        assertThat(contract.beginDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(contract.endDate()).isNull();
        assertThat(contract.isArchive()).isFalse();
        assertThat(contract.insurer()).isNotNull().satisfies(insurer -> {
            assertThat(insurer.id()).isEqualTo(INSURER_ID);
            assertThat(insurer.externalId()).isEqualTo(UUID.fromString("d23f3088-f547-4755-b93e-ae192a89d166"));
            assertThat(insurer.name()).isEqualTo("ООО «Тестовый страхователь»");
            assertThat(insurer.fullName())
                    .isEqualTo("Общество с ограниченной ответственностью «Тестовый страхователь»");
            assertThat(insurer.inn()).isEqualTo("7700000002");
            assertThat(insurer.kpp()).isEqualTo("770001002");
        });
    }

    @Test
    void policy_isReadWithoutPersonalData() {
        reference.expect(once(), requestTo(BASE + "/policies/" + POLICY_ID + "/contract-view"))
                .andRespond(example("10-policy-contract-view.json", Map.of("policy_id", POLICY_ID.toString())));

        ReferencePolicy policy = client.policy(POLICY_ID);

        reference.verify();
        assertThat(policy).isEqualTo(new ReferencePolicy(POLICY_ID, POLICY_EXTERNAL_ID, "СТЕНД-0001",
                LocalDate.of(2026, 1, 1), LocalDate.of(2099, 12, 31), false));
    }

    @Test
    void policyByExternalId_isLookedUpByQueryParameter() {
        reference.expect(once(), requestTo(BASE + "/policies/contract-view?external_id=" + POLICY_EXTERNAL_ID))
                .andRespond(example("09-policy-contract-view-by-external-id.json",
                        Map.of("policy_id", POLICY_ID.toString())));

        ReferencePolicy policy = client.policyByExternalId(POLICY_EXTERNAL_ID);

        reference.verify();
        assertThat(policy.id()).isEqualTo(POLICY_ID);
        assertThat(policy.externalId()).isEqualTo(POLICY_EXTERNAL_ID);
        assertThat(policy.number()).isEqualTo("СТЕНД-0001");
    }

    /** 404 по своему идентификатору — окончательный ответ: записи нет, повторять нечего. */
    @Test
    void policyNotFoundById_isFinalWithoutRetry() {
        reference.expect(once(), requestTo(BASE + "/policies/" + POLICY_ID + "/contract-view"))
                .andRespond(example("11-policy-contract-view-not-found.json", Map.of()));

        assertThatThrownBy(() -> client.policy(POLICY_ID))
                .isInstanceOfSatisfying(ReferenceNotFoundException.class,
                        notFound -> assertThat(notFound.kind()).isEqualTo(ReferenceKind.POLICY));
        reference.verify();
    }

    /** Полис, о котором сообщила 1С:УСК, может ещё идти в справочник: «не найден» повторяется, затем — недоступность. */
    @Test
    void policyNotFoundByExternalId_isRetriedThenReportedAsNotReceived() {
        reference.expect(times(ATTEMPTS), requestTo(BASE + "/policies/contract-view?external_id=" + POLICY_EXTERNAL_ID))
                .andRespond(example("11-policy-contract-view-not-found.json", Map.of()));

        assertThatThrownBy(() -> client.policyByExternalId(POLICY_EXTERNAL_ID))
                .isInstanceOfSatisfying(ReferenceUnavailableException.class,
                        unavailable -> assertThat(unavailable.reason()).isEqualTo(Reason.POLICY_NOT_RECEIVED))
                .hasCauseInstanceOf(ReferenceNotFoundException.class);
        reference.verify();
    }

    @Test
    void policyByExternalId_arrivesOnARetry() {
        reference.expect(once(), requestTo(BASE + "/policies/contract-view?external_id=" + POLICY_EXTERNAL_ID))
                .andRespond(example("11-policy-contract-view-not-found.json", Map.of()));
        reference.expect(once(), requestTo(BASE + "/policies/contract-view?external_id=" + POLICY_EXTERNAL_ID))
                .andRespond(example("09-policy-contract-view-by-external-id.json",
                        Map.of("policy_id", POLICY_ID.toString())));

        assertThat(client.policyByExternalId(POLICY_EXTERNAL_ID).id()).isEqualTo(POLICY_ID);
        reference.verify();
    }

    @Test
    void serviceUnavailable_isRetriedThenReported() {
        reference.expect(times(ATTEMPTS), requestTo(BASE + "/programs/" + PROGRAM_ID))
                .andRespond(example("12-service-unavailable.json", Map.of()));

        assertThatThrownBy(() -> client.program(PROGRAM_ID))
                .isInstanceOfSatisfying(ReferenceUnavailableException.class,
                        unavailable -> assertThat(unavailable.reason()).isEqualTo(Reason.SERVICE_UNAVAILABLE));
        reference.verify();
    }

    @Test
    void serviceUnavailable_recoversOnARetry() {
        reference.expect(once(), requestTo(BASE + "/programs/" + PROGRAM_ID))
                .andRespond(example("12-service-unavailable.json", Map.of()));
        reference.expect(once(), requestTo(BASE + "/programs/" + PROGRAM_ID))
                .andRespond(example("05-program.json", Map.of("program_id", PROGRAM_ID.toString(),
                        "lpu_id", LPU_ID.toString())));

        assertThat(client.program(PROGRAM_ID).id()).isEqualTo(PROGRAM_ID);
        reference.verify();
    }

    /** Договор по архивной записи выпускать нельзя: это ошибка выпуска, а не повод перечитать справочник. */
    @Test
    void archivedRecord_isRejectedWithoutRetry() {
        String archived = StandExample.load("10-policy-contract-view.json")
                .body(Map.of("policy_id", POLICY_ID.toString()))
                .replace("\"is_archive\":false", "\"is_archive\":true");
        reference.expect(once(), requestTo(BASE + "/policies/" + POLICY_ID + "/contract-view"))
                .andRespond(withSuccess(archived, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.policy(POLICY_ID))
                .isInstanceOfSatisfying(ReferenceArchivedException.class, rejected -> {
                    assertThat(rejected.kind()).isEqualTo(ReferenceKind.POLICY);
                    assertThat(rejected.id()).isEqualTo(POLICY_ID);
                });
        reference.verify();
    }

    /** 400 с кодом справочника повторится на тот же запрос: окончательный отказ с кодом, без повтора. */
    @Test
    void clientErrorWithCode_isRejectedWithoutRetry() {
        reference.expect(once(), requestTo(BASE + "/programs/" + PROGRAM_ID))
                .andRespond(example("07-malformed-id.json", Map.of("issued_request_id", "stand-07")));

        assertThatThrownBy(() -> client.program(PROGRAM_ID))
                .isInstanceOfSatisfying(ReferenceRejectedException.class, rejected -> {
                    assertThat(rejected.status()).isEqualTo(400);
                    assertThat(rejected.code()).isEqualTo("VALIDATION_ERROR");
                });
        reference.verify();
    }

    /** Обрезанный JSON при успешном статусе — ответ, которому нельзя верить: недоступность с повтором. */
    @Test
    void truncatedJsonBody_isUnexpectedResponse() {
        reference.expect(times(ATTEMPTS), requestTo(BASE + "/programs/" + PROGRAM_ID))
                .andRespond(withSuccess("{\"id\":", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.program(PROGRAM_ID))
                .isInstanceOfSatisfying(ReferenceUnavailableException.class,
                        unavailable -> assertThat(unavailable.reason()).isEqualTo(Reason.UNEXPECTED_RESPONSE));
        reference.verify();
    }

    @Test
    void htmlErrorPage_isUnexpectedResponse() {
        reference.expect(times(ATTEMPTS), requestTo(BASE + "/programs/" + PROGRAM_ID))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.TEXT_HTML).body("<html>503 Service Unavailable</html>"));

        assertThatThrownBy(() -> client.program(PROGRAM_ID))
                .isInstanceOfSatisfying(ReferenceUnavailableException.class,
                        unavailable -> assertThat(unavailable.reason()).isEqualTo(Reason.UNEXPECTED_RESPONSE));
        reference.verify();
    }

    @Test
    void successWithoutJsonBody_isUnexpectedResponse() {
        reference.expect(times(ATTEMPTS), requestTo(BASE + "/programs/" + PROGRAM_ID))
                .andRespond(withSuccess("<html>proxy</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.program(PROGRAM_ID))
                .isInstanceOfSatisfying(ReferenceUnavailableException.class,
                        unavailable -> assertThat(unavailable.reason()).isEqualTo(Reason.UNEXPECTED_RESPONSE));
        reference.verify();
    }

    /** Разбор JSON не замечает чужих имён полей: запись без идентификаторов — ответ не по контракту, а не пустая запись. */
    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"id\":\"1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f\",\"externalId\":\"9e6ee30d-2275-417d-a44c-039d3b8c2ac5\","
            + "\"number\":\"СТЕНД-0001\",\"beginDate\":\"2026-01-01\",\"isArchive\":false}",
        "{\"external_id\":\"9e6ee30d-2275-417d-a44c-039d3b8c2ac5\",\"number\":\"СТЕНД-0001\",\"is_archive\":false}"})
    void recordWithoutIdentifiers_isUnexpectedResponse(String body) {
        reference.expect(times(ATTEMPTS), requestTo(BASE + "/policies/" + POLICY_ID + "/contract-view"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.policy(POLICY_ID))
                .isInstanceOfSatisfying(ReferenceUnavailableException.class,
                        unavailable -> assertThat(unavailable.reason()).isEqualTo(Reason.UNEXPECTED_RESPONSE));
        reference.verify();
    }

    @Test
    void successWithEmptyBody_isUnexpectedResponse() {
        reference.expect(times(ATTEMPTS), requestTo(BASE + "/programs/" + PROGRAM_ID)).andRespond(withSuccess());

        assertThatThrownBy(() -> client.program(PROGRAM_ID))
                .isInstanceOfSatisfying(ReferenceUnavailableException.class,
                        unavailable -> assertThat(unavailable.reason()).isEqualTo(Reason.UNEXPECTED_RESPONSE));
        reference.verify();
    }

    private static DefaultResponseCreator example(String name, Map<String, String> placeholders) {
        StandExample example = StandExample.load(name);
        return withStatus(HttpStatus.valueOf(example.status()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(example.body(placeholders));
    }
}
