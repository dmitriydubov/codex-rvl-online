package ru.projectx.datareference.entity;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Чего ждёт сообщение 1С, пришедшее раньше своего родителя, и с какого момента.
 *
 * @param parent родитель, которого ждёт сообщение
 * @param since  когда сообщение начало ждать: от этого момента считается срок ожидания
 * @param record GUID записи справочника, которую меняет сообщение; пусто у передачи прайса, у которой порядок
 *               передач по услуге решается отдельно
 */
public record ParentWait(AwaitedParent parent, Instant since, @Nullable UUID record) {
}
