package ru.projectx.datareference.repository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Где и сколько искать полисы для подсказки R-04: действующие на {@code today} полисы договора
 * с программой РВЛ (базовая логика A), не больше {@code limit} строк.
 */
public record PolicyScope(UUID contractExternalId, LocalDate today, int limit) {
}
