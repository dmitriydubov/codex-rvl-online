package ru.projectx.datareference.mapper;

import java.util.Objects;
import ru.projectx.datareference.entity.AwaitedParent;

/**
 * Чем закончилось применение сообщения к справочнику: запись обновлена, сообщение не наше или сообщение ждёт
 * записи, на которую ссылается.
 *
 * <p>«Не наше» — штатный и частый исход, а не ошибка: один поток 1С несёт данные всех контрагентов 1С:УСК, а нам
 * нужны только санатории с видом деятельности РВЛ. Поэтому у пропуска обязательна причина: без неё «данных нет» и
 * «фильтр задан неверно» выглядят в базе одинаково. Нарушение контракта — не исход, а исключение.
 *
 * <p>Запечатанный тип, а не флаг со строкой: у каждого исхода свои данные, и исчерпывающий {@code switch} не даст
 * забыть ни один исход.
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
     * Сообщение ссылается на запись справочника, которой ещё нет и о которой сервис ничего не знает. Применить его
     * можно будет, когда запись придёт.
     */
    record AwaitingParent(AwaitedParent parent) implements ImportResult {

        public AwaitingParent {
            Objects.requireNonNull(parent, "parent");
        }
    }

    static ImportResult applied() {
        return new Applied();
    }

    static ImportResult skipped(String reason) {
        return new Skipped(reason);
    }

    static ImportResult awaitingParent(AwaitedParent parent) {
        return new AwaitingParent(parent);
    }
}
