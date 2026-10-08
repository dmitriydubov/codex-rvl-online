package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.LpuCatalogResponse;

/** Чтение каталога лечебных учреждений (ЛПУ) по договору страхования. */
public interface LpuCatalogReads {

    LpuCatalogResponse catalog(UUID contractExternalId);
}
