package ru.projectx.contracts.controller;

import java.util.UUID;
import java.util.regex.Pattern;

final class AttachmentIds {

    private static final Pattern GUID = Pattern.compile("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}",
            Pattern.CASE_INSENSITIVE);

    private AttachmentIds() {
    }

    static UUID parse(String field, String value) {
        if (!GUID.matcher(value).matches()) {
            throw new InvalidAttachmentFieldException(field);
        }
        return UUID.fromString(value);
    }
}
