package ru.projectx.contracts.service.payment.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(strings = {"created", "order.show", "order_hide-2", "A", "Z9"})
    void typeMadeOfLettersDigitsDotsDashesAndUnderscores_isNamedInTheText(String eventType) {
        assertThat(Ignored.notApplicable(eventType).text()).isEqualTo("событие " + eventType + " заказ не меняет");
    }

    @Test
    void typeOfSixtyFourCharacters_isStillNamedInTheText() {
        String longest = "a".repeat(64);

        assertThat(Ignored.notApplicable(longest).text()).isEqualTo("событие " + longest + " заказ не меняет");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "order show", "оплата", "paid\nERROR", "insured@example.test", "paid;drop"})
    void typeThatIsNotAPlainIdentifier_staysOutOfTheText(String eventType) {
        assertThat(Ignored.notApplicable(eventType))
                .isEqualTo(new Ignored(IgnoredEvent.NOT_APPLICABLE, "событие с типом не по формату заказ не меняет"));
    }

    @Test
    void typeOfSixtyFiveCharacters_staysOutOfTheText() {
        assertThat(Ignored.notApplicable("a".repeat(65)).text())
                .isEqualTo("событие с типом не по формату заказ не меняет");
    }
}
