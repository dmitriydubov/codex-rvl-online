package ru.projectx.datareference.dto;

import java.util.List;
import java.util.UUID;

public record InsuredRelativesResponse(List<Relative> items) {

    public record Relative(UUID relationTypeExternalId, RelativePolicy policy) {
    }
}
