package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.LpuCardResponse;

public interface LpuCardReads {

    LpuCardResponse card(UUID lpuId);
}
