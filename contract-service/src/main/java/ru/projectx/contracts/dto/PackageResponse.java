package ru.projectx.contracts.dto;

import java.util.List;
import java.util.UUID;

public record PackageResponse(UUID individualRequestId, String status, List<PackageItem> items) {

    public PackageResponse {
        items = List.copyOf(items);
    }
}
