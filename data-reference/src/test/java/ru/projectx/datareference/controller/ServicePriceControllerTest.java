package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.dto.NamedRef;
import ru.projectx.datareference.dto.ServicePriceResponse;
import ru.projectx.datareference.service.price.ServicePriceReads;

@ExtendWith(MockitoExtension.class)
class ServicePriceControllerTest {

    private static final String PRICE = InternalApi.BASE_PATH + "/services/{id}/price";
    private static final UUID SERVICE_ID = UUID.fromString("5e000000-0000-4000-8000-000000000010");
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 4, 10);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 4, 12);

    @Mock
    private ServicePriceReads prices;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = StandaloneMvc.of(new ServicePriceController(prices));
    }

    @Test
    void price_passesServiceAndBothDates_andReturnsCalculation() {
        when(prices.price(SERVICE_ID, CHECK_IN, CHECK_OUT)).thenReturn(response());

        MvcTestResult result = mvc.get()
                .uri(PRICE + "?date_from=2026-04-10&date_to=2026-04-12", SERVICE_ID).exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.service.id").isEqualTo(SERVICE_ID.toString());
        assertThat(result).bodyJson().extractingPath("$.dateFrom").isEqualTo("2026-04-10");
        assertThat(result).bodyJson().extractingPath("$.total.basis").isEqualTo("BASE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.04.2026", "2026-4-10", "2026-02-30", "2026-04-10T00:00:00"})
    void dateFromNotInIsoRecord_isValidationError_namingDateFrom(String dateFrom) {
        MvcTestResult result = mvc.get()
                .uri(PRICE + "?date_from={from}&date_to=2026-04-12", SERVICE_ID, dateFrom).exchange();

        assertViolation(result, "date_from", "ожидается дата yyyy-mm-dd");
    }

    @Test
    void dateFromInLocalizedShortRecord_isValidationError_namingDateFrom() {
        MvcTestResult result = mvc.get().uri(PRICE + "?date_from={from}&date_to=2026-04-12", SERVICE_ID, "4/10/26")
                .locale(Locale.ENGLISH).exchange();

        assertViolation(result, "date_from", "ожидается дата yyyy-mm-dd");
    }

    @Test
    void dateToNotInIsoRecord_isValidationError_namingDateTo() {
        MvcTestResult result = mvc.get().uri(PRICE + "?date_from=2026-04-10&date_to=12.04.2026", SERVICE_ID).exchange();

        assertViolation(result, "date_to", "ожидается дата yyyy-mm-dd");
    }

    @Test
    void emptyDateTo_isMissingParameter() {
        MvcTestResult result = mvc.get().uri(PRICE + "?date_from=2026-04-10&date_to=", SERVICE_ID).exchange();

        assertViolation(result, "date_to", "обязательный параметр");
    }

    @Test
    void missingDateFrom_isMissingParameter() {
        MvcTestResult result = mvc.get().uri(PRICE + "?date_to=2026-04-12", SERVICE_ID).exchange();

        assertViolation(result, "date_from", "обязательный параметр");
    }

    @Test
    void nonCanonicalServiceId_isValidationError_namingId() {
        MvcTestResult result = mvc.get().uri(PRICE + "?date_from=2026-04-10&date_to=2026-04-12", "1-2-3-4-5").exchange();

        assertViolation(result, "id", "ожидается UUID");
    }

    private void assertViolation(MvcTestResult result, String field, String reason) {
        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("VALIDATION_ERROR");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo(field);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].reason").isEqualTo(reason);
        verifyNoInteractions(prices);
    }

    private static ServicePriceResponse response() {
        return new ServicePriceResponse(new NamedRef(SERVICE_ID, UUID.randomUUID(), "Размещение"), CHECK_IN, CHECK_OUT,
                List.of(), new ServicePriceResponse.Total(2, new BigDecimal("7000.00"), null,
                        ServicePriceResponse.Basis.BASE));
    }
}
