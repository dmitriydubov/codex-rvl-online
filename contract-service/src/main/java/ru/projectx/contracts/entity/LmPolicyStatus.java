package ru.projectx.contracts.entity;

import java.util.Set;

public enum LmPolicyStatus implements IssuanceStatus {
    NEW, SUBMITTED, ISSUED, ERROR, CANCELLED;

    private static final Set<LmPolicyStatus> FINISHED = Set.of(ISSUED, ERROR, CANCELLED);

    @Override
    public boolean finished() {
        return FINISHED.contains(this);
    }
}
