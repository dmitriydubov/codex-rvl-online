package ru.projectx.contracts.service.outbox;

import java.time.Duration;

public sealed interface PublishOutcome {

    String reason();

    default boolean endsPass() {
        return false;
    }

    sealed interface Attempted extends PublishOutcome {
    }

    record Delivered() implements Attempted {

        @Override
        public String reason() {
            return "доставлено";
        }
    }

    record Refused(String reason) implements Attempted {
    }

    record Rejected(String reason) implements Attempted {
    }

    record Unconfirmed(Duration timeout) implements Attempted {

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

    record Interrupted() implements PublishOutcome {

        @Override
        public String reason() {
            return "ожидание подтверждения брокера прервано остановкой сервиса";
        }

        @Override
        public boolean endsPass() {
            return true;
        }
    }
}
