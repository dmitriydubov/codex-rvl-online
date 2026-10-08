package ru.projectx.datareference.service;

import java.util.List;
import ru.projectx.datareference.dto.NamedRef;
import ru.projectx.datareference.service.price.PriceLine;

/** Всё, что расчёту стоимости нужно из базы: читается в транзакции, а считается уже после неё. */
public record PricedService(NamedRef service, List<PriceLine> lines) {

    public PricedService {
        lines = List.copyOf(lines);
    }
}
