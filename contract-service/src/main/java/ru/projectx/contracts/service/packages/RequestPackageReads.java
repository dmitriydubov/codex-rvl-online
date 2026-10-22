package ru.projectx.contracts.service.packages;

import java.util.UUID;
import ru.projectx.contracts.dto.PackageResponse;

public interface RequestPackageReads {

    PackageResponse packageOf(UUID individualRequestId);
}
