package ru.projectx.datareference.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.api")
public record ReferenceApiProperties(
        @DefaultValue("Europe/Moscow") ZoneId zone,
        @NotEmpty List<@NotBlank String> rvlAppealTypes,
        @Valid @DefaultValue Search search,
        @Valid @DefaultValue Mkb10 mkb10,
        @Valid @DefaultValue Price price,
        @Min(1) int readTimeoutSeconds) {

    public record Search(
            @Min(1) @DefaultValue("10") int defaultLimit,
            @Min(1) @DefaultValue("50") int maxLimit,
            @Min(1) @DefaultValue("2") int minQueryLength,
            @Min(1) @DefaultValue("100") int maxQueryLength) implements QueryLengthRange {
    }

    public record Mkb10(
            @Min(1) @DefaultValue("20") int defaultLimit,
            @Min(1) @DefaultValue("100") int maxLimit,
            @Min(1) @DefaultValue("2") int minQueryLength,
            @Min(1) @DefaultValue("100") int maxQueryLength,
            @Min(1) @DefaultValue("100") int maxCodes) implements QueryLengthRange {
    }

    public record Price(@Min(1) @DefaultValue("366") int maxNights) {
    }

    public interface QueryLengthRange {

        int minQueryLength();

        int maxQueryLength();

        @AssertTrue(message = "max-query-length не меньше min-query-length")
        default boolean isQueryLengthRangeConsistent() {
            return maxQueryLength() >= minQueryLength();
        }
    }
}
