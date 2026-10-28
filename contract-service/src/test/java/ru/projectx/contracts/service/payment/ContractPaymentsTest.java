package ru.projectx.contracts.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class ContractPaymentsTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:25:00Z");
    private static final Instant PAID_AT = Instant.parse("2026-02-10T07:20:15Z");
    private static final UUID CONTRACT_ID = UUID.fromString("0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64");

    private final ContractRepository contracts = mock();
    private final ContractPayments payments = new ContractPayments(contracts, Clock.fixed(NOW, ZoneOffset.UTC));
    private final Contract contract = Issuances.contract(CONTRACT_ID.toString(), Tasks.SPOUSE, "DKM-2026-000001");

    @Test
    void markPaid_locksTheAcceptedContractAndRecordsTheMomentOfPaymentWithTheNextVersion() {
        contract.offerDraft();
        contract.accept();
        given(contracts.findLockedById(CONTRACT_ID)).willReturn(Optional.of(contract));

        payments.markPaid(CONTRACT_ID, PAID_AT);

        assertThat(contract.status()).isEqualTo(ContractStatus.PAID);
        assertThat(contract.progress().paidAt()).contains(PAID_AT);
        assertThat(contract.state().entityVersion()).isEqualTo(2);
        assertThat(contract).hasFieldOrPropertyWithValue("updatedAt", NOW);
        verify(contracts, never()).findById(any());
    }

    @Test
    void markPaid_isRefusedForAContractThatDoesNotAwaitPayment() {
        given(contracts.findLockedById(CONTRACT_ID)).willReturn(Optional.of(contract));

        assertThatThrownBy(() -> payments.markPaid(CONTRACT_ID, PAID_AT)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("не ждёт оплаты");
        assertThat(contract.status()).isEqualTo(ContractStatus.NEW);
    }

    @Test
    void unknownContract_isIllegalStateNamingIt() {
        given(contracts.findLockedById(CONTRACT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> payments.markPaid(CONTRACT_ID, PAID_AT)).isInstanceOf(IllegalStateException.class)
                .hasMessage("договора КМ " + CONTRACT_ID + " нет");
    }

    @Test
    void contractRowThatCannotBeLocked_failsThePaymentBeforeAnyChange() {
        CannotAcquireLockException busy = new CannotAcquireLockException("строка занята");
        given(contracts.findLockedById(CONTRACT_ID)).willThrow(busy);

        assertThatThrownBy(() -> payments.markPaid(CONTRACT_ID, PAID_AT)).isSameAs(busy);
        assertThat(contract.status()).isEqualTo(ContractStatus.NEW);
    }
}
