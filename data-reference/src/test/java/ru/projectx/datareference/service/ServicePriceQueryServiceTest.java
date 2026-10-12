package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.dto.NamedRef;
import ru.projectx.datareference.dto.ServicePriceResponse;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.exception.PriceNotCoveredException;
import ru.projectx.datareference.service.price.PriceLine;
import ru.projectx.datareference.service.price.StayPeriod;
import ru.projectx.datareference.support.TestApiProperties;

@ExtendWith(MockitoExtension.class)
class ServicePriceQueryServiceTest {

    private static final UUID SERVICE_ID = UUID.fromString("5e000000-0000-4000-8000-000000000002");
    private static final LocalDate APRIL_10 = LocalDate.of(2026, 4, 10);

    @Mock
    private ServicePriceLookup lookup;

    @Test
    void checkOutNextDay_isOneNight_pricedByRowsReadByLookup() {
        ServicePriceQueryService prices = new ServicePriceQueryService(lookup, TestApiProperties.defaults());
        NamedRef service = new NamedRef(SERVICE_ID, UUID.randomUUID(), "Размещение");
        PriceLine line = new PriceLine(UUID.randomUUID(), APRIL_10, null, new BigDecimal("7300.00"), null);
        when(lookup.source(SERVICE_ID, new StayPeriod(APRIL_10, APRIL_10.plusDays(1))))
                .thenReturn(new PricedService(service, List.of(line)));

        ServicePriceResponse response = prices.price(SERVICE_ID, APRIL_10, APRIL_10.plusDays(1));

        assertThat(response.service()).isSameAs(service);
        assertThat(response.total().nights()).isEqualTo(1);
        assertThat(response.total().amount()).isEqualTo(new BigDecimal("7300.00"));
    }

    @Test
    void noRowsForPeriod_isRefusedForWholePeriod_namingService() {
        ServicePriceQueryService prices = new ServicePriceQueryService(lookup, TestApiProperties.defaults());
        StayPeriod period = new StayPeriod(APRIL_10, APRIL_10.plusDays(2));
        when(lookup.source(SERVICE_ID, period)).thenReturn(
                new PricedService(new NamedRef(SERVICE_ID, UUID.randomUUID(), "Размещение"), List.of()));

        assertThatThrownBy(() -> prices.price(SERVICE_ID, period.checkIn(), period.checkOut()))
                .isInstanceOfSatisfying(PriceNotCoveredException.class, notCovered -> {
                    assertThat(notCovered.serviceId()).isEqualTo(SERVICE_ID);
                    assertThat(notCovered.intervals()).containsExactly(
                            new PriceNotCoveredException.Interval(period.checkIn(), period.checkOut()));
                });
    }

    @Test
    void checkOutOnCheckInDay_isRefused_namingDateTo_withoutReadingDatabase() {
        ServicePriceQueryService prices = new ServicePriceQueryService(lookup, TestApiProperties.defaults());

        assertThatThrownBy(() -> prices.price(SERVICE_ID, APRIL_10, APRIL_10))
                .isInstanceOfSatisfying(InvalidRequestException.class, invalid -> {
                    assertThat(invalid.field()).isEqualTo("date_to");
                    assertThat(invalid.getMessage()).isEqualTo("позже date_from");
                });
        verifyNoInteractions(lookup);
    }

    @Test
    void checkOutBeforeCheckIn_isRefused() {
        ServicePriceQueryService prices = new ServicePriceQueryService(lookup, TestApiProperties.defaults());

        assertThatThrownBy(() -> prices.price(SERVICE_ID, APRIL_10, APRIL_10.minusDays(1)))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(lookup);
    }

    @Test
    void stayOfExactlyMaxNights_isReadFromDatabase() {
        ServicePriceQueryService prices = new ServicePriceQueryService(lookup, TestApiProperties.defaults());
        StayPeriod period = new StayPeriod(APRIL_10, LocalDate.of(2027, 4, 11));
        when(lookup.source(SERVICE_ID, period)).thenReturn(new PricedService(
                new NamedRef(SERVICE_ID, UUID.randomUUID(), "Размещение"),
                List.of(new PriceLine(UUID.randomUUID(), APRIL_10, null, new BigDecimal("1000.00"), null))));

        ServicePriceResponse response = prices.price(SERVICE_ID, period.checkIn(), period.checkOut());

        assertThat(response.total().nights()).isEqualTo(366);
    }

    @Test
    void stayOneNightLongerThanMax_isRefused_namingDateToAndLimit_withoutReadingDatabase() {
        ServicePriceQueryService prices = new ServicePriceQueryService(lookup, TestApiProperties.defaults());

        assertThatThrownBy(() -> prices.price(SERVICE_ID, APRIL_10, LocalDate.of(2027, 4, 12)))
                .isInstanceOfSatisfying(InvalidRequestException.class, invalid -> {
                    assertThat(invalid.field()).isEqualTo("date_to");
                    assertThat(invalid.getMessage()).isEqualTo("не больше 366 ночей после date_from");
                });
        verifyNoInteractions(lookup);
    }

    @Test
    void periodBeyondIntRange_isRefusedAsTooLong_withoutReadingDatabase() {
        ServicePriceQueryService prices = new ServicePriceQueryService(lookup, TestApiProperties.defaults());

        assertThatThrownBy(() -> prices.price(SERVICE_ID, LocalDate.MIN, LocalDate.MAX))
                .isInstanceOfSatisfying(InvalidRequestException.class, invalid -> {
                    assertThat(invalid.field()).isEqualTo("date_to");
                    assertThat(invalid.getMessage()).isEqualTo("не больше 366 ночей после date_from");
                });
        verifyNoInteractions(lookup);
    }

    @Test
    void configuredMaxNights_decidesBoundaryAndReason() {
        ServicePriceQueryService prices =
                new ServicePriceQueryService(lookup, TestApiProperties.withPriceMaxNights(30));
        StayPeriod allowed = new StayPeriod(APRIL_10, APRIL_10.plusDays(30));
        when(lookup.source(SERVICE_ID, allowed)).thenReturn(
                new PricedService(new NamedRef(SERVICE_ID, UUID.randomUUID(), "Размещение"), List.of(
                        new PriceLine(UUID.randomUUID(), APRIL_10, null, new BigDecimal("1000.00"), null))));

        assertThat(prices.price(SERVICE_ID, allowed.checkIn(), allowed.checkOut()).total().nights()).isEqualTo(30);
        assertThatThrownBy(() -> prices.price(SERVICE_ID, APRIL_10, APRIL_10.plusDays(31)))
                .isInstanceOfSatisfying(InvalidRequestException.class, invalid ->
                        assertThat(invalid.getMessage()).isEqualTo("не больше 30 ночей после date_from"));
    }

    @Test
    void longReversedStay_isRefusedForDateOrder() {
        ServicePriceQueryService prices = new ServicePriceQueryService(lookup, TestApiProperties.defaults());

        assertThatThrownBy(() -> prices.price(SERVICE_ID, LocalDate.of(9999, 12, 31), APRIL_10))
                .isInstanceOfSatisfying(InvalidRequestException.class, invalid ->
                        assertThat(invalid.getMessage()).isEqualTo("позже date_from"));
        verifyNoInteractions(lookup);
    }
}
