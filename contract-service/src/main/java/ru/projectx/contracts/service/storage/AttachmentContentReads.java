package ru.projectx.contracts.service.storage;

import java.util.UUID;

public interface AttachmentContentReads {

    AttachmentContent open(UUID attachmentId);
}
