package ru.projectx.datareference.mapper;

import java.util.Objects;

/**
 * Чем закончился разбор одного сообщения: запись обновлена, сообщение не наше или сообщение отклонено.
 *
 * <p>«Не наше» — штатный и частый исход, а не ошибка: один поток 1С несёт данные всех контрагентов 1С:УСК, а нам
 * нужны только санатории с видом деятельности РВЛ. Поэтому у пропуска и отказа обязательна причина: без неё «данных
 * нет» и «фильтр задан неверно» выглядят в базе одинаково.
 *
 * <p>Запечатанный тип, а не флаг со строкой: причина есть только у пропуска и отказа, и исчерпывающий {@code switch}
 * не даст забыть ни один исход.
 */
public sealed interface ImportResult {

    /** Запись создана или обновлена. */
    record Applied() implements ImportResult {
    }

    /**
     * Целевой справочник потока сообщением не пополнился — с объяснением, почему.
     *
     * <p>Не обязательно означает, что не записано ничего: страхователь ложится в свою таблицу, санаторием при этом
     * не становясь. Отвечает исход на один вопрос — появилось ли то, за чем в поток шли.
     */
    record Skipped(String reason) implements ImportResult {

        public Skipped {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Сообщение отклонено окончательно — с причиной. Как записать отказ, решает обработчик потока: передача прайса,
     * не сошедшаяся со счётчиками, отклоняет все свои части в той же транзакции, а одиночное сообщение уходит общим
     * путём окончательного отказа после отката разбора.
     */
    record Rejected(String reason) implements ImportResult {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    static ImportResult applied() {
        return new Applied();
    }

    static ImportResult skipped(String reason) {
        return new Skipped(reason);
    }

    static ImportResult rejected(String reason) {
        return new Rejected(reason);
    }
}
