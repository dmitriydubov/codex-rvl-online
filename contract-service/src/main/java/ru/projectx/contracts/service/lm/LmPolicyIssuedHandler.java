package ru.projectx.contracts.service.lm;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.IssuedPolicy;
import ru.projectx.contracts.entity.PrintedFormType;
import ru.projectx.contracts.entity.UskDocument;
import ru.projectx.contracts.message.IssuedDocument;
import ru.projectx.contracts.message.LmPolicyEvents;
import ru.projectx.contracts.message.LmPolicyResult;
import ru.projectx.contracts.service.inbox.InboxOrdering;
import ru.projectx.contracts.service.inbox.InboxWork;
import ru.projectx.contracts.service.inbox.LmPolicyIssuedParser;
import ru.projectx.contracts.service.inbox.ParsedEventHandler;

@Component
public class LmPolicyIssuedHandler extends ParsedEventHandler<LmPolicyResult> {

    private final IssuedPolicyLookup lookup;
    private final IssuedPolicyRecording recording;

    public LmPolicyIssuedHandler(LmPolicyIssuedParser parser, IssuedPolicyLookup lookup,
            IssuedPolicyRecording recording) {
        super(parser);
        this.lookup = lookup;
        this.recording = recording;
    }

    @Override
    public String eventType() {
        return LmPolicyEvents.LM_POLICY_ISSUED;
    }

    @Override
    public InboxOrdering ordering() {
        return InboxOrdering.NONE;
    }

    @Override
    protected InboxWork work(LmPolicyResult result) {
        if (recording.expect(result.id()) == PolicyExpectation.ALREADY_FINISHED) {
            return now -> recording.recordLate(result.id());
        }
        return switch (result) {
            case LmPolicyResult.Issued issued -> issuedWork(issued);
            case LmPolicyResult.Failed failed -> now -> recording.recordFailure(failed.id(),
                    new IssueError(failed.errorCode(), failed.errorMessage()), now);
        };
    }

    private InboxWork issuedWork(LmPolicyResult.Issued issued) {
        UUID ourPolicyId = lookup.find(issued.policyExternalId());
        IssuedPolicy policy = new IssuedPolicy(issued.policyUskId(), issued.policyUskNumber(), ourPolicyId,
                issued.policyNumber());
        List<UskDocument> documents = issued.documents().stream().map(LmPolicyIssuedHandler::document).toList();
        return now -> recording.recordIssued(issued.id(), policy, documents, now);
    }

    private static UskDocument document(IssuedDocument document) {
        return new UskDocument(PrintedFormType.valueOf(document.docType()), document.fileUrl(), document.fileSize());
    }
}
