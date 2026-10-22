package ru.projectx.contracts.entity;

public enum PrintedFormType {
    CONTRACT(true), POLICY(true), APPENDIX(true), MEDICAL_CERTIFICATE(false);

    private final boolean issuedByUsk;

    PrintedFormType(boolean issuedByUsk) {
        this.issuedByUsk = issuedByUsk;
    }

    public boolean issuedByUsk() {
        return issuedByUsk;
    }
}
