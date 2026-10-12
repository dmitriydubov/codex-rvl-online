package ru.projectx.datareference.entity;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ParentWait(AwaitedParent parent, Instant since, @Nullable UUID record) {
}
