package ru.projectx.contracts.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.projectx.contracts.dto.AcceptanceRequest;
import ru.projectx.contracts.dto.AcceptanceResponse;
import ru.projectx.contracts.dto.DocumentState;
import ru.projectx.contracts.exception.PayerEmailRequiredException;
import ru.projectx.contracts.service.RequestIds;
import ru.projectx.contracts.service.acceptance.OfferAcceptances;
import ru.projectx.contracts.service.storage.AttachmentMetrics;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class ContractAcceptanceEmailTest {

    private static final UUID CONTRACT = UUID.fromString("3f77b1c8-2a55-4c19-9e30-6b8d2f0a71c4");
    private static final String PATH = "/internal/v1/contracts/{contract}/acceptance";
    private static final String TRACE = "a41f8c02-7d3b-4e19-9a55-2c7e0b6f1d84";
    private static final String WITHOUT_EMAIL = """
            {"contract_version": 1, "accepted_by": "b0a1c7e4-52d9-4f31-8c06-7e3a9b1d2f45",
             "accepted_at": "2026-02-10T10:04:58+03:00", "channel": "WEB"}""";
    private static final String LONGEST_ADDRESS = "payer-" + "a".repeat(58) + "@stand-" + "b".repeat(55) + ".stand-"
            + "c".repeat(55) + ".stand-" + "d".repeat(54) + ".test";

    private final OfferAcceptances acceptances = mock(OfferAcceptances.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ContractAcceptanceController(acceptances))
            .setControllerAdvice(new AcceptanceErrorAdvice(), new JsonBodyErrorAdvice(),
                    new RequestFormatAdvice(new AttachmentMetrics(new SimpleMeterRegistry())), new DomainErrorAdvice(),
                    new UnexpectedErrorAdvice())
            .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()))
            .addFilters(new RequestIdFilter())
            .build();

    @ParameterizedTest
    @ValueSource(strings = {"insured@example.test", "second.payer@mail.example.test"})
    void payerAddress_isPassedToTheServiceAndNotEchoedInTheAnswer(String address) throws Exception {
        when(acceptances.accept(eq(CONTRACT), any()))
                .thenReturn(new AcceptanceResponse(CONTRACT, DocumentState.ACCEPTED, Instant.parse("2026-02-10T07:04:58Z")));

        accept(withEmail("\"" + address + "\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(content().string(not(containsString(address))));

        assertThat(received().email()).isEqualTo(address);
    }

    @Test
    void addressOf254Characters_isPassedToTheService() throws Exception {
        accept(withEmail("\"" + LONGEST_ADDRESS + "\"")).andExpect(status().isOk());

        assertThat(received().email()).hasSize(254).isEqualTo(LONGEST_ADDRESS);
    }

    @Test
    void bodyWithoutAddress_isLeftToTheServiceToDecide() throws Exception {
        accept(WITHOUT_EMAIL).andExpect(status().isOk());

        assertThat(received().email()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "42", "\"insured.example.test\"", "\"insured@\"", "\"insured @example.test\"",
        "\"страхователь@example.test\"", "\"insured@пример.тест\"", "\"insured@localhost\"",
        "\"\\\"insured\\\"@example.test\"", "\"insured@[192.0.2.1]\""})
    void addressOutOfFormat_isValidationErrorWithOneViolationOfTheEmailField(String email) throws Exception {
        accept(withEmail(email))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.length()").value(1))
                .andExpect(jsonPath("$.error.details[0].field").value("email"))
                .andExpect(jsonPath("$.error.details[0].reason").value("неверный формат"))
                .andExpect(jsonPath("$.error.request_id").value(TRACE));
        verifyNoInteractions(acceptances);
    }

    @Test
    void addressOf255Characters_isValidationErrorThatDoesNotEchoTheAddress() throws Exception {
        String tooLong = LONGEST_ADDRESS.replace("d.test", "dd.test");

        accept(withEmail("\"" + tooLong + "\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.length()").value(1))
                .andExpect(jsonPath("$.error.details[0].field").value("email"))
                .andExpect(content().string(not(containsString("payer-"))));
        verifyNoInteractions(acceptances);
    }

    @Test
    void addressMissingWhilePaymentIsOn_isValidationErrorOfTheEmailFieldAsARequiredOne() throws Exception {
        when(acceptances.accept(eq(CONTRACT), any())).thenThrow(new PayerEmailRequiredException());

        accept(WITHOUT_EMAIL)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("запрос не соответствует формату"))
                .andExpect(jsonPath("$.error.details.length()").value(1))
                .andExpect(jsonPath("$.error.details[0].field").value("email"))
                .andExpect(jsonPath("$.error.details[0].reason").value("обязательное поле не передано"))
                .andExpect(jsonPath("$.error.request_id").value(TRACE));
    }

    private AcceptanceRequest received() {
        ArgumentCaptor<AcceptanceRequest> received = ArgumentCaptor.forClass(AcceptanceRequest.class);
        verify(acceptances).accept(eq(CONTRACT), received.capture());
        return received.getValue();
    }

    private static String withEmail(String jsonValue) {
        return WITHOUT_EMAIL.replace("\"channel\": \"WEB\"", "\"channel\": \"WEB\", \"email\": " + jsonValue);
    }

    private ResultActions accept(String body) throws Exception {
        return mvc.perform(post(PATH, CONTRACT).contentType(MediaType.APPLICATION_JSON).content(body)
                .header(RequestIds.HEADER, TRACE));
    }
}
