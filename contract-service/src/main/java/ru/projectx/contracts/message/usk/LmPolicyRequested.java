package ru.projectx.contracts.message.usk;

import ru.projectx.contracts.message.MessageMeta;

public record LmPolicyRequested(MessageMeta meta, LmPolicyRequest data) {
}
