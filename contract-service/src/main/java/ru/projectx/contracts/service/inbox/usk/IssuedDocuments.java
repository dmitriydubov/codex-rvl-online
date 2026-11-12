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
        String type = document.field(DOC_TYPE).text();
        Enums.named(PrintedFormType.class, type)
                .filter(PrintedFormType::issuedByUsk)
                .orElseThrow(() -> ContractViolationException.invalidValue(document.field(DOC_TYPE).path()));
        return new IssuedDocument(type, document.field("file_url").text(),
                document.field("file_size").optional().longInteger());
    }
}
