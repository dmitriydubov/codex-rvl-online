package ru.projectx.contracts.service.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.dto.AcceptanceRequest;
import ru.projectx.contracts.dto.AcceptanceResponse;
import ru.projectx.contracts.dto.DocumentState;
import ru.projectx.contracts.entity.contract.AcceptanceChannel;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractAcceptance;
import ru.projectx.contracts.entity.contract.ContractDraft;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.contract.ContractStep;
import ru.projectx.contracts.entity.contract.OfferAcceptance;
import ru.projectx.contracts.exception.AcceptanceRefusal;
import ru.projectx.contracts.exception.ContractAlreadyAcceptedException;
import ru.projectx.contracts.exception.ContractBusyException;
import ru.projectx.contracts.exception.ContractNotFoundException;
import ru.projectx.contracts.exception.ContractStateException;
import ru.projectx.contracts.exception.DocumentVersionMismatchException;
import ru.projectx.contracts.repository.issuance.ContractAcceptanceRepository;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.service.payment.OrderOpening;
import ru.projectx.contracts.service.payment.PaymentOrderOpening;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class ContractOfferAcceptanceTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID CONTRACT = UUID.fromString("3f77b1c8-2a55-4c19-9e30-6b8d2f0a71c4");
    private static final UUID USER = UUID.fromString("b0a1c7e4-52d9-4f31-8c06-7e3a9b1d2f45");
    private static final String EMAIL = "insured@example.test";
    private static final OffsetDateTime ACCEPTED_AT = OffsetDateTime.of(2026, 2, 10, 10, 4, 58, 0,
            ZoneOffset.ofHours(3));

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
        given(paymentOrders.forPayer(EMAIL)).willReturn(paymentOrder);
    }

    @Test
    void acceptanceOfTheCurrentVersion_isRecordedAndAcceptsTheContract() throws Exception {
        AcceptanceResponse response = service.accept(CONTRACT, request(1, "10.20.30.40"));

        assertThat(response).isEqualTo(new AcceptanceResponse(CONTRACT, DocumentState.ACCEPTED, ACCEPTED_AT.toInstant()));
        assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
        ContractAcceptance saved = saved();
        assertThat(saved.contractId()).isEqualTo(CONTRACT);
        assertThat(saved.offer().contractVersion()).isEqualTo(1);
        assertThat(saved.offer().acceptedAt()).isEqualTo(ACCEPTED_AT.toInstant());
        assertThat(saved.offer()).isEqualTo(new OfferAcceptance(1, USER, ACCEPTED_AT.toInstant(), AcceptanceChannel.WEB,
                InetAddress.getByName("10.20.30.40"), "Mozilla/5.0 (Стенд)"));
        assertThat(saved).hasFieldOrPropertyWithValue("createdAt", NOW);
        verify(paymentOrder).open(contract, saved.offer(), NOW);
    }

    @Test
    void firstAcceptance_isJournaledWithTheContractTheAcceptedVersionAndTheChannel() {
        contract.advanceVersion(NOW);

        try (CapturedLog log = CapturedLog.of(ContractOfferAcceptance.class)) {
            service.accept(CONTRACT, request(2, null));

            assertThat(log.at(Level.INFO))
                    .containsExactly("договор КМ " + CONTRACT + " акцептован по версии 2, канал WEB");
        }
    }

    @Test
    void acceptanceWithoutAddress_isRecordedWithoutIt() {
        service.accept(CONTRACT, request(1, null));

        assertThat(saved().offer().ipAddress()).isNull();
    }

    @Test
    void repeatedAcceptanceOfTheSameVersion_answersThePreviousRecord() {
        ContractAcceptance previous = ContractAcceptance.record(CONTRACT, offer(1), NOW.minusSeconds(300));
        given(acceptances.findByContractId(CONTRACT)).willReturn(Optional.of(previous));
        contract.advance(ContractStep.OFFER_ACCEPTED);

        AcceptanceResponse response = service.accept(CONTRACT, request(1, "10.20.30.41"));

        assertThat(response.acceptedAt()).isEqualTo(Instant.parse("2026-02-10T07:00:00Z"));
        verify(acceptances, never()).save(any());
        verifyNoInteractions(paymentOrder);
    }

    @Test
    void acceptanceOfAnotherVersionAfterAcceptance_isAlreadyAccepted() {
        given(acceptances.findByContractId(CONTRACT))
                .willReturn(Optional.of(ContractAcceptance.record(CONTRACT, offer(2), NOW)));
        contract.advance(ContractStep.OFFER_ACCEPTED);

        assertThatThrownBy(() -> service.accept(CONTRACT, request(1, null)))
                .isInstanceOf(ContractAlreadyAcceptedException.class).hasMessageContaining("версии 2");
        verify(acceptances, never()).save(any());
    }

    @Test
    void acceptanceOfAnOutdatedVersion_namesTheCurrentOneAndChangesNothing() {
        contract.advanceVersion(NOW);

        assertThatThrownBy(() -> service.accept(CONTRACT, request(1, null)))
                .isInstanceOfSatisfying(DocumentVersionMismatchException.class,
                        mismatch -> assertThat(mismatch.currentVersion()).isEqualTo(2));
        assertThat(contract.status()).isEqualTo(ContractStatus.DRAFT_READY);
        verify(acceptances, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"NEW", "ISSUED", "ERROR", "CANCELLED"})
    void contractOutsideAcceptance_isInvalidStateBeforeAnyVersionCheck(ContractStatus status) {
        ReflectionTestUtils.setField(contract, "status", status);

        assertThatThrownBy(() -> service.accept(CONTRACT, request(7, null)))
                .isInstanceOf(ContractStateException.class).hasMessageContaining(status.name());
        verify(acceptances, never()).save(any());
    }

    @Test
    void unknownContract_isNotFound() {
        given(contracts.findLockedById(CONTRACT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(CONTRACT, request(1, null)))
                .isInstanceOf(ContractNotFoundException.class).hasMessageContaining(CONTRACT.toString());
    }

    @Test
    void contractRowHeldLongerThanTheLockTimeout_isBusyWithTheLockFailureAsCause() {
        CannotAcquireLockException timeout = new CannotAcquireLockException("строка договора занята");
        given(contracts.findLockedById(CONTRACT)).willThrow(timeout);

        assertThatThrownBy(() -> service.accept(CONTRACT, request(1, null)))
                .isInstanceOfSatisfying(ContractBusyException.class,
                        busy -> assertThat(busy.refusal()).isEqualTo(AcceptanceRefusal.CONTRACT_BUSY))
                .hasCause(timeout).hasMessageContaining(CONTRACT.toString());
        verify(acceptances, never()).save(any());
    }

    private ContractAcceptance saved() {
        ArgumentCaptor<ContractAcceptance> saved = ArgumentCaptor.captor();
        verify(acceptances).save(saved.capture());
        return saved.getValue();
    }

    private static AcceptanceRequest request(int version, String ipAddress) {
        return new AcceptanceRequest(version, USER, ACCEPTED_AT, AcceptanceChannel.WEB, ipAddress, "Mozilla/5.0 (Стенд)",
                EMAIL);
    }

    private static OfferAcceptance offer(int version) {
        return new OfferAcceptance(version, USER, Instant.parse("2026-02-10T07:00:00Z"), AcceptanceChannel.WEB, null,
                null);
    }
}
