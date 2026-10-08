package ru.projectx.datareference.service.onec;

import java.util.Objects;
import ru.projectx.datareference.mapper.ImportResult;

/**
 * Чем закончился разбор одного сообщения 1С; исход нужен планировщику для журнала, а правда лежит в статусе сырья.
 * Отклонённым исход бывает только у обработчика потока, например у передачи прайса с несошедшимися счётчиками:
 * маппер нарушение контракта выражает исключением.
 */
public sealed interface ImportOutcome {

    /** Как исход звучит в журнале после слов «сообщение такое-то потока такого-то». */
    String summary();

    /** Нужно ли внимание дежурного: такой исход журнал пишет предупреждением, а не сведением. */
    default boolean needsAttention() {
        return false;
    }

    /** Мог ли разбор изменить справочник: от этого зависит, сбрасывать ли кэши ответов потока. */
    default boolean mayChangeCatalog() {
        return true;
    }

    /** Запись справочника создана или обновлена. */
    record Applied() implements ImportOutcome {

        @Override
        public String summary() {
            return "применено";
        }
    }

    /** Справочник потока сообщением не пополнился — с причиной. */
    record Skipped(String reason) implements ImportOutcome {

        public Skipped {
            Objects.requireNonNull(reason, "reason");
        }

        @Override
        public String summary() {
            return "пропущено: " + reason;
        }
    }

    /** Сообщение ждёт записи, на которую ссылается, — с причиной, которая её называет. */
    record AwaitingParent(String reason) implements ImportOutcome {

        public AwaitingParent {
            Objects.requireNonNull(reason, "reason");
        }

        @Override
        public String summary() {
            return reason;
        }

        /** Сообщение ждёт родителя и в справочник ничего не записало. */
        @Override
        public boolean mayChangeCatalog() {
            return false;
        }
    }

    /** Сообщение отклонено окончательно — с причиной. */
    record Rejected(String reason) implements ImportOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }

        @Override
        public String summary() {
            return "отклонено: " + reason;
        }

        @Override
        public boolean needsAttention() {
            return true;
        }
    }

    /** Исход разбора по исходу применения сообщения к справочнику. */
    static ImportOutcome of(ImportResult result) {
        return switch (result) {
            case ImportResult.Applied ignored -> applied();
            case ImportResult.Skipped skipped -> skipped(skipped.reason());
            case ImportResult.AwaitingParent awaiting -> awaitingParent(awaiting.parent().waitingReason());
        };
    }

    static ImportOutcome applied() {
        return new Applied();
    }

    static ImportOutcome skipped(String reason) {
        return new Skipped(reason);
    }

    static ImportOutcome awaitingParent(String reason) {
        return new AwaitingParent(reason);
    }

    static ImportOutcome rejected(String reason) {
        return new Rejected(reason);
    }
}
