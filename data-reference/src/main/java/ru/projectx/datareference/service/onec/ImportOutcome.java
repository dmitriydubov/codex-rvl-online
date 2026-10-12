package ru.projectx.datareference.service.onec;

import java.util.Objects;
import ru.projectx.datareference.mapper.ImportResult;

public sealed interface ImportOutcome {

    String summary();

    default boolean needsAttention() {
        return false;
    }

    default boolean mayChangeCatalog() {
        return true;
    }

    record Applied() implements ImportOutcome {

        @Override
        public String summary() {
            return "применено";
        }
    }

    record Skipped(String reason) implements ImportOutcome {

        public Skipped {
            Objects.requireNonNull(reason, "reason");
        }

        @Override
        public String summary() {
            return "пропущено: " + reason;
        }
    }

    record AwaitingParent(String reason) implements ImportOutcome {

        public AwaitingParent {
            Objects.requireNonNull(reason, "reason");
        }

        @Override
        public String summary() {
            return reason;
        }

        @Override
        public boolean mayChangeCatalog() {
            return false;
        }
    }

    record Rejected(String reason) implements ImportOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }

        @Override
        public String summary() {
            return "отклонено: " + reason;
        }

        @Override
        public boolean needsAttention() {
            return true;
        }
    }

    static ImportOutcome of(ImportResult result) {
        return switch (result) {
            case ImportResult.Applied ignored -> applied();
            case ImportResult.Skipped skipped -> skipped(skipped.reason());
            case ImportResult.AwaitingParent awaiting -> awaitingParent(awaiting.parent().waitingReason());
        };
    }

    static ImportOutcome applied() {
        return new Applied();
    }

    static ImportOutcome skipped(String reason) {
        return new Skipped(reason);
    }

    static ImportOutcome awaitingParent(String reason) {
        return new AwaitingParent(reason);
    }

    static ImportOutcome rejected(String reason) {
        return new Rejected(reason);
    }
}
