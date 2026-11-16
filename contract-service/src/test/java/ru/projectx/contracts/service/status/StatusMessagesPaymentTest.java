package ru.projectx.contracts.service.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.message.MessageMeta;
import ru.projectx.contracts.support.Invoices;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class StatusMessagesPaymentTest {

    private static final RequestParticipant SPOUSE_KM = new RequestParticipant(Tasks.SPOUSE, DocumentKind.KM_CONTRACT);
    private static final MessageMeta META = new MessageMeta(UUID.fromString("7d2a41c9-9f01-4f77-8a1c-2b9cf0e5a442"),
            Tasks.REQUEST, "rvl.contract-status.changed", "ContractStatusChanged", "1.0",
            OffsetDateTime.parse("2026-02-10T10:35:00+03:00"), 3, "RVL-CONTRACTS");

    private final JsonMapper json = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final StatusMessages messages = new StatusMessages(json);
    private final Contract invoiced = Issuances.invoicedContract("01960000-0000-7000-8000-000000000001", Tasks.SPOUSE,
            "DKM-2026-000001");

    @Test
    void contractWithInvoiceLink_carriesPendingPaymentAndTheLinkRightAfterThePaymentStatus() {
        JsonNode participant = participant(Invoices.issued(invoiced.id()));

        assertThat(participant.path("km_contract_state").stringValue()).isEqualTo("DRAFTED");
        assertThat(participant.path("payment_status").stringValue()).isEqualTo("PENDING");
        assertThat(participant.path("payment_url").stringValue()).isEqualTo(Invoices.PAYMENT_URL);
        List<String> fields = new ArrayList<>(participant.propertyNames());
        assertThat(fields.indexOf("payment_url")).isEqualTo(fields.indexOf("payment_status") + 1);
    }

    @Test
    void contractWithoutLinkToShow_carriesNoPaymentLinkFieldAtAll() {
        JsonNode lostReply = participant(Invoices.issuedWithoutLink(invoiced.id()));
        JsonNode deferred = participant(Invoices.cancellationDeferred(Invoices.issued(invoiced.id())));

        assertThat(lostReply.path("payment_status").stringValue()).isEqualTo("NOT_REQUIRED");
        assertThat(lostReply.has("payment_url")).isFalse();
        assertThat(deferred.path("km_contract_state").stringValue()).isEqualTo("CANCELLED");
        assertThat(deferred.path("payment_status").stringValue()).isEqualTo("NOT_REQUIRED");
        assertThat(deferred.has("payment_url")).isFalse();
    }

    private JsonNode participant(OrderInvoice invoice) {
        return json.readTree(messages.write(new IssuanceSnapshot(Tasks.REQUEST, false, List.of(SPOUSE_KM),
                List.of(invoiced), List.of(), List.of(), List.of(invoice.view())), META))
                .path("data").path("participants").path(0);
    }
}
