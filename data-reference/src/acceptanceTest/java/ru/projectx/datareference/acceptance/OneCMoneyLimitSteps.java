package ru.projectx.datareference.acceptance;

import io.cucumber.java.ru.Дано;
import java.math.BigDecimal;
import java.util.UUID;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Шаги «1С:УСК прислала …» о денежных полях прайса, полиса и программы, которые не помещаются в колонку. Программа и
 * полис строятся из присланных сценарием раньше, прайс — заново. Ожидаемый исход разбора несёт само сообщение.
 */
public class OneCMoneyLimitSteps {

    private final OneCInbox inbox;

    public OneCMoneyLimitSteps(OneCInbox inbox) {
        this.inbox = inbox;
    }

    @Дано("1С:УСК прислала пакет {string} передачи прайса {string} по услуге {string} из одной строки, где поле {string} — число на одну сотую больше {bigdecimal}")
    public void priceBatchPastLimitSent(String messageId, String cid, String service, String field, BigDecimal value) {
        inbox.receive(OneCMoneyScaleMessages.priceBatch(UUID.fromString(messageId), UUID.fromString(cid), service, field,
                value.add(OneCMoneyLimitMessages.ONE_HUNDREDTH)));
    }

    @Дано("1С:УСК прислала пакет {string} передачи прайса {string} по услуге {string} из одной строки, где поле {string} — число, записанное как {string}")
    public void priceBatchRecordedSent(String messageId, String cid, String service, String field, String record) {
        inbox.receive(OneCMoneyScaleMessages.priceBatch(UUID.fromString(messageId), UUID.fromString(cid), service, field,
                OneCMoneyLimitMessages.recorded(field, record)));
    }

    @Дано("1С:УСК прислала тот же полис под новым номером, а поле {string} — число, записанное как {string}")
    public void policyRecordedSent(String field, String record) {
        inbox.receive(OneCMoneyScaleMessages.renamedPolicy(
                inbox.lastOf(OneCStream.POLICIES), field, OneCMoneyLimitMessages.recorded(field, record)));
    }

    @Дано("1С:УСК прислала тот же полис под новым номером, а в его программе ещё незнакомое поле — число, записанное как {string}")
    public void policyWithUnknownFieldSent(String record) {
        inbox.receive(OneCMoneyScaleMessages.renamedPolicyWithUnknownField(
                inbox.lastOf(OneCStream.POLICIES), OneCMoneyLimitMessages.recorded("bonusAmount", record)));
    }

    @Дано("1С:УСК прислала ту же программу РВЛ под новым наименованием, а в строке услуги поле {string} — число, записанное как {string}")
    public void programRecordedSent(String field, String record) {
        inbox.receive(OneCMoneyLimitMessages.program(inbox.lastOf(OneCStream.PROGRAMS), field, record));
    }
}
