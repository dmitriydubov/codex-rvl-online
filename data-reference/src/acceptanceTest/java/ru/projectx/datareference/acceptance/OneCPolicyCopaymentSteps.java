package ru.projectx.datareference.acceptance;

import io.cucumber.java.ru.Дано;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import ru.projectx.datareference.entity.inbound.OneCStream;
import tools.jackson.databind.node.ObjectNode;

public class OneCPolicyCopaymentSteps {

    private static final String COPAYMENT = "copayment";
    private static final Map<String, Consumer<ObjectNode>> SENT_AS = Map.of(
            "нет", row -> row.remove(COPAYMENT),
            "null", row -> row.putNull(COPAYMENT),
            "текст", row -> row.put(COPAYMENT, "0"),
            "логическое значение", row -> row.put(COPAYMENT, true));

    private final OneCInbox inbox;

    public OneCPolicyCopaymentSteps(OneCInbox inbox) {
        this.inbox = inbox;
    }

    @Дано("1С:УСК прислала тот же полис под новым номером, а поле {string} — {string}")
    public void policyWithCopaymentSentAs(String field, String sentAs) {
        if (!COPAYMENT.equals(field)) {
            throw new IllegalArgumentException("шаг не знает поля строки условий полиса: " + field);
        }
        Consumer<ObjectNode> change = Optional.ofNullable(SENT_AS.get(sentAs))
                .orElseThrow(() -> new IllegalArgumentException("шаг не знает, как прислано поле: " + sentAs));
        OneCMessage policy = OneCMoneyScaleMessages.renamedPolicy(
                inbox.lastOf(OneCStream.POLICIES), COPAYMENT, BigDecimal.ZERO);
        change.accept((ObjectNode) policy.data().withArray("services").get(0));
        inbox.receive(policy);
    }
}
