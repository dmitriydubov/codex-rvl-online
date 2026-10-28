package ru.projectx.contracts.service.inbox.usk;

import java.util.ArrayList;
import java.util.List;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.message.usk.IssuedDocument;
import ru.projectx.contracts.service.Enums;
import ru.projectx.contracts.service.inbox.parse.JsonFields;

final class IssuedDocuments {

    private static final String DOC_TYPE = "doc_type";

    private IssuedDocuments() {
    }

    static List<IssuedDocument> of(JsonFields array) {
        List<IssuedDocument> documents = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            documents.add(document(array.item(index)));
        }
        return List.copyOf(documents);
    }

    private static IssuedDocument document(JsonFields document) {
        String type = document.text(DOC_TYPE);
        Enums.named(PrintedFormType.class, type)
                .filter(PrintedFormType::issuedByUsk)
                .orElseThrow(() -> ContractViolationException.invalidValue(document.pathOf(DOC_TYPE)));
        return new IssuedDocument(type, document.text("file_url"), document.longOrNull("file_size"));
    }
}
