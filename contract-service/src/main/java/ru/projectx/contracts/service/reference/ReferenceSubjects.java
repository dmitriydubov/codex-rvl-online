package ru.projectx.contracts.service.reference;

import java.util.UUID;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.exception.ReferenceArchivedException;
import ru.projectx.contracts.exception.ReferenceKind;
import ru.projectx.contracts.exception.ReferenceNotFoundException;
import ru.projectx.contracts.exception.ReferenceRejectedException;

public final class ReferenceSubjects {

    private static final String ARCHIVED_SUFFIX = "_ARCHIVED";

    private ReferenceSubjects() {
    }

    public static IssueError archived(ReferenceKind kind, UUID id) {
        return new IssueError(kind.name() + ARCHIVED_SUFFIX, nominative(kind) + " " + id + " в архиве справочного сервиса");
    }

    public static IssueError notFound(ReferenceKind kind, UUID id) {
        return new IssueError(kind.notFoundCode(), genitive(kind) + " " + id + " нет у справочного сервиса");
    }

    public static IssueError rejected(ReferenceKind kind, UUID id, String code) {
        return new IssueError(code, "справочный сервис отклонил запрос: " + genitive(kind) + " " + id);
    }

    public static IssueError refusal(ReferenceKind kind, UUID id, RuntimeException refusal) {
        if (refusal instanceof ReferenceRejectedException rejected) {
            return rejected(kind, id, rejected.code());
        }
        return finalRefusal(kind, id, refusal);
    }

    private static IssueError finalRefusal(ReferenceKind kind, UUID id, RuntimeException refusal) {
        if (refusal instanceof ReferenceArchivedException) {
            return archived(kind, id);
        }
        if (refusal instanceof ReferenceNotFoundException) {
            return notFound(kind, id);
        }
        throw refusal;
    }

    static String nominative(ReferenceKind kind) {
        return switch (kind) {
            case PROGRAM -> "программа страхования";
            case INSURANCE_CONTRACT -> "договор страхования";
            case POLICY -> "полис страхователя";
        };
    }

    static String genitive(ReferenceKind kind) {
        return switch (kind) {
            case PROGRAM -> "программы страхования";
            case INSURANCE_CONTRACT -> "договора страхования";
            case POLICY -> "полиса страхователя";
        };
    }
}
