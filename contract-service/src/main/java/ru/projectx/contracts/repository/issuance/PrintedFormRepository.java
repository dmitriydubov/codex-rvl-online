package ru.projectx.contracts.repository.issuance;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;

public interface PrintedFormRepository extends JpaRepository<PrintedForm, UUID> {

    List<PrintedForm> findByLmPolicyIdInOrContractIdInOrderByCreatedAtAscIdAsc(Collection<UUID> lmPolicyIds,
            Collection<UUID> contractIds);

    Optional<PrintedForm> findByAttachmentAttachmentId(UUID attachmentId);

    @Query(value = """
            select document.* from contract_documents document
             where document.attachment_id = :attachmentId
               and document.deleted_at is null
               and document.doc_type in ('MEDICAL_CERTIFICATE', 'PD_CONSENT')
            """, nativeQuery = true)
    Optional<PrintedForm> findActiveAttachment(@Param("attachmentId") UUID attachmentId);

    @Modifying(clearAutomatically = true)
    @Query("""
            update PrintedForm document
               set document.attachment.deletedAt = :deletedAt, document.attachment.deletedRequestId = :requestId
             where document.attachment.attachmentId = :attachmentId and document.attachment.deletedAt is null
            """)
    int markAttachmentDeleted(@Param("attachmentId") UUID attachmentId, @Param("deletedAt") Instant deletedAt,
            @Param("requestId") String requestId);

    List<PrintedForm> findByAttachmentIndividualRequestIdAndAttachmentParticipantIdAndDocTypeAndAttachmentDeletedAtIsNullOrderByCreatedAtAscIdAsc(
            UUID individualRequestId, UUID participantId, PrintedFormType docType);
}
