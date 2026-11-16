package ru.projectx.contracts.service.payment.order;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ru.projectx.contracts.entity.contract.AcceptanceChannel;
import ru.projectx.contracts.entity.contract.OfferAcceptance;
import ru.projectx.contracts.service.payment.OrderOpening;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.PaymentOrders;
import ru.projectx.contracts.support.Tasks;

class DisabledPaymentOrdersTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final OfferAcceptance OFFER = new OfferAcceptance(1, Tasks.INSURED, NOW, AcceptanceChannel.WEB,
            null, null);

    private final DisabledPaymentOrders disabled = new DisabledPaymentOrders();
    private final Logger logger = (Logger) LoggerFactory.getLogger(DisabledPaymentOrders.class);
    private Level levelBefore;

    @BeforeEach
    void letDebugRecordsThrough() {
        levelBefore = logger.getLevel();
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void restoreLevel() {
        logger.setLevel(levelBefore);
    }

    @Test
    void acceptanceWithoutAddress_isNotRefusedWhilePaymentIsOff() {
        OrderOpening opening = disabled.forPayer(null);

        assertThat(opening).isNotNull();
    }

    @Test
    void opening_onlyNotesThatNoOrderIsOpenedAndKeepsTheAddressOutOfTheLog() {
        try (CapturedLog log = CapturedLog.of(DisabledPaymentOrders.class)) {
            disabled.forPayer("insured@example.test").open(PaymentOrders.acceptedContract(), OFFER, NOW);

            assertThat(log.at(Level.DEBUG)).containsExactly(
                    "оплата выключена: заказ на оплату договора КМ " + PaymentOrders.CONTRACT + " не заводится");
            assertThat(log.events()).hasSize(1);
        }
    }
}
