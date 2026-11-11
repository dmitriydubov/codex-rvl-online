package ru.projectx.contracts.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.contract.AcceptanceChannel;

class AcceptanceRequestTest {

    private static final UUID USER = UUID.fromString("b0a1c7e4-52d9-4f31-8c06-7e3a9b1d2f45");

    @Test
    void text_namesVersionUserMomentAndChannelButNeitherThePayerAddressNorTheClient() {
        AcceptanceRequest request = new AcceptanceRequest(3, USER, OffsetDateTime.parse("2026-02-10T10:04:58+03:00"),
                AcceptanceChannel.MOBILE, "10.20.30.40", "Mozilla/5.0 (Стенд)", "insured@example.test");

        assertThat(request).hasToString("AcceptanceRequest[contractVersion=3, acceptedBy=" + USER
                + ", acceptedAt=2026-02-10T10:04:58+03:00, channel=MOBILE]");
    }

    @Test
    void payerAddress_staysReadableForTheService() {
        AcceptanceRequest request = new AcceptanceRequest(1, USER, OffsetDateTime.parse("2026-02-10T10:04:58+03:00"),
                AcceptanceChannel.WEB, null, null, "insured@example.test");

        assertThat(request.email()).isEqualTo("insured@example.test");
    }
}
