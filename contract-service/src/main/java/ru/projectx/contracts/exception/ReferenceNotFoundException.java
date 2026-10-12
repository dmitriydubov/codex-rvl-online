package ru.projectx.contracts.exception;

/**
 * Справочник ответил, что записи с таким идентификатором нет. Для чтения по своему идентификатору это окончательный
 * ответ: выпуск по участнику завершается ошибкой с кодом справочника. Полис, который ищут по идентификатору 1С:УСК,
 * мог ещё не дойти до справочника, поэтому там этот ответ повторяется ограниченное число раз.
 */
public class ReferenceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ReferenceKind kind;

    public ReferenceNotFoundException(ReferenceKind kind) {
        super("запись справочника не найдена: " + kind.notFoundCode());
        this.kind = kind;
    }

    public ReferenceKind kind() {
        return kind;
    }

    /** Код ошибки, как его назвал справочник; он же уходит сервису Заявок в состоянии выпуска по участнику. */
    public String code() {
        return kind.notFoundCode();
    }
}
