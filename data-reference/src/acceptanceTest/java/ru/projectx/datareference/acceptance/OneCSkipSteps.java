package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Тогда;
import java.util.Map;
import java.util.UUID;
import ru.projectx.datareference.entity.InboundStatus;

/**
 * Шаги о пропущенных сообщениях 1С:УСК: сообщение, которое разбор не применяет по отбору своего потока, и его
 * исход в строке сырья.
 */
public class OneCSkipSteps {

    private final OneCInbox inbox;

    public OneCSkipSteps(OneCInbox inbox) {
        this.inbox = inbox;
    }

    @Дано("1С:УСК прислала договор страхования продукта {string}:")
    public void insuranceContractOfProductSent(String productType, Map<String, String> contract) {
        inbox.receive(OneCMessages.insuranceContractOfProduct(productType, contract));
    }

    /** «Пропущено» значит статус {@code SKIPPED}; причина сравнивается дословно. */
    @Тогда("последнее сообщение 1С:УСК пропущено с причиной {string}")
    public void lastMessageSkipped(String reason) {
        assertSkipped(inbox.last(), "последнего сообщения", reason);
    }

    @Тогда("сообщение 1С:УСК {string} пропущено с причиной {string}")
    public void messageSkipped(String messageId, String reason) {
        assertSkipped(inbox.row(UUID.fromString(messageId)), "сообщения " + messageId, reason);
    }

    private static void assertSkipped(InboundRow row, String whose, String reason) {
        assertThat(row.status()).as("статус %s", whose).isEqualTo(InboundStatus.SKIPPED.name());
        assertThat(row.lastError()).as("причина пропуска %s", whose).isEqualTo(reason);
    }
}
