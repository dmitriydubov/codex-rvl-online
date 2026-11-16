package ru.projectx.contracts.entity.issuance;

import java.util.UUID;

public enum IssuanceTransition {
    SUBMITTED {
        @Override
        public <S extends IssuanceStatus> S after(IssuanceFlow<S> flow, S current, UUID recordId) {
            return flow.move(current, flow.submittedFrom(), flow.submitted(), recordId);
        }
    },
    ERROR {
        @Override
        public <S extends IssuanceStatus> S after(IssuanceFlow<S> flow, S current, UUID recordId) {
            return flow.failed();
        }
    },
    CANCELLED {
        @Override
        public <S extends IssuanceStatus> S after(IssuanceFlow<S> flow, S current, UUID recordId) {
            return flow.cancelled();
        }
    };

    public abstract <S extends IssuanceStatus> S after(IssuanceFlow<S> flow, S current, UUID recordId);
}
