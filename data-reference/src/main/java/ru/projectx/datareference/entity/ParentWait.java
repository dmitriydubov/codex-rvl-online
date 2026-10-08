package ru.projectx.datareference.entity;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Чего ждёт сообщение 1С, пришедшее раньше своего родителя, и с какого момента считается срок ожидания.
 * {@code record} — GUID записи, которую меняет сообщение; у передачи прайса он пуст, её порядок решается отдельно.
 */
public record ParentWait(AwaitedParent parent, Instant since, @Nullable UUID record) {
}
