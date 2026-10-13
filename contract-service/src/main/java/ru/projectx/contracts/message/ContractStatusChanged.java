package ru.projectx.contracts.message;

public record ContractStatusChanged(MessageMeta meta, RequestIssuanceStatus data) {
}
