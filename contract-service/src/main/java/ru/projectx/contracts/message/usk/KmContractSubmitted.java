package ru.projectx.contracts.message.usk;

import ru.projectx.contracts.message.MessageMeta;

public record KmContractSubmitted(MessageMeta meta, KmContractSubmission data) {
}
