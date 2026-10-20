package ru.projectx.datareference.service.lpu;

import java.util.UUID;
import ru.projectx.datareference.dto.LpuCardResponse;

public interface LpuCardReads {

    LpuCardResponse card(UUID lpuId);
}
