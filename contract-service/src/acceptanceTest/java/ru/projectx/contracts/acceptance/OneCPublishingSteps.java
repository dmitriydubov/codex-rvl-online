package ru.projectx.contracts.acceptance;

import io.cucumber.java.ru.Когда;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.node.ObjectNode;

public class OneCPublishingSteps {

    private static final String RESULTS = "результаты выпуска полисов ЛМ";

    private final OneCMessages messages;
    private final StandQueues queues;
    private final PublishedMessages published;
    private final IssuanceRecords records;

    public OneCPublishingSteps(OneCMessages messages, StandQueues queues, PublishedMessages published,
            IssuanceRecords records) {
        this.messages = messages;
        this.queues = queues;
        this.published = published;
        this.records = records;
    }

    @Когда("1С:УСК публикует в свою очередь результат выпуска {string} по полису ЛМ участника {string} заявки {string}:")
    public void resultPublished(String messageId, String participantId, String requestId, Map<String, String> fields) {
        publish(messageId, messages.issued(messageId, requestId, records.latestLmPolicyId(participantId, requestId),
                fields));
    }

    @Когда("1С:УСК публикует в свою очередь результат выпуска {string} по заявке {string} для полиса ЛМ с идентификатором {string}:")
    public void resultPublishedForPolicy(String messageId, String requestId, String policyId,
            Map<String, String> fields) {
        publish(messageId, messages.issued(messageId, requestId, UUID.fromString(policyId), fields));
    }

    @Когда("1С:УСК публикует в свою очередь результат выпуска {string} по полису ЛМ участника {string} заявки {string} с дефектом {string}")
    public void defectiveResultPublished(String messageId, String participantId, String requestId, String defect) {
        publish(messageId, messages.defective(messageId, requestId,
                records.latestLmPolicyId(participantId, requestId), defect));
    }

    private void publish(String messageId, ObjectNode body) {
        byte[] bytes = messages.bytes(body);
        published.remember(messageId, bytes);
        ((OneCQueue) queues.called(RESULTS)).publish(bytes);
    }
}
