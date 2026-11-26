package ru.projectx.contracts.dto;

public sealed interface AttachmentReceipt permits AttachmentResponse, ConsentResponse {

    AttachmentDetails details();
}
