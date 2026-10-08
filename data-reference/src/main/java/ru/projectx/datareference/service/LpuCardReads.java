package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.LpuCardResponse;

/** Чтение карточки лечебного учреждения (ЛПУ) с его прайсовым договором. */
public interface LpuCardReads {

    LpuCardResponse card(UUID lpuId);
}
