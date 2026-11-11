package ru.projectx.contracts.service.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.dto.AcceptanceRequest;
import ru.projectx.contracts.entity.contract.AcceptanceChannel;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractAcceptance;
import ru.projectx.contracts.entity.contract.ContractDraft;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.contract.ContractStep;
import ru.projectx.contracts.entity.contract.OfferAcceptance;
import ru.projectx.contracts.exception.DocumentVersionMismatchException;
import ru.projectx.contracts.exception.PayerEmailRequiredException;
import ru.projectx.contracts.repository.issuance.ContractAcceptanceRepository;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.service.payment.OrderOpening;
import ru.projectx.contracts.service.payment.PaymentOrderOpening;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class ContractOfferAcceptancePayerTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID CONTRACT = UUID.fromString("3f77b1c8-2a55-4c19-9e30-6b8d2f0a71c4");
    private static final UUID USER = UUID.fromString("b0a1c7e4-52d9-4f31-8c06-7e3a9b1d2f45");
    private static final String EMAIL = "insured@example.test";
    private static final String OTHER_EMAIL = "second.payer@example.test";

    private final ContractRepository contracts = mock();
    private final ContractAcceptanceRepository acceptances = mock();
    private final PaymentOrderOpening paymentOrders = mock();
    private final OrderOpening paymentOrder = mock();
    private final ContractOfferAcceptance service =
            new ContractOfferAcceptance(contracts, acceptances, paymentOrders, Clock.fixed(NOW, ZoneOffset.UTC));
    private final Contract contract = Contract.open(new ContractDraft("DKM-2026-000001", Issuances.TERMS,
            Tasks.SPOUSE, new BigDecimal("34000.00")), NOW.minusSeconds(60));

    @BeforeEach
    void contractIsReadyForAcceptance() {
        ReflectionTestUtils.setField(contract, "id", CONTRACT);
        contract.offerDraft();
        given(contracts.findLockedById(CONTRACT)).willReturn(Optional.of(contract));
        given(acceptances.findByContractId(CONTRACT)).willReturn(Optional.empty());
        given(acceptances.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(paymentOrders.forPayer(any())).willReturn(paymentOrder);
    }

    @Test
    void payerAddress_isCheckedBeforeTheContractRowIsLockedAndTheOrderIsOpenedAfterTheAcceptanceIsSaved() {
        service.accept(CONTRACT, request(EMAIL));

        InOrder order = inOrder(paymentOrders, contracts, acceptances, paymentOrder);
        order.verify(paymentOrders).forPayer(EMAIL);
        order.verify(contracts).findLockedById(CONTRACT);
        order.verify(acceptances).save(any());
        order.verify(paymentOrder).open(eq(contract), any(), eq(NOW));
    }

    @Test
    void order_isOpenedForTheContractThatIsAlreadyAccepted() {
        willAnswer(opening -> {
            assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
            return null;
        }).given(paymentOrder).open(any(), any(), any());

        service.accept(CONTRACT, request(EMAIL));

        verify(paymentOrder).open(eq(contract), any(), eq(NOW));
    }

    @Test
    void absentAddress_isHandedToTheOrderOpeningAsItCame() {
        service.accept(CONTRACT, request(null));

        verify(paymentOrders).forPayer(null);
    }

    @Test
    void addressRefusedByTheOrderOpening_leavesTheContractUnlockedAndNothingRecorded() {
        given(paymentOrders.forPayer(null)).willThrow(new PayerEmailRequiredException());

        assertThatThrownBy(() -> service.accept(CONTRACT, request(null)))
                .isInstanceOf(PayerEmailRequiredException.class);

        verifyNoInteractions(contracts, acceptances, paymentOrder);
        assertThat(contract.status()).isEqualTo(ContractStatus.DRAFT_READY);
    }

    @Test
    void repeatedAcceptance_checksItsAddressAgainButOpensNoSecondOrder() {
        acceptedBefore();

        service.accept(CONTRACT, request(OTHER_EMAIL));

        verify(paymentOrders).forPayer(OTHER_EMAIL);
        verifyNoInteractions(paymentOrder);
    }

    @Test
    void repeatedAcceptanceWithoutAddress_isRefusedLikeTheFirstOne() {
        acceptedBefore();
        given(paymentOrders.forPayer(null)).willThrow(new PayerEmailRequiredException());

        assertThatThrownBy(() -> service.accept(CONTRACT, request(null)))
                .isInstanceOf(PayerEmailRequiredException.class);

        verifyNoInteractions(contracts, acceptances);
    }

    @Test
    void acceptanceOfAnOutdatedVersion_opensNoOrder() {
        contract.advanceVersion(NOW);

        assertThatThrownBy(() -> service.accept(CONTRACT, request(EMAIL)))
                .isInstanceOf(DocumentVersionMismatchException.class);

        verifyNoInteractions(paymentOrder);
    }

    private void acceptedBefore() {
        given(acceptances.findByContractId(CONTRACT)).willReturn(Optional.of(ContractAcceptance.record(CONTRACT,
                new OfferAcceptance(1, USER, Instant.parse("2026-02-10T07:00:00Z"), AcceptanceChannel.WEB, null, null),
                NOW.minusSeconds(300))));
        contract.advance(ContractStep.OFFER_ACCEPTED);
    }

    private static AcceptanceRequest request(String email) {
        return new AcceptanceRequest(1, USER, OffsetDateTime.parse("2026-02-10T10:04:58+03:00"), AcceptanceChannel.WEB,
                null, null, email);
    }
}
