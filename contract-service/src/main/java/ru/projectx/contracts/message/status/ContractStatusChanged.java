package ru.projectx.contracts.message.status;

import ru.projectx.contracts.message.MessageMeta;

public record ContractStatusChanged(MessageMeta meta, RequestIssuanceStatus data) {
}
