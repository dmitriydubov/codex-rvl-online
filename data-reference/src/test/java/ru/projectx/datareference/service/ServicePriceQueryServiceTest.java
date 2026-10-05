package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.dto.ServicePriceResponse;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.service.price.StayPeriod;

/**
 * Период проверяется до чтения базы: выезд не позже заезда — ошибка вызывающего, и она даёт 400 даже при
 * недоступной базе. Верный период уходит на расчёт как есть.
 */
@ExtendWith(MockitoExtension.class)
class ServicePriceQueryServiceTest {

    private static final UUID SERVICE_ID = UUID.fromString("5e000000-0000-4000-8000-000000000002");
    private static final LocalDate APRIL_10 = LocalDate.of(2026, 4, 10);

    @Mock
    private ServicePriceLookup lookup;

    @Mock
    private ServicePriceResponse response;

    @Test
    void checkOutNextDay_isOneNight_calculatedByLookup() {
        ServicePriceQueryService prices = new ServicePriceQueryService(lookup);
        when(lookup.price(SERVICE_ID, new StayPeriod(APRIL_10, APRIL_10.plusDays(1)))).thenReturn(response);

        assertThat(prices.price(SERVICE_ID, APRIL_10, APRIL_10.plusDays(1))).isSameAs(response);
    }

    @Test
    void checkOutOnCheckInDay_isRefused_namingDateTo_withoutReadingDatabase() {
        ServicePriceQueryService prices = new ServicePriceQueryService(lookup);

        assertThatThrownBy(() -> prices.price(SERVICE_ID, APRIL_10, APRIL_10))
                .isInstanceOfSatisfying(InvalidRequestException.class, invalid -> {
                    assertThat(invalid.field()).isEqualTo("date_to");
                    assertThat(invalid.getMessage()).isEqualTo("позже date_from");
                });
        verifyNoInteractions(lookup);
    }

    @Test
    void checkOutBeforeCheckIn_isRefused() {
        ServicePriceQueryService prices = new ServicePriceQueryService(lookup);

        assertThatThrownBy(() -> prices.price(SERVICE_ID, APRIL_10, APRIL_10.minusDays(1)))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(lookup);
    }
}
