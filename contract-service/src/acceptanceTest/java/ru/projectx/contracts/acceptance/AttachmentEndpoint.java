package ru.projectx.contracts.acceptance;

enum AttachmentEndpoint {
    CERTIFICATE("/internal/v1/requests/%s/participants/%s/attachments"),
    CONSENT("/internal/v1/policies/%s/family-members/%s/consents");

    private final String path;

    AttachmentEndpoint(String path) {
        this.path = path;
    }

    String path(String owner, String person) {
        return path.formatted(owner, person);
    }

    static AttachmentEndpoint fromOperation(String operation) {
        return switch (operation) {
            case "СЭ-04" -> CERTIFICATE;
            case "СЭ-05" -> CONSENT;
            default -> throw new AssertionError("неизвестная операция загрузки " + operation);
        };
    }
}
