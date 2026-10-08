package ru.projectx.datareference.mapper;

import java.util.Objects;
import ru.projectx.datareference.entity.AwaitedParent;

/**
 * Чем закончилось применение сообщения к справочнику. Пропуск чужих данных штатен, ведь поток несёт всех контрагентов
 * 1С:УСК, поэтому у него обязательна причина: иначе «данных нет» и «фильтр неверен» выглядят одинаково. Нарушение
 * контракта не исход, а исключение.
 */
public sealed interface ImportResult {

    record Applied() implements ImportResult {
    }

    /**
     * Целевой справочник потока не пополнился. Это не значит, что не записано ничего: страхователь ложится в свою
     * таблицу, санаторием при этом не становясь.
     */
    record Skipped(String reason) implements ImportResult {

        public Skipped {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** Сообщение ссылается на запись, которой ещё нет и о которой сервис ничего не знает; оно ждёт её прихода. */
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
