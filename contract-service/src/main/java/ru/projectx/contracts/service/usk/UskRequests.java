package ru.projectx.contracts.service.usk;

import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.data.domain.Limit;
import ru.projectx.contracts.entity.IssuanceRecord;
import ru.projectx.contracts.entity.IssuanceStatus;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.exception.SubmissionRefusedException;
import ru.projectx.contracts.repository.UskRecordRepository;
import ru.projectx.contracts.service.RequestIds;
import ru.projectx.contracts.service.SqlStates;
import ru.projectx.contracts.service.TemporaryFailures;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;
import ru.projectx.contracts.service.reference.IssuanceReferences;
import ru.projectx.contracts.service.reference.ReferenceMetrics;
import ru.projectx.contracts.service.reference.RequestResolution;

public class UskRequests<R extends IssuanceRecord, S extends IssuanceStatus> {

    private static final String FAILURE_FORMAT = "{} {} участника {} заявки {}: {}, тип отказа {}, SQLSTATE {}";
    private static final String ERROR_NOT_WRITTEN = "передача не подготовлена, записать ошибку подготовки не удалось";
    private static final String NO_LONGER_READY = "запись уже не готова к передаче, ошибка подготовки не записана";

    private final IssuanceKind<R, S> kind;
    private final UskRecordRepository<R, S> records;
    private final UskRequestRecording<R, S> recording;
    private final IssuanceReferences references;
    private final ReferenceMetrics metrics;
    private final UskPreparationMetrics preparationMetrics;
    private final Limit batch;
    private final Logger log;

    protected UskRequests(IssuanceKind<R, S> kind, UskRecordRepository<R, S> records,
            UskRequestRecording<R, S> recording, IssuanceReferences references, ReferenceMetrics metrics,
            UskPreparationMetrics preparationMetrics, int batchSize, Logger log) {
        this.kind = kind;
        this.records = records;
        this.recording = recording;
        this.references = references;
        this.metrics = metrics;
        this.preparationMetrics = preparationMetrics;
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
        } catch (SubmissionRefusedException refused) {
            warn(record, refused.getMessage());
            recording.refuse(record.id(), new IssueError(refused.errorCode(), refused.errorMessage()));
        } catch (RuntimeException failure) {
            failPreparation(record, failure);
        }
    }

    private void failPreparation(R record, RuntimeException failure) {
        if (TemporaryFailures.temporary(failure)) {
            throw failure;
        }
        if (refuseAsNotPrepared(record, failure)) {
            preparationMetrics.countFailed(kind.requestEventType());
            logFailure(log.atError(), record, kind.wording().preparationFailed(), failure);
            return;
        }
        logFailure(log.atWarn(), record, NO_LONGER_READY, failure);
    }

    private boolean refuseAsNotPrepared(R record, RuntimeException failure) {
        try {
            return recording.refuse(record.id(), kind.errors().preparationFailed());
        } catch (RuntimeException refusal) {
            logFailure(log.atWarn(), record, ERROR_NOT_WRITTEN, failure);
            refusal.addSuppressed(failure);
            throw refusal;
        }
    }

    private void logFailure(LoggingEventBuilder event, R record, String reason, RuntimeException failure) {
        event.log(FAILURE_FORMAT, kind.wording().kind(), record.id(), record.participantId(),
                record.state().request().individualRequestId(), reason, failure.getClass().getName(),
                SqlStates.deepestOrNone(failure));
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
