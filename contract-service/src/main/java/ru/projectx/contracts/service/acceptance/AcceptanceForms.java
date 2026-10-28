package ru.projectx.contracts.service.acceptance;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import ru.projectx.contracts.dto.AcceptanceRequest;
import ru.projectx.contracts.entity.contract.OfferAcceptance;

final class AcceptanceForms {

    private AcceptanceForms() {
    }

    static OfferAcceptance offer(AcceptanceRequest request) {
        return new OfferAcceptance(request.contractVersion(), request.acceptedBy(), request.acceptedAt().toInstant(),
                request.channel(), Optional.ofNullable(request.ipAddress()).map(AcceptanceForms::address).orElse(null),
                request.userAgent());
    }

    private static InetAddress address(String literal) {
        try {
            return InetAddress.getByName(literal);
        }
        catch (UnknownHostException invalid) {
            throw new IllegalArgumentException("адрес принявшего оферту не разобран как IP-адрес", invalid);
        }
    }
}
