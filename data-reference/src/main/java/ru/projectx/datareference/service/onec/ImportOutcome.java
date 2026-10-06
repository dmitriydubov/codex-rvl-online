package ru.projectx.datareference.service.onec;

import java.util.Objects;
import ru.projectx.datareference.mapper.ImportResult;

/**
 * Чем закончился разбор одного сообщения 1С: применено, пропущено, ждёт родителя или отклонено. Исход нужен
 * планировщику разбора для журнала; правда о каждом сообщении лежит в статусе его строки сырья.
 *
 * <p>Отклонить сообщение может только обработчик потока: передача прайса, не сошедшаяся со своими счётчиками,
 * отклоняет все свои части сразу. Маппер отклонённым сообщение не возвращает: нарушение контракта у него —
 * исключение.
 */
public sealed interface ImportOutcome {

    /** Как исход звучит в журнале после слов «сообщение такое-то потока такого-то». */
    String summary();

    /** Нужно ли внимание дежурного: такой исход журнал пишет предупреждением, а не сведением. */
    default boolean needsAttention() {
        return false;
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
