package ru.projectx.contracts.exception;

/**
 * Справочник отклонил запрос своим кодом ошибки со статусом 4xx, кроме задокументированного «не найдено». Такой ответ на
 * тот же запрос повторится, поэтому это постоянная ошибка выпуска по участнику с кодом справочника, а не повод перечитать.
 */
public class ReferenceRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;

    public ReferenceRejectedException(int status, String code) {
        super("справочник отклонил запрос: " + status + " " + code);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    /** Код ошибки, как его назвал справочник; он же уходит сервису Заявок в состоянии выпуска по участнику. */
    public String code() {
        return code;
    }
}
