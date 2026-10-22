package ru.projectx.contracts.service.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.dto.AcceptanceRequest;
import ru.projectx.contracts.entity.AcceptanceChannel;
import ru.projectx.contracts.entity.OfferAcceptance;

class AcceptanceFormsTest {

    private static final UUID USER = UUID.fromString("5c3e9a71-0d24-4b8f-a6e2-93f1b7c4d0a8");

    @Test
    void request_becomesTheOfferWithTheMomentInUtcAndTheParsedAddress() throws UnknownHostException {
        OfferAcceptance offer = AcceptanceForms.offer(request("2001:db8::1", "RVL-Mobile/1.0 (Стенд)"));

        assertThat(offer).isEqualTo(new OfferAcceptance(2, USER, Instant.parse("2026-02-10T07:04:30Z"),
                AcceptanceChannel.MOBILE, InetAddress.getByName("2001:db8::1"), "RVL-Mobile/1.0 (Стенд)"));
    }

    @Test
    void requestWithoutAddressAndClient_becomesTheOfferWithoutThem() {
        OfferAcceptance offer = AcceptanceForms.offer(request(null, null));

        assertThat(offer.ipAddress()).isNull();
        assertThat(offer.userAgent()).isNull();
    }

    @Test
    void addressThatIsNotALiteral_isRefusedWithoutNamingIt() {
        assertThatThrownBy(() -> AcceptanceForms.offer(request("[2001:db8::1", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("адрес принявшего оферту не разобран как IP-адрес")
                .hasCauseInstanceOf(UnknownHostException.class);
    }

    private static AcceptanceRequest request(String ipAddress, String userAgent) {
        return new AcceptanceRequest(2, USER, OffsetDateTime.parse("2026-02-10T07:04:30Z"), AcceptanceChannel.MOBILE,
                ipAddress, userAgent);
    }
}
