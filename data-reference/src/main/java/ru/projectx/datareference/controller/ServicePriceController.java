package ru.projectx.datareference.controller;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.projectx.datareference.dto.ServicePriceResponse;
import ru.projectx.datareference.service.ServicePriceReads;

@InternalApi
public class ServicePriceController {

    private final ServicePriceReads prices;

    public ServicePriceController(ServicePriceReads prices) {
        this.prices = prices;
    }

    @GetMapping("/services/{id}/price")
    public ServicePriceResponse price(
            @PathVariable UUID id,
            @RequestParam("date_from") LocalDate checkIn,
            @RequestParam("date_to") LocalDate checkOut) {

        return prices.price(id, checkIn, checkOut);
    }
}
