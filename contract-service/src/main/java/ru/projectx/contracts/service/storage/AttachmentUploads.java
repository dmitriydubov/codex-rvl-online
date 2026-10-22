package ru.projectx.contracts.service.storage;

import ru.projectx.contracts.dto.AttachmentResponse;

public interface AttachmentUploads {

    AttachmentResponse receive(CertificateUpload upload);
}
