package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.LpuCardResponse;

/** Карточка ЛПУ с прайсовым договором (R-02). */
public interface LpuCardReads {

    LpuCardResponse card(UUID lpuId);
}
