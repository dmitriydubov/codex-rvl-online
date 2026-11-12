package ru.projectx.contracts.service.inbox.usk;

import java.util.List;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.message.usk.IssuedDocument;
import ru.projectx.contracts.message.usk.UskResult;
import ru.projectx.contracts.service.inbox.parse.JsonFields;
import ru.projectx.contracts.service.inbox.parse.MessageBodies;
import ru.projectx.contracts.service.inbox.parse.PayloadParser;
import ru.projectx.contracts.service.usk.UskResultLayout;

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
        String status = data.field(STATUS).text();
        if (layout.issuedStatus().equals(status)) {
            return issued(data);
        }
        if (ERROR.equals(status)) {
            return new UskResult.Failed(data.field(ID).uuid(), data.field("error_code").text(),
                    data.field("error_message").text());
        }
        throw ContractViolationException.invalidValue(data.field(STATUS).path());
    }

    private UskResult.Issued issued(JsonFields data) {
        JsonFields policy = data.requiredObject("policy");
        return new UskResult.Issued(data.field(ID).uuid(), data.field(layout.uskIdField()).uuid(),
                layout.uskNumber().textOf(data, layout.uskNumberField()), policy.field("external_id").uuid(),
                policy.field("number").text(), documents(data));
    }

    private List<IssuedDocument> documents(JsonFields data) {
        if (layout.documents().arrayMissingIn(data, DOCUMENTS)) {
            return List.of();
        }
        return IssuedDocuments.of(data.requiredArray(DOCUMENTS));
    }
}
