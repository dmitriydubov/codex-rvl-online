package ru.projectx.contracts.repository.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.support.Invoices;

class FreshInvoicesImplTest {

    private static final UUID CONTRACT = UUID.fromString("3f77b1c8-2a55-4c19-9e30-6b8d2f0a71c4");
    private static final UUID OTHER_CONTRACT = UUID.fromString("4a2b3c5d-6e7f-4081-9a2b-1c2d3e4f5a6b");
    private static final String BY_CONTRACT = "select i from OrderInvoice i where i.contractId = :contractId";

    private final EntityManager entityManager = mock();
    private final TypedQuery<OrderInvoice> ofContract = mock();
    private final TypedQuery<OrderInvoice> ofOtherContract = mock();
    private final FreshInvoicesImpl invoices = new FreshInvoicesImpl(entityManager);
    private final OrderInvoice invoice = Invoices.issued(CONTRACT);

    @BeforeEach
    void queryIsBoundToTheGivenContract() {
        TypedQuery<OrderInvoice> query = mock();
        given(entityManager.createQuery(BY_CONTRACT, OrderInvoice.class)).willReturn(query);
        given(query.setParameter("contractId", CONTRACT)).willReturn(ofContract);
        given(query.setParameter("contractId", OTHER_CONTRACT)).willReturn(ofOtherContract);
        given(ofContract.getResultStream()).willAnswer(invocation -> Stream.of(invoice));
        given(ofOtherContract.getResultStream()).willAnswer(invocation -> Stream.empty());
    }

    @Test
    void invoiceOfTheContract_isReadAfreshFromTheDatabaseAndReturnedAsTheSameRecord() {
        Optional<OrderInvoice> found = invoices.findFreshByContractId(CONTRACT);

        assertThat(found).containsSame(invoice);
        InOrder order = inOrder(ofContract, entityManager);
        order.verify(ofContract).getResultStream();
        order.verify(entityManager).refresh(invoice);
    }

    @Test
    void contractWithoutInvoice_givesNothingAndNothingIsReadAfresh() {
        assertThat(invoices.findFreshByContractId(OTHER_CONTRACT)).isEmpty();

        verify(ofOtherContract).getResultStream();
        verify(ofContract, never()).getResultStream();
        verify(entityManager, never()).refresh(any());
    }

    @Test
    void firstRecordOfTheQuery_isTheOneReadAfresh() {
        OrderInvoice another = Invoices.ready(CONTRACT);
        given(ofContract.getResultStream()).willAnswer(invocation -> Stream.of(invoice, another));

        assertThat(invoices.findFreshByContractId(CONTRACT)).containsSame(invoice);

        verify(entityManager).refresh(invoice);
        verify(entityManager, never()).refresh(another);
    }
}
