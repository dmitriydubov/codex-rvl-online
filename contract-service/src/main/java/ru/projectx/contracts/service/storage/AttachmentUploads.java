package ru.projectx.contracts.service.storage;

import ru.projectx.contracts.dto.AttachmentReceipt;

public interface AttachmentUploads {

    AttachmentReceipt receive(AttachmentUpload upload);
}
