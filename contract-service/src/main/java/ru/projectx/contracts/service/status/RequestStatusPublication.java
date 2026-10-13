package ru.projectx.contracts.service.status;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.entity.IssuanceRequest;
import ru.projectx.contracts.entity.OutboxDraft;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.RequestParticipant;
import ru.projectx.contracts.message.ContractTask;
import ru.projectx.contracts.message.MessageMeta;
import ru.projectx.contracts.message.StatusEvents;
import ru.projectx.contracts.repository.IssuanceRequestRepository;
import ru.projectx.contracts.service.DocumentKinds;
import ru.projectx.contracts.service.outbox.OutboxJournal;

@Service
public class RequestStatusPublication {

    static final String AGGREGATE_TYPE = "INDIVIDUAL_REQUEST";

    private final IssuanceRequestRepository requests;
    private final IssuanceSnapshots snapshots;
    private final StatusMessages messages;
    private final OutboxJournal journal;
    private final Clock clock;

    public RequestStatusPublication(IssuanceRequestRepository requests, IssuanceSnapshots snapshots,
            StatusMessages messages, OutboxJournal journal, Clock clock) {
        this.requests = requests;
        this.snapshots = snapshots;
        this.messages = messages;
        this.journal = journal;
        this.clock = clock;
    }

    public IssuanceSnapshot watch(UUID individualRequestId) {
        return snapshots.current(individualRequestId);
    }

    public void afterTask(IssuanceSnapshot before, ContractTask task, Instant now) {
        IssuanceRequest request = requestOf(task.individualRequestId(), now);
        request.takeComposition(composition(task), now);
        IssuanceSnapshot after = snapshots.current(request.individualRequestId());
        if (StatusTransitions.publishAfterTask(before, after)) {
            record(request, after, now);
        }
    }

    public void afterCancellation(UUID individualRequestId, Instant now) {
        IssuanceRequest request = requestOf(individualRequestId, now);
        request.cancel(now);
        record(request, snapshots.current(individualRequestId), now);
    }

    private IssuanceRequest requestOf(UUID individualRequestId, Instant now) {
        return requests.findById(individualRequestId)
                .orElseGet(() -> requests.save(IssuanceRequest.start(individualRequestId, now)));
    }

    private static List<RequestParticipant> composition(ContractTask task) {
        return task.participants().stream()
                .map(participant -> new RequestParticipant(participant.id(), DocumentKinds.of(participant)))
                .toList();
    }

    private void record(IssuanceRequest request, IssuanceSnapshot snapshot, Instant now) {
        UUID individualRequestId = request.individualRequestId();
        MessageMeta meta = new MessageMeta(UUID.randomUUID(), individualRequestId, StatusEvents.CONTRACT_STATUS_TOPIC,
                StatusEvents.CONTRACT_STATUS_CHANGED, StatusEvents.SCHEMA_VERSION,
                OffsetDateTime.ofInstant(now, clock.getZone()), request.nextSnapshotVersion(now), StatusEvents.SOURCE);
        MessageEnvelope envelope = new MessageEnvelope(meta.messageId(), individualRequestId, meta.topic(),
                meta.eventType(), meta.entityVersion());
        journal.record(new OutboxDraft(AGGREGATE_TYPE, individualRequestId, envelope, messages.write(snapshot, meta)),
                now);
    }
}
