package ru.projectx.contracts.entity;

public interface IssuanceStatus {

    String ISSUED = "ISSUED";
    String ERROR = "ERROR";

    String name();

    boolean finished();

    default boolean issued() {
        return ISSUED.equals(name());
    }

    default boolean error() {
        return ERROR.equals(name());
    }

    default boolean stopped() {
        return finished() && !issued();
    }
}
