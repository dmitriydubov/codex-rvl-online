package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.LpuCatalogResponse;

public interface LpuCatalogReads {

    LpuCatalogResponse catalog(UUID contractExternalId);
}
