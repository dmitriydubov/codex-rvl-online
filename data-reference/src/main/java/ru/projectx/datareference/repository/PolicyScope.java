package ru.projectx.datareference.repository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Где и сколько искать полисы для подсказки: действующие на {@code today} полисы договора с программой
 * восстановительного лечения, не больше {@code limit} строк.
 */
public record PolicyScope(UUID contractExternalId, LocalDate today, int limit) {
}
