package ru.projectx.datareference.service;

import java.util.List;
import ru.projectx.datareference.dto.NamedRef;
import ru.projectx.datareference.service.price.PriceLine;

/**
 * Всё, что расчёту стоимости нужно из базы: услуга, как её назвать в ответе, и строки её прайса, которые приходятся
 * на период. Прочитано в транзакции чтения, а считается уже после неё.
 */
public record PricedService(NamedRef service, List<PriceLine> lines) {

    public PricedService {
        lines = List.copyOf(lines);
    }
}
