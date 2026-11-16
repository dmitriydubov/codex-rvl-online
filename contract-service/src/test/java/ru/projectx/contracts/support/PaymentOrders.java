package ru.projectx.contracts.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStep;
import ru.projectx.contracts.entity.payment.OrderDocument;
import ru.projectx.contracts.entity.payment.OrderDraft;
import ru.projectx.contracts.entity.payment.OrderRequest;
import ru.projectx.contracts.entity.payment.PayerEmail;
import ru.projectx.contracts.entity.payment.PaymentOrder;

public final class PaymentOrders {

    public static final UUID CONTRACT = UUID.fromString("3f77b1c8-2a55-4c19-9e30-6b8d2f0a71c4");
    public static final UUID ORDER = UUID.fromString("0196f0a2-0000-7000-8000-000000000011");
    public static final UUID CORRELATION = UUID.fromString("0196f0a2-0000-7000-8000-0000000000aa");
    public static final String ORDER_ID = "0196f0a2-5b1c-7d3e-9f40-1a2b3c4d5e01";
    public static final OrderDocument DOCUMENT = new OrderDocument("DKM-2026-000001", new BigDecimal("34000.00"),
            Instant.parse("2026-02-10T07:04:58Z"), Instant.parse("2026-04-09T20:59:59Z"));
    public static final PayerEmail PAYER = new PayerEmail("insured@example.test");

    private PaymentOrders() {
    }

    public static Contract acceptedContract() {
        Contract contract = Issuances.contract(CONTRACT.toString(), Tasks.SPOUSE, "DKM-2026-000001");
        contract.offerDraft();
        contract.advance(ContractStep.OFFER_ACCEPTED);
        return contract;
    }

    public static PaymentOrder awaiting() {
        PaymentOrder order = PaymentOrder.open(new OrderDraft(CONTRACT, Tasks.INSURED, DOCUMENT, PAYER),
                Issuances.OPENED);
        ReflectionTestUtils.setField(order, "id", ORDER);
        ReflectionTestUtils.setField(order, "correlationId", CORRELATION);
        return order;
    }

    public static PaymentOrder created() {
        PaymentOrder order = awaiting();
        order.markRequested(OrderRequest.CREATION, Issuances.OPENED);
        order.recordOrder(ORDER_ID, Issuances.OPENED);
        return order;
    }

    public static PaymentOrder cancelPending() {
        PaymentOrder order = created();
        order.withdraw(Issuances.OPENED);
        return order;
    }
}
