package ru.projectx.contracts.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.attachments")
public record AttachmentProperties(
        @NotNull @PositiveSize DataSize maxFileSize,
        @NotEmpty Map<String, @NotNull @Pattern(regexp = "[a-z0-9]+") String> contentTypes) {
}
