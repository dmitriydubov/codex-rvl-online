package ru.projectx.datareference.mapper;

import java.util.Objects;
import ru.projectx.datareference.entity.AwaitedParent;

public sealed interface ImportResult {

    record Applied() implements ImportResult {
    }

    record Skipped(String reason) implements ImportResult {

        public Skipped {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record AwaitingParent(AwaitedParent parent) implements ImportResult {

        public AwaitingParent {
            Objects.requireNonNull(parent, "parent");
        }
    }

    static ImportResult applied() {
        return new Applied();
    }

    static ImportResult skipped(String reason) {
        return new Skipped(reason);
    }

    static ImportResult awaitingParent(AwaitedParent parent) {
        return new AwaitingParent(parent);
    }
}
