package ru.projectx.contracts.service.usk;

import java.util.List;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.UskDocument;
import ru.projectx.contracts.message.IssuedDocument;

public final class UskDocuments {

    private UskDocuments() {
    }

    public static List<UskDocument> of(List<IssuedDocument> documents) {
        return documents.stream()
                .map(document -> new UskDocument(PrintedFormType.valueOf(document.docType()), document.fileUrl(),
                        document.fileSize()))
                .toList();
    }
}
