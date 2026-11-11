package ru.projectx.contracts.service.packages;

import org.jspecify.annotations.Nullable;
import ru.projectx.contracts.dto.DocumentState;
import ru.projectx.contracts.dto.PackageItem.PackagePayment;
import ru.projectx.contracts.dto.PackageItem.PaymentState;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.service.status.PaymentOffer;

final class PackagePayments {

    private PackagePayments() {
    }

    static @Nullable PackagePayment of(Contract contract, DocumentState shown, PaymentOffer offer) {
        if (contract.progress().paidAt().isPresent()) {
            return new PackagePayment(PaymentState.PAID, contract.premiumAmount(), null, null);
        }
        return switch (shown) {
            case ACCEPTED -> new PackagePayment(PaymentState.PENDING, contract.premiumAmount(), null, null);
            case INVOICE_ISSUED -> new PackagePayment(PaymentState.PENDING, contract.premiumAmount(),
                    offer.link().orElse(null), offer.deadline().orElse(null));
            default -> null;
        };
    }
}
