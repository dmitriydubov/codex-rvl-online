package ru.projectx.contracts.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.leadership")
public record LeadershipProperties(
        @NotBlank @Size(max = MAX_GROUP_LENGTH) String group,
        @NotNull @DurationMin(seconds = 1) Duration lockTtl,
        @NotNull @DurationMin(millis = 1) Duration heartbeat,
        @Min(1) int connections) {

    private static final int MAX_GROUP_LENGTH = 100;

    @AssertTrue(message = "срок блокировки ведущего должен быть больше шага её продления")
    public boolean isLockTtlLongerThanHeartbeat() {
        return lockTtl != null && heartbeat != null && lockTtl.compareTo(heartbeat) > 0;
    }
}
