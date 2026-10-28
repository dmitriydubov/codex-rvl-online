package ru.projectx.contracts.service.usk;

import java.util.List;
import java.util.UUID;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;
import ru.projectx.contracts.entity.issuance.IssuanceStatus;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.issuance.IssuedPolicy;
import ru.projectx.contracts.entity.document.UskDocument;
import ru.projectx.contracts.message.usk.UskResult;
import ru.projectx.contracts.service.inbox.InboxOrdering;
import ru.projectx.contracts.service.inbox.InboxWork;
import ru.projectx.contracts.service.inbox.ParsedEventHandler;
import ru.projectx.contracts.service.inbox.parse.PayloadParser;
import ru.projectx.contracts.service.reference.IssuedPolicyLookup;

public class UskResultHandler<R extends IssuanceRecord, S extends IssuanceStatus> extends ParsedEventHandler<UskResult> {

    private final IssuanceKind<R, S> kind;
    private final IssuedPolicyLookup lookup;
    private final UskResultRecording<R, S> recording;

    protected UskResultHandler(PayloadParser<UskResult> parser, IssuanceKind<R, S> kind, IssuedPolicyLookup lookup,
            UskResultRecording<R, S> recording) {
        super(parser);
        this.kind = kind;
        this.lookup = lookup;
        this.recording = recording;
    }

    @Override
    public String eventType() {
        return kind.resultEventType();
    }

    @Override
    public InboxOrdering ordering() {
        return InboxOrdering.NONE;
    }

    @Override
    protected InboxWork work(UskResult result) {
        UUID id = result.id();
        if (recording.expect(id) == ResultExpectation.ALREADY_FINISHED) {
            return now -> recording.recordLate(id);
        }
        return switch (result) {
            case UskResult.Issued issued -> issuedWork(issued);
            case UskResult.Failed failed -> now -> recording.recordFailure(id,
                    new IssueError(failed.errorCode(), failed.errorMessage()), now);
        };
    }

    private InboxWork issuedWork(UskResult.Issued issued) {
        UUID ourPolicyId = lookup.find(issued.policyExternalId());
        IssuedPolicy policy = new IssuedPolicy(issued.uskId(), issued.uskNumber(), ourPolicyId, issued.policyNumber());
        List<UskDocument> documents = UskDocuments.of(issued.documents());
        return now -> recording.recordIssued(issued.id(), policy, documents, now);
    }
}
