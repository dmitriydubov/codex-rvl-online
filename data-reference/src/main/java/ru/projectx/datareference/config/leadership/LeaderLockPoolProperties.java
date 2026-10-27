package ru.projectx.datareference.config.leadership;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.database.leader-lock-pool")
public record LeaderLockPoolProperties(@Min(1) int maximumPoolSize) {
}
