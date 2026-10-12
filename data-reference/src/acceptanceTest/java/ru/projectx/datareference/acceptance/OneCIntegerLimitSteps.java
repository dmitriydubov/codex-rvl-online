package ru.projectx.datareference.acceptance;

import io.cucumber.java.ru.Дано;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import tools.jackson.databind.node.ObjectNode;

public class OneCIntegerLimitSteps {

    private static final String RENAMED = "Стенд: новое наименование";
    private static final Map<String, OneCStream> STREAMS = Map.of(
            "МКБ-10", OneCStream.MKB10, "виды обращений", OneCStream.APPEAL_TYPES);
    private static final Map<OneCStream, String> LEVEL_FIELDS = Map.of(
            OneCStream.MKB10, "hierarchyLevels", OneCStream.APPEAL_TYPES, "hierarchyLevel");

    private final OneCInbox inbox;

    public OneCIntegerLimitSteps(OneCInbox inbox) {
        this.inbox = inbox;
    }

    @Дано("1С:УСК прислала в потоке {string} ту же запись под новым наименованием, а поле {string} — число, записанное как {string}")
    public void referenceRecordedSent(String streamWord, String field, String record) {
        OneCStream stream = Optional.ofNullable(STREAMS.get(streamWord))
                .orElseThrow(() -> new IllegalArgumentException("шаг не знает потока: " + streamWord));
        if (!LEVEL_FIELDS.get(stream).equals(field)) {
            throw new IllegalArgumentException("шаг не знает поля " + field + " потока " + streamWord);
        }
        OneCMessage previous = inbox.lastOf(stream);
        ObjectNode data = previous.data().deepCopy()
                .put("name", RENAMED)
                .put(field, OneCMoneyLimitMessages.recorded(field, record));
        inbox.receive(new OneCMessage(stream, previous.queue(), data, InboundStatus.FAILED));
    }

    @Дано("1С:УСК прислала финальное сообщение {string} передачи прайса {string} по услуге {string}, где поле {string} — число, записанное как {string}")
    public void finalMessageWithCounterSent(String messageId, String cid, String service, String field, String record) {
        inbox.receive(OneCPriceTransferMessages.finalMessageWithCounter(UUID.fromString(messageId), UUID.fromString(cid),
                service, field, OneCMoneyLimitMessages.recorded(field, record)));
    }

    @Дано("1С:УСК прислала пакет {string} передачи прайса {string} по услуге {string} из одной строки, где поле {string} — число на единицу больше {bigdecimal}")
    public void priceBatchPastIntegerLimitSent(String messageId, String cid, String service, String field, BigDecimal limit) {
        inbox.receive(OneCMoneyScaleMessages.priceBatch(UUID.fromString(messageId), UUID.fromString(cid), service, field,
                limit.add(BigDecimal.ONE)));
    }
}
