package ru.projectx.contracts.service.payment.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.outbox.OutboxDraft;
import ru.projectx.contracts.support.OrderingTestProperties;
import ru.projectx.contracts.support.PaymentOrders;
import ru.projectx.contracts.support.Tasks;
import tools.jackson.databind.json.JsonMapper;

class DocumentFixMessagesTest {

    private final DocumentFixMessages messages =
            new DocumentFixMessages(JsonMapper.builder().build(), OrderingTestProperties.attempts(3));

    @Test
    void draft_isTheOrderingBodyWithoutOurEnvelopeAddressedByTheOrderRecord() {
        OutboxDraft draft = messages.draft(PaymentOrders.acceptedContract(), PaymentOrders.created(),
                Instant.parse("2026-02-10T07:10:00Z"));

        assertThat(draft.payload()).isEqualTo("{\"source\":\"rvl\",\"eventType\":\"fixed\","
                + "\"data\":{\"agreementId\":\"" + PaymentOrders.ORDER + "\"}}");
        assertThat(draft.aggregateType()).isEqualTo("PAYMENT_ORDER");
        assertThat(draft.aggregateId()).isEqualTo(PaymentOrders.ORDER);
        assertThat(draft.envelope().eventType()).isEqualTo("PaymentOrderFixed");
        assertThat(draft.envelope().correlationId()).isEqualTo(Tasks.REQUEST);
        assertThat(draft.envelope().topic()).isEqualTo("ordering.document.fixed");
        assertThat(draft.envelope().entityVersion()).isEqualTo(1);
        assertThat(draft.envelope().messageId()).isNotNull();
    }

    @Test
    void everyDraft_hasItsOwnMessageId() {
        OutboxDraft first = messages.draft(PaymentOrders.acceptedContract(), PaymentOrders.created(), Instant.EPOCH);
        OutboxDraft second = messages.draft(PaymentOrders.acceptedContract(), PaymentOrders.created(), Instant.EPOCH);

        assertThat(first.envelope().messageId()).isNotEqualTo(second.envelope().messageId());
    }
}
