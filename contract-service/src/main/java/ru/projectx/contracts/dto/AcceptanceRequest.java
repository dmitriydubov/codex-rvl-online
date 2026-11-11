package ru.projectx.contracts.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.contracts.entity.contract.AcceptanceChannel;

public record AcceptanceRequest(
        @NotNull Integer contractVersion,
        @NotNull UUID acceptedBy,
        @NotNull OffsetDateTime acceptedAt,
        @NotNull AcceptanceChannel channel,
        @Nullable @Pattern(regexp = IpAddressFormat.PATTERN) String ipAddress,
        @Nullable String userAgent,
        @Nullable @PayerEmailFormat String email) {

    @Override
    public String toString() {
        return "AcceptanceRequest[contractVersion=" + contractVersion + ", acceptedBy=" + acceptedBy
                + ", acceptedAt=" + acceptedAt + ", channel=" + channel + "]";
    }
}
