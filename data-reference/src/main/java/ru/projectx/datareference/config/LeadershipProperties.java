package ru.projectx.datareference.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.leadership")
public record LeadershipProperties(
        @DefaultValue("data-reference") @NotBlank @Size(max = 100) String group,
        @DefaultValue("30s") @DurationMin(seconds = 1) Duration lockTtl,
        @DefaultValue("1s") @DurationMin(millis = 1) Duration heartbeat) {

    @AssertTrue(message = "срок блокировки ведущего должен быть больше шага её продления")
    public boolean isLockTtlLongerThanHeartbeat() {
        return lockTtl.compareTo(heartbeat) > 0;
    }
}
