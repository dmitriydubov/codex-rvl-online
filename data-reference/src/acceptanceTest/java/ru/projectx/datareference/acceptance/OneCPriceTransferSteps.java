package ru.projectx.datareference.acceptance;

import io.cucumber.java.ru.Дано;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Шаги «1С:УСК прислала …» о частях передачи прайса по услуге с заданными сценарием {@code message-id} и
 * идентификатором передачи. Сырьё ложится так, как его сохранил бы приём из очереди.
 */
public class OneCPriceTransferSteps {

    private final OneCInbox inbox;

    public OneCPriceTransferSteps(OneCInbox inbox) {
        this.inbox = inbox;
    }

    @Дано("1С:УСК прислала финальное сообщение {string} передачи прайса {string} по услуге {string}: пакетов {int}, строк {int}")
    public void finalMessageSent(String messageId, String cid, String service, int batches, int rows) {
        inbox.receive(OneCPriceTransferMessages.finalMessage(
                UUID.fromString(messageId), UUID.fromString(cid), service, batches, rows));
    }

    @Дано("1С:УСК прислала пакет {string} передачи прайса {string} по услуге {string}:")
    public void batchSent(String messageId, String cid, String service, List<Map<String, String>> rows) {
        inbox.receive(OneCPriceTransferMessages.batch(UUID.fromString(messageId), UUID.fromString(cid), service, rows));
    }

    @Дано("1С:УСК прислала пакет {string} передачи прайса {string} по услуге {string} из одной строки, в которой {string}")
    public void defectiveBatchSent(String messageId, String cid, String service, String defect) {
        inbox.receive(OneCPriceTransferMessages.defectiveBatch(
                UUID.fromString(messageId), UUID.fromString(cid), service, defect));
    }

    @Дано("1С:УСК прислала пакет {string} прайса по услуге {string} без идентификатора передачи:")
    public void batchWithoutTransferSent(String messageId, String service, List<Map<String, String>> rows) {
        inbox.receive(OneCPriceTransferMessages.batchWithoutTransfer(UUID.fromString(messageId), service, rows));
    }
}
