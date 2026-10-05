package ru.projectx.datareference.service;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.dto.ServicePriceResponse;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.service.price.ServicePriceCalculator;
import ru.projectx.datareference.service.price.StayPeriod;

/**
 * Стоимость услуги ЛПУ за период проживания для сервиса Заявок.
 *
 * <p>Здесь проверяется вход, данные из базы читает {@link ServicePriceLookup}, а считает
 * {@link ServicePriceCalculator}. Сам этот класс транзакцию не открывает намеренно. Выезд не позже заезда — ошибка вызывающего, и она отклоняется
 * ответом 400, даже когда база недоступна. Формат дат проверяется ещё раньше, при чтении параметров запроса. Расчёт
 * идёт после транзакции чтения и соединение с базой не держит.
 */
@Service
public class ServicePriceQueryService implements ServicePriceReads {

    private static final String CHECK_OUT = "date_to";
    private static final String CHECK_OUT_AFTER_CHECK_IN = "позже date_from";

    private final ServicePriceLookup lookup;

    public ServicePriceQueryService(ServicePriceLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    public ServicePriceResponse price(UUID serviceId, LocalDate checkIn, LocalDate checkOut) {
        if (!checkOut.isAfter(checkIn)) {
            throw new InvalidRequestException(CHECK_OUT, CHECK_OUT_AFTER_CHECK_IN);
        }
        StayPeriod period = new StayPeriod(checkIn, checkOut);
        PricedService priced = lookup.source(serviceId, period);
        return ServicePriceResponses.of(priced.service(), period,
                ServicePriceCalculator.calculate(serviceId, period, priced.lines()));
    }
}
