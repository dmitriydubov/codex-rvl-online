package ru.projectx.contracts.service.outbox;

import java.time.Duration;

public sealed interface PublishOutcome {

    String reason();

    default boolean endsPass() {
        return false;
    }

    record Delivered() implements PublishOutcome {

        @Override
        public String reason() {
            return "доставлено";
        }
    }

    record Refused(String reason) implements PublishOutcome {
    }

    record Rejected(String reason) implements PublishOutcome {
    }

    record Unconfirmed(Duration timeout) implements PublishOutcome {

        @Override
        public String reason() {
            return "подтверждение брокера не получено за " + timeout;
        }

        @Override
        public boolean endsPass() {
            return true;
        }
    }

    record NotSent(String reason) implements PublishOutcome {

        @Override
        public boolean endsPass() {
            return true;
        }
    }
}
