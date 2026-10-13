package ru.projectx.contracts.entity;

public interface IssuanceStatus {

    IssuanceOutcome outcome();

    default boolean finished() {
        return outcome() != IssuanceOutcome.PENDING;
    }

    default boolean issued() {
        return outcome() == IssuanceOutcome.ISSUED;
    }

    default boolean error() {
        return outcome() == IssuanceOutcome.ERROR;
    }

    default boolean stopped() {
        return outcome() == IssuanceOutcome.ERROR || outcome() == IssuanceOutcome.CANCELLED;
    }
}
