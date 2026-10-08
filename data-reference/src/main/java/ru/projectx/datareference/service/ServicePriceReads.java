package ru.projectx.datareference.service;

import java.time.LocalDate;
import java.util.UUID;
import ru.projectx.datareference.dto.ServicePriceResponse;

/** Стоимость услуги учреждения за период проживания; день выезда не оплачивается. */
public interface ServicePriceReads {

    ServicePriceResponse price(UUID serviceId, LocalDate checkIn, LocalDate checkOut);
}
