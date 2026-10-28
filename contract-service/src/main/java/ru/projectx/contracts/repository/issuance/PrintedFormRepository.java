package ru.projectx.contracts.repository.issuance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.projectx.contracts.entity.document.PrintedForm;

public interface PrintedFormRepository extends JpaRepository<PrintedForm, UUID> {

    List<PrintedForm> findByLmPolicyIdInOrContractIdInOrderByCreatedAtAscIdAsc(Collection<UUID> lmPolicyIds,
            Collection<UUID> contractIds);

    Optional<PrintedForm> findByAttachmentAttachmentId(UUID attachmentId);

    List<PrintedForm> findByAttachmentIndividualRequestIdAndAttachmentParticipantIdOrderByCreatedAtAscIdAsc(
            UUID individualRequestId, UUID participantId);
}
