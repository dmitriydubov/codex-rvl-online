package ru.projectx.contracts.service.usk;

import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.data.domain.Limit;
import ru.projectx.contracts.entity.IssuanceRecord;
import ru.projectx.contracts.entity.IssuanceStatus;
import ru.projectx.contracts.exception.MessageTooLargeException;
import ru.projectx.contracts.repository.UskRecordRepository;
import ru.projectx.contracts.service.IssueErrors;
import ru.projectx.contracts.service.RequestIds;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;
import ru.projectx.contracts.service.reference.IssuanceReferences;
import ru.projectx.contracts.service.reference.ReferenceMetrics;
import ru.projectx.contracts.service.reference.RequestResolution;

public class UskRequests<R extends IssuanceRecord, S extends IssuanceStatus> {

    private final IssuanceKind<R, S> kind;
    private final UskRecordRepository<R, S> records;
    private final UskRequestRecording<R, S> recording;
    private final IssuanceReferences references;
    private final ReferenceMetrics metrics;
    private final Limit batch;
    private final Logger log;

    protected UskRequests(IssuanceKind<R, S> kind, UskRecordRepository<R, S> records,
            UskRequestRecording<R, S> recording, IssuanceReferences references, ReferenceMetrics metrics,
            int batchSize, Logger log) {
        this.kind = kind;
        this.records = records;
        this.recording = recording;
        this.references = references;
        this.metrics = metrics;
        this.batch = Limit.of(batchSize);
        this.log = log;
    }

    public int prepareDue() {
        int prepared = 0;
        for (R record : records.findByStatusOrderByCreatedAtAscIdAsc(kind.readyForUsk(), batch)) {
            if (!prepare(record)) {
                break;
            }
            prepared++;
        }
        return prepared;
    }

    private boolean prepare(R record) {
        MDC.put(RequestIds.MDC_KEY, record.id().toString());
        try {
            return prepareWithItsRequestId(record);
        }
        finally {
            MDC.remove(RequestIds.MDC_KEY);
        }
    }

    private boolean prepareWithItsRequestId(R record) {
        switch (references.resolve(record.state())) {
            case RequestResolution.Resolved resolved -> submit(record, resolved.identifiers());
            case RequestResolution.Refused refused -> recording.refuse(record.id(), refused.error());
            case RequestResolution.Unavailable unavailable -> {
                postpone(record, unavailable);
                return false;
            }
        }
        return true;
    }

    private void submit(R record, ExternalIdentifiers identifiers) {
        try {
            recording.submit(record.id(), identifiers);
        } catch (MessageTooLargeException tooLarge) {
            warn(record, tooLarge.getMessage());
            recording.refuse(record.id(), IssueErrors.kmContractMessageTooLarge(tooLarge.limit(), tooLarge.documents()));
        }
    }

    private void postpone(R record, RequestResolution.Unavailable unavailable) {
        warn(record, kind.wording().postponed());
        metrics.countUnavailable(unavailable.kind());
    }

    private void warn(R record, String reason) {
        log.warn("{} {} участника {} заявки {}: {}", kind.wording().kind(), record.id(), record.participantId(),
                record.state().request().individualRequestId(), reason);
    }
}
