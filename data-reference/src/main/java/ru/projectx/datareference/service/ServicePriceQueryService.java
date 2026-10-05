package ru.projectx.datareference.service;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.config.ReferenceApiProperties;
import ru.projectx.datareference.dto.ServicePriceResponse;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.service.price.ServicePriceCalculator;
import ru.projectx.datareference.service.price.StayPeriod;

/**
 * Стоимость услуги ЛПУ за период проживания для сервиса Заявок.
 *
 * <p>Здесь проверяется вход, данные из базы читает {@link ServicePriceLookup}, а считает
 * {@link ServicePriceCalculator}. Сам этот класс транзакцию не открывает намеренно. Выезд не позже заезда и период
 * длиннее предела ночей из настроек — ошибки вызывающего, и они отклоняются ответом 400, даже когда база недоступна.
 * Порядок дат проверяется первым: выезд раньше заезда называется именно так, а не слишком длинным периодом. Формат
 * дат проверяется ещё раньше, при чтении параметров запроса. Расчёт идёт после транзакции чтения и соединение с базой
 * не держит.
 */
@Service
public class ServicePriceQueryService implements ServicePriceReads {

    private static final String CHECK_OUT = "date_to";
    private static final String CHECK_OUT_AFTER_CHECK_IN = "позже date_from";
    private static final String STAY_LIMIT_PREFIX = "не больше ";
    private static final String STAY_LIMIT_SUFFIX = " ночей после date_from";

    private final ServicePriceLookup lookup;
    private final ReferenceApiProperties api;

    public ServicePriceQueryService(ServicePriceLookup lookup, ReferenceApiProperties api) {
        this.lookup = lookup;
        this.api = api;
    }

    @Override
    public ServicePriceResponse price(UUID serviceId, LocalDate checkIn, LocalDate checkOut) {
        if (!checkOut.isAfter(checkIn)) {
            throw new InvalidRequestException(CHECK_OUT, CHECK_OUT_AFTER_CHECK_IN);
        }
        StayPeriod period = new StayPeriod(checkIn, checkOut);
        requireNotLongerThanAllowed(period);
        PricedService priced = lookup.source(serviceId, period);
        return ServicePriceResponses.of(priced.service(), period,
                ServicePriceCalculator.calculate(serviceId, period, priced.lines()));
    }

    private void requireNotLongerThanAllowed(StayPeriod period) {
        int maxNights = api.price().maxNights();
        if (period.nights() > maxNights) {
            throw new InvalidRequestException(CHECK_OUT, STAY_LIMIT_PREFIX + maxNights + STAY_LIMIT_SUFFIX);
        }
    }
}
