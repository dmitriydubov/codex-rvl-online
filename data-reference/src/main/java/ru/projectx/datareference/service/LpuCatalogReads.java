package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.LpuCatalogResponse;

/** Каталог ЛПУ договора страхования (R-01). */
public interface LpuCatalogReads {

    LpuCatalogResponse catalog(UUID contractExternalId);
}
