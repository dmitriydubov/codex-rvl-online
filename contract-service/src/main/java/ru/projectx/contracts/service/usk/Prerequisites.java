package ru.projectx.contracts.service.usk;

public sealed interface Prerequisites {

    record Met() implements Prerequisites {
    }

    record Postponed(String reason) implements Prerequisites {
    }

    record Broken(String reason, RuntimeException failure) implements Prerequisites {
    }
}
