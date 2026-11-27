package ru.projectx.contracts.service.storage;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;
import ru.projectx.contracts.service.RequestIds;

@Component
public class AttachmentDeletionTransactions {

    private final AttachmentRegistry registry;
    private final PrintedFormRepository documents;
    private final CertificateDeletionGuard guard;
    private final Clock clock;

    public AttachmentDeletionTransactions(AttachmentRegistry registry, PrintedFormRepository documents,
            CertificateDeletionGuard guard, Clock clock) {
        this.registry = registry;
        this.documents = documents;
        this.guard = guard;
        this.clock = clock;
    }

    @Transactional
    public Optional<PrintedForm> prepare(UUID attachmentId) {
        Optional<PrintedForm> candidate = registry.active(attachmentId);
        candidate.ifPresent(attachment -> guard.requireUnsubmitted(attachment, guard.lock(attachment)));
        return candidate;
    }

    @Transactional
    public void complete(UUID attachmentId, PrintedForm candidate) {
        List<Contract> locked = guard.lock(candidate);
        registry.active(attachmentId).ifPresent(attachment -> {
            guard.requireUnsubmitted(attachment, locked);
            documents.markAttachmentDeleted(attachmentId, clock.instant(), RequestIds.current());
        });
    }
}
