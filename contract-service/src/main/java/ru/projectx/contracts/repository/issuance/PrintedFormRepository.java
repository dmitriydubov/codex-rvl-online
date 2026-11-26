package ru.projectx.contracts.repository.issuance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;

public interface PrintedFormRepository extends JpaRepository<PrintedForm, UUID> {

    List<PrintedForm> findByLmPolicyIdInOrContractIdInOrderByCreatedAtAscIdAsc(Collection<UUID> lmPolicyIds,
            Collection<UUID> contractIds);

    Optional<PrintedForm> findByAttachmentAttachmentId(UUID attachmentId);

    @Query("""
            select form from PrintedForm form
            where form.attachment.individualRequestId = :request and form.attachment.participantId = :participant
              and form.docType = :type and form.deletedAt is null
            order by form.createdAt, form.id
            """)
    List<PrintedForm> findActiveParticipantDocuments(@Param("request") UUID individualRequestId,
            @Param("participant") UUID participantId, @Param("type") PrintedFormType type);
}
