package ru.projectx.contracts.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.projectx.contracts.dto.AcceptanceRequest;
import ru.projectx.contracts.dto.AcceptanceResponse;
import ru.projectx.contracts.dto.DocumentState;
import ru.projectx.contracts.entity.contract.AcceptanceChannel;
import ru.projectx.contracts.exception.ContractAlreadyAcceptedException;
import ru.projectx.contracts.exception.ContractBusyException;
import ru.projectx.contracts.exception.ContractNotFoundException;
import ru.projectx.contracts.exception.ContractStateException;
import ru.projectx.contracts.exception.DocumentVersionMismatchException;
import ru.projectx.contracts.service.RequestIds;
import ru.projectx.contracts.service.acceptance.OfferAcceptances;
import ru.projectx.contracts.service.storage.AttachmentMetrics;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class ContractAcceptanceControllerTest {

    private static final UUID CONTRACT = UUID.fromString("3f77b1c8-2a55-4c19-9e30-6b8d2f0a71c4");
    private static final String PATH = "/internal/v1/contracts/{contract}/acceptance";
    private static final String TRACE = "a41f8c02-7d3b-4e19-9a55-2c7e0b6f1d84";
    private static final String BODY = """
            {"contract_version": 1, "accepted_by": "b0a1c7e4-52d9-4f31-8c06-7e3a9b1d2f45",
             "accepted_at": "2026-02-10T10:04:58+03:00", "channel": "WEB", "ip_address": "10.20.30.40",
             "user_agent": "Mozilla/5.0 (Стенд)"}""";

    private final OfferAcceptances acceptances = mock(OfferAcceptances.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ContractAcceptanceController(acceptances))
            .setControllerAdvice(new AcceptanceErrorAdvice(), new JsonBodyErrorAdvice(),
                    new RequestFormatAdvice(new AttachmentMetrics(new SimpleMeterRegistry())), new DomainErrorAdvice(),
                    new UnexpectedErrorAdvice())
            .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()))
            .addFilters(new RequestIdFilter())
            .build();

    @Test
    void acceptance_isPassedToTheServiceAndAnsweredWithoutPayment() throws Exception {
        when(acceptances.accept(eq(CONTRACT), any()))
                .thenReturn(new AcceptanceResponse(CONTRACT, DocumentState.ACCEPTED, Instant.parse("2026-02-10T07:04:58Z")));

        accept(CONTRACT.toString(), BODY)
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIds.HEADER, TRACE))
                .andExpect(jsonPath("$.contract_id").value(CONTRACT.toString()))
                .andExpect(jsonPath("$.state").value("ACCEPTED"))
                .andExpect(jsonPath("$.accepted_at").value("2026-02-10T07:04:58Z"))
                .andExpect(jsonPath("$.payment").doesNotExist());

        ArgumentCaptor<AcceptanceRequest> received = ArgumentCaptor.forClass(AcceptanceRequest.class);
        verify(acceptances).accept(eq(CONTRACT), received.capture());
        AcceptanceRequest request = received.getValue();
        assertThat(request.contractVersion()).isEqualTo(1);
        assertThat(request.acceptedBy()).isEqualTo(UUID.fromString("b0a1c7e4-52d9-4f31-8c06-7e3a9b1d2f45"));
        assertThat(request.acceptedAt()).isAtSameInstantAs(OffsetDateTime.parse("2026-02-10T10:04:58+03:00"));
        assertThat(request.channel()).isEqualTo(AcceptanceChannel.WEB);
        assertThat(request.ipAddress()).isEqualTo("10.20.30.40");
        assertThat(request.userAgent()).isEqualTo("Mozilla/5.0 (Стенд)");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "\"contract_version\": 1,|                           | contract_version | обязательное поле не передано",
        "\"accepted_by\": \"b0a1c7e4-52d9-4f31-8c06-7e3a9b1d2f45\",| | accepted_by | обязательное поле не передано",
        "\"accepted_at\": \"2026-02-10T10:04:58+03:00\",|    | accepted_at      | обязательное поле не передано",
        "\"channel\": \"WEB\",|                              | channel          | обязательное поле не передано",
        "\"contract_version\": 1,| \"contract_version\": \"один\", | contract_version | неверный формат",
        "b0a1c7e4-52d9-4f31-8c06-7e3a9b1d2f45| not-a-guid     | accepted_by      | неверный формат",
        "2026-02-10T10:04:58+03:00| 2026-02-10T10:04:58          | accepted_at      | неверный формат",
        "2026-02-10T10:04:58+03:00| вчера                        | accepted_at      | неверный формат",
        "\"WEB\"| \"SMS\"                                        | channel          | неверный формат",
        "10.20.30.40| 999.1.1.1                                  | ip_address       | неверный формат",
        "10.20.30.40| 2001:db8::g                                | ip_address       | неверный формат",
        "10.20.30.40| ::ffff:10.0.0.256                          | ip_address       | неверный формат"})
    void bodyOutOfFormat_isValidationErrorNamingTheJsonField(String original, String replacement, String field,
            String reason) throws Exception {
        accept(CONTRACT.toString(), BODY.replace(original.strip(), replacement == null ? "" : replacement.strip()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.length()").value(1))
                .andExpect(jsonPath("$.error.details[0].field").value(field))
                .andExpect(jsonPath("$.error.details[0].reason").value(reason))
                .andExpect(jsonPath("$.error.request_id").value(TRACE));
        verifyNoInteractions(acceptances);
    }

    @Test
    void ipv6AddressAndAbsentClient_areAccepted() throws Exception {
        accept(CONTRACT.toString(), """
                {"contract_version": 1, "accepted_by": "b0a1c7e4-52d9-4f31-8c06-7e3a9b1d2f45",
                 "accepted_at": "2026-02-10T10:04:58+03:00", "channel": "MOBILE", "ip_address": "2001:db8::1"}""")
                .andExpect(status().isOk());

        ArgumentCaptor<AcceptanceRequest> received = ArgumentCaptor.forClass(AcceptanceRequest.class);
        verify(acceptances).accept(eq(CONTRACT), received.capture());
        assertThat(received.getValue().ipAddress()).isEqualTo("2001:db8::1");
        assertThat(received.getValue().userAgent()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"::ffff:10.0.0.1", "64:ff9b::192.0.2.33", "::1", "fe80::1"})
    void ipv6AddressInAnyStandardForm_isAccepted(String address) throws Exception {
        accept(CONTRACT.toString(), BODY.replace("10.20.30.40", address)).andExpect(status().isOk());
    }

    @Test
    void bodyThatIsNotJson_isValidationErrorWithoutFields() throws Exception {
        accept(CONTRACT.toString(), "{\"contract_version\": 1, \"channel\": \"WEB\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("тело запроса не разобрано как JSON"))
                .andExpect(jsonPath("$.error.details.length()").value(0));
        verifyNoInteractions(acceptances);
    }

    @Test
    void bodyThatIsAJsonArrayInsteadOfAnObject_isValidationErrorWithoutFields() throws Exception {
        accept(CONTRACT.toString(), "[1, 2]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("тело запроса не разобрано как JSON"))
                .andExpect(jsonPath("$.error.details.length()").value(0));
        verifyNoInteractions(acceptances);
    }

    @Test
    void contractIdentifierThatIsNotAGuid_isValidationErrorOfThePathField() throws Exception {
        accept("not-a-guid", BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("contract_id"));
        verifyNoInteractions(acceptances);
    }

    @ParameterizedTest
    @CsvSource({"404, CONTRACT_NOT_FOUND", "409, ALREADY_ACCEPTED", "409, INVALID_STATE", "503, SERVICE_UNAVAILABLE"})
    void refusal_isAnsweredInTheCommonErrorFormatWithoutFields(int status, String code) throws Exception {
        when(acceptances.accept(eq(CONTRACT), any())).thenThrow(switch (code) {
            case "CONTRACT_NOT_FOUND" -> new ContractNotFoundException(CONTRACT);
            case "ALREADY_ACCEPTED" -> new ContractAlreadyAcceptedException(CONTRACT, 2);
            case "INVALID_STATE" -> new ContractStateException(CONTRACT, "CANCELLED");
            default -> new ContractBusyException(CONTRACT, new CannotAcquireLockException("строка договора занята"));
        });

        accept(CONTRACT.toString(), BODY)
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.message").isNotEmpty())
                .andExpect(jsonPath("$.error.details.length()").value(0))
                .andExpect(jsonPath("$.error.request_id").value(TRACE));
    }

    @Test
    void outdatedVersion_isConflictNamingTheCurrentVersion() throws Exception {
        when(acceptances.accept(eq(CONTRACT), any())).thenThrow(new DocumentVersionMismatchException(CONTRACT, 2));

        accept(CONTRACT.toString(), BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DOCUMENT_VERSION_MISMATCH"))
                .andExpect(jsonPath("$.error.details.length()").value(1))
                .andExpect(jsonPath("$.error.details[0].field").value("contract_version"))
                .andExpect(jsonPath("$.error.details[0].reason").value("актуальная версия договора — 2"));
    }

    private ResultActions accept(String contract, String body) throws Exception {
        return mvc.perform(post(PATH, contract).contentType(MediaType.APPLICATION_JSON).content(body)
                .header(RequestIds.HEADER, TRACE));
    }
}
