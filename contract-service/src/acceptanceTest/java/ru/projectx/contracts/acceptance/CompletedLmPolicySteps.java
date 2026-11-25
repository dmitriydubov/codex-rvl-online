package ru.projectx.contracts.acceptance;

import io.cucumber.java.ru.Дано;
import java.util.Map;

public class CompletedLmPolicySteps {

    private static final String INITIAL_RESULT = "5b1e7d44-2c09-4a63-8f17-6e0b9a3c1d05";
    private static final String ISSUANCE_FAILED = "отказ 1С:УСК";
    private static final String ISSUANCE_SUCCEEDED = "успешный выпуск 1С:УСК";
    private static final String REQUEST_CANCELLED = "прекращение работы Заявками";

    private final SubmittedLmPolicySteps submitted;
    private final ReferenceStubSteps reference;
    private final OneCPublishingSteps results;
    private final TaskPublishingSteps tasks;
    private final InboxSteps inbox;
    private final OutboxSteps outbox;
    private final OptionalIssuanceSteps issuance;
    private final IssuanceSteps issuanceStatus;

    public CompletedLmPolicySteps(SubmittedLmPolicySteps submitted, ReferenceStubSteps reference,
            OneCPublishingSteps results, TaskPublishingSteps tasks, InboxSteps inbox, OutboxSteps outbox,
            OptionalIssuanceSteps issuance, IssuanceSteps issuanceStatus) {
        this.submitted = submitted;
        this.reference = reference;
        this.results = results;
        this.tasks = tasks;
        this.inbox = inbox;
        this.outbox = outbox;
        this.issuance = issuance;
        this.issuanceStatus = issuanceStatus;
    }

    @Дано("по заявке {string} версии {int} выпущен полис ЛМ участника {string} и сервису Заявок отправлен ответ о завершении")
    public void policyIssuedAndReported(String requestId, int version, String participantId) {
        submitted.submitPolicy(requestId, participantId, version);
        reference.policyDefined(Map.of("id", "13ab5388-61ee-4e21-8a46-c325478b78c0",
                "external_id", OneCMessages.ISSUED_EXAMPLE.get("policy.external_id"), "номер", "0555123456",
                "в архиве", "false"));
        completePolicy(participantId, requestId, ISSUANCE_SUCCEEDED, INITIAL_RESULT);
        issuance.rememberPolicy(participantId, requestId);
        outbox.publicationPass();
    }

    @Дано("для полиса ЛМ участника {string} заявки {string} обработано событие {string} с message_id {string}")
    public void completePolicy(String participantId, String requestId, String event, String messageId) {
        switch (event) {
            case ISSUANCE_SUCCEEDED -> results.resultPublished(messageId, participantId, requestId,
                    OneCMessages.ISSUED_EXAMPLE);
            case ISSUANCE_FAILED -> results.resultPublished(messageId, participantId, requestId,
                    Map.of("status", "ERROR", "error_code", "INSURED_NOT_COVERED", "error_message", "Стенд: отказ"));
            case REQUEST_CANCELLED -> tasks.cancellationPublished(messageId, requestId, 2,
                    Map.of("аннулировано", "2026-04-01T10:00:00Z", "причина", "INSURED_REFUSED"));
            default -> throw new AssertionError("неизвестное событие завершения: " + event);
        }
        inbox.processingPass();
        inbox.processed(REQUEST_CANCELLED.equals(event) ? MessageSender.called("сервиса Заявок")
                : MessageSender.called("1С:УСК"), messageId);
        if (REQUEST_CANCELLED.equals(event)) {
            issuanceStatus.latestIssuance(participantId, requestId, Map.of("документ", "полис ЛМ", "статус", "CANCELLED"));
        }
    }
}
