package ru.projectx.contracts.service.inbox;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.PrintedFormType;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.message.IssuedDocument;
import ru.projectx.contracts.message.LmPolicyResult;
import ru.projectx.contracts.service.Enums;

@Component
public class LmPolicyIssuedParser implements PayloadParser<LmPolicyResult> {

    private static final String ISSUED = "ISSUED";
    private static final String ERROR = "ERROR";
    private static final String STATUS = "status";
    private static final String DOCUMENTS = "documents";

    private final MessageBodies bodies;

    public LmPolicyIssuedParser(MessageBodies bodies) {
        this.bodies = bodies;
    }

    @Override
    public LmPolicyResult parse(String payload) {
        JsonFields data = bodies.data(payload);
        return switch (data.text(STATUS)) {
            case ISSUED -> issued(data);
            case ERROR -> new LmPolicyResult.Failed(data.uuid("id"), data.text("error_code"), data.text("error_message"));
            default -> throw ContractViolationException.invalidValue(data.pathOf(STATUS));
        };
    }

    private static LmPolicyResult.Issued issued(JsonFields data) {
        JsonFields policy = data.requiredObject("policy");
        return new LmPolicyResult.Issued(data.uuid("id"), data.uuid("policy_usk_id"), data.textOrNull("policy_usk_number"),
                policy.uuid("external_id"), policy.text("number"), documents(data));
    }

    private static List<IssuedDocument> documents(JsonFields data) {
        if (!data.hasArray(DOCUMENTS)) {
            return List.of();
        }
        JsonFields array = data.requiredArray(DOCUMENTS);
        List<IssuedDocument> documents = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            documents.add(document(array.item(index)));
        }
        return List.copyOf(documents);
    }

    private static IssuedDocument document(JsonFields document) {
        String type = document.text("doc_type");
        Enums.named(PrintedFormType.class, type)
                .filter(PrintedFormType::issuedByUsk)
                .orElseThrow(() -> ContractViolationException.invalidValue(document.pathOf("doc_type")));
        return new IssuedDocument(type, document.text("file_url"), document.longOrNull("file_size"));
    }
}
