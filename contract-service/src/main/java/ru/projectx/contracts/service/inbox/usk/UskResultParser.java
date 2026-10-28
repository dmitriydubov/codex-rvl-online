package ru.projectx.contracts.service.inbox.usk;

import java.util.List;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.message.usk.IssuedDocument;
import ru.projectx.contracts.message.usk.UskResult;
import ru.projectx.contracts.service.inbox.parse.JsonFields;
import ru.projectx.contracts.service.inbox.parse.MessageBodies;
import ru.projectx.contracts.service.inbox.parse.PayloadParser;

public class UskResultParser implements PayloadParser<UskResult> {

    private static final String ERROR = "ERROR";
    private static final String ID = "id";
    private static final String STATUS = "status";
    private static final String DOCUMENTS = "documents";

    private final MessageBodies bodies;
    private final UskResultLayout layout;

    public UskResultParser(MessageBodies bodies, UskResultLayout layout) {
        this.bodies = bodies;
        this.layout = layout;
    }

    @Override
    public UskResult parse(String payload) {
        JsonFields data = bodies.data(payload);
        String status = data.text(STATUS);
        if (layout.issuedStatus().equals(status)) {
            return issued(data);
        }
        if (ERROR.equals(status)) {
            return new UskResult.Failed(data.uuid(ID), data.text("error_code"), data.text("error_message"));
        }
        throw ContractViolationException.invalidValue(data.pathOf(STATUS));
    }

    private UskResult.Issued issued(JsonFields data) {
        JsonFields policy = data.requiredObject("policy");
        return new UskResult.Issued(data.uuid(ID), data.uuid(layout.uskIdField()),
                layout.uskNumber().textOf(data, layout.uskNumberField()), policy.uuid("external_id"),
                policy.text("number"), documents(data));
    }

    private List<IssuedDocument> documents(JsonFields data) {
        if (layout.documents().arrayMissingIn(data, DOCUMENTS)) {
            return List.of();
        }
        return IssuedDocuments.of(data.requiredArray(DOCUMENTS));
    }
}
