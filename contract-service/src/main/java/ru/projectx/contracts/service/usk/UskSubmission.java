package ru.projectx.contracts.service.usk;

import java.util.List;
import ru.projectx.contracts.entity.OutboxDraft;
import ru.projectx.contracts.service.outbox.OutboundAttachment;

public record UskSubmission(OutboxDraft draft, List<OutboundAttachment> attachments) {
}
