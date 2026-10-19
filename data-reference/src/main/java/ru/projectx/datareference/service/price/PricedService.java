package ru.projectx.datareference.service.price;

import java.util.List;
import ru.projectx.datareference.dto.NamedRef;

public record PricedService(NamedRef service, List<PriceLine> lines) {

    public PricedService {
        lines = List.copyOf(lines);
    }
}
