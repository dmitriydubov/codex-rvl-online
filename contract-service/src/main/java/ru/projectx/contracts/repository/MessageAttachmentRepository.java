package ru.projectx.contracts.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.projectx.contracts.entity.MessageAttachment;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, UUID> {

    List<MessageAttachment> findByOutboxMessageIdOrderByCreatedAtAscIdAsc(UUID outboxMessageId);
}
