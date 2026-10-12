package ru.projectx.datareference.entity;

public enum InboundStatus {

    NEW,

    BUFFERED,

    PROCESSED,

    SKIPPED,

    AWAITING_PARENT,

    RETRY,

    FAILED
}
