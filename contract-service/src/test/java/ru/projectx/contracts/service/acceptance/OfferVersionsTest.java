package ru.projectx.contracts.service.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.contract.AcceptanceChannel;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractAcceptance;
import ru.projectx.contracts.entity.contract.OfferAcceptance;
import ru.projectx.contracts.exception.ContractAlreadyAcceptedException;
import ru.projectx.contracts.exception.DocumentVersionMismatchException;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class OfferVersionsTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final String CONTRACT_ID = "3f77b1c8-2a55-4c19-9e30-6b8d2f0a71c4";

    private final Contract contract = Issuances.contract(CONTRACT_ID, Tasks.SPOUSE, "DKM-2026-000001");

    @Test
    void currentVersion_isAnsweredWhenItIsTheRequestedOne() {
        contract.advanceVersion(NOW);

        assertThat(OfferVersions.current(contract, 2)).isEqualTo(2);
    }

    @Test
    void requestedVersionOtherThanTheCurrentOne_isAMismatchNamingTheCurrentVersion() {
        contract.advanceVersion(NOW);

        assertThatThrownBy(() -> OfferVersions.current(contract, 1))
                .isInstanceOfSatisfying(DocumentVersionMismatchException.class,
                        mismatch -> assertThat(mismatch.currentVersion()).isEqualTo(2));
        assertThatThrownBy(() -> OfferVersions.current(contract, 3))
                .isInstanceOf(DocumentVersionMismatchException.class);
    }

    @Test
    void repeatedAcceptanceOfTheAcceptedVersion_isAllowed() {
        assertThatCode(() -> OfferVersions.ensureAccepted(acceptedByVersion(2), 2)).doesNotThrowAnyException();
    }

    @Test
    void repeatedAcceptanceOfAnotherVersion_isRefusedNamingTheContractAndTheAcceptedVersion() {
        assertThatThrownBy(() -> OfferVersions.ensureAccepted(acceptedByVersion(2), 1))
                .isInstanceOf(ContractAlreadyAcceptedException.class)
                .hasMessageContaining(CONTRACT_ID).hasMessageContaining("версии 2");
        assertThatThrownBy(() -> OfferVersions.ensureAccepted(acceptedByVersion(2), 3))
                .isInstanceOf(ContractAlreadyAcceptedException.class);
    }

    private static ContractAcceptance acceptedByVersion(int version) {
        return ContractAcceptance.record(UUID.fromString(CONTRACT_ID), new OfferAcceptance(version,
                UUID.fromString("b0a1c7e4-52d9-4f31-8c06-7e3a9b1d2f45"), NOW, AcceptanceChannel.WEB, null, null), NOW);
    }
}
