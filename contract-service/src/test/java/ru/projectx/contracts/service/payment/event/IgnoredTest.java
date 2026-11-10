package ru.projectx.contracts.service.payment.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.projectx.contracts.service.payment.IgnoredEvent;

class IgnoredTest {

    @Test
    void eventThatDoesNotChangeTheOrder_isNotApplicable() {
        assertThat(Ignored.notApplicable("canceled"))
                .isEqualTo(new Ignored(IgnoredEvent.NOT_APPLICABLE, "событие canceled заказ не меняет"));
    }

    @Test
    void eventOutOfOrder_namesWhereTheOrderStands() {
        assertThat(Ignored.outOfOrder("fixed", "ждёт оплаты"))
                .isEqualTo(new Ignored(IgnoredEvent.OUT_OF_ORDER, "событие fixed не по порядку: заказ ждёт оплаты"));
    }
}
