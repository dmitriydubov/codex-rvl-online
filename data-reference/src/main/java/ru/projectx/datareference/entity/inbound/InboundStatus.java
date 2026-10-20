package ru.projectx.datareference.entity.inbound;

public enum InboundStatus {

    NEW,

    BUFFERED,

    PROCESSED,

    SKIPPED,

    AWAITING_PARENT,

    RETRY,

    FAILED
}
