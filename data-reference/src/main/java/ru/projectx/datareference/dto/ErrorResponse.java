package ru.projectx.datareference.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ErrorResponse(Body error) {

    public static ErrorResponse of(String code, String message, @Nullable String requestId) {
        return new ErrorResponse(new Body(code, message, List.of(), requestId));
    }

    public static ErrorResponse of(
            String code, String message, List<? extends Detail> details, @Nullable String requestId) {

        return new ErrorResponse(new Body(code, message, List.copyOf(details), requestId));
    }

    public record Body(String code, String message, List<? extends Detail> details, @Nullable String requestId) {
    }

    public sealed interface Detail permits Violation, PolicyCandidate, LpuContractCandidate, PriceRowCandidate, UncoveredInterval {
    }

    public record Violation(String field, String reason) implements Detail {
    }

    public record PolicyCandidate(UUID policyId) implements Detail {
    }

    public record LpuContractCandidate(UUID lpuContractId) implements Detail {
    }

    public record PriceRowCandidate(LocalDate validFrom, @Nullable LocalDate validTo) implements Detail {
    }

    public record UncoveredInterval(LocalDate dateFrom, LocalDate dateTo) implements Detail {
    }
}
