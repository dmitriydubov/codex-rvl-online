package ru.projectx.datareference.service.lpu;

import java.util.UUID;
import ru.projectx.datareference.dto.LpuCatalogResponse;

public interface LpuCatalogReads {

    LpuCatalogResponse catalog(UUID contractExternalId);
}
