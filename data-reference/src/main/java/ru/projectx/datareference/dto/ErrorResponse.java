package ru.projectx.datareference.dto;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Единый формат ошибки REST — тот же, что задан для сервиса Договоров, чтобы у контура
 * был один: {@code error.code} машиночитаем, {@code error.message} — для журнала,
 * а не для показа пользователю, {@code error.details[]} — подробности,
 * {@code error.request_id} — по нему ошибку находят в журнале.
 *
 * <p>{@code details} — всегда массив объектов, по решению от 23.09: поле с плавающим
 * типом ломает генерацию клиентов. Что лежит в элементах, зависит от кода ошибки —
 * нарушение по полю запроса, конфликтующая запись, непокрытый период, — и каждый вид
 * записан своим типом {@link Detail}, чтобы состав элемента был виден в коде,
 * а не собирался из строк.
 */
public record ErrorResponse(Body error) {

    public static ErrorResponse of(String code, String message, @Nullable String requestId) {
        return new ErrorResponse(new Body(code, message, List.of(), requestId));
    }

    public static ErrorResponse of(
            String code, String message, List<? extends Detail> details, @Nullable String requestId) {

        return new ErrorResponse(new Body(code, message, List.copyOf(details), requestId));
    }

    public record Body(String code, String message, List<? extends Detail> details, @Nullable String requestId) {
    }

    /** Элемент {@code details}: у каждого кода ошибки свой состав. */
    public sealed interface Detail permits Violation, PolicyCandidate, LpuContractCandidate {
    }

    /** {@code VALIDATION_ERROR}: какое поле запроса и чем не устроило. */
    public record Violation(String field, String reason) implements Detail {
    }

    /** {@code AMBIGUOUS_POLICY}: один из полисов, между которыми не удалось выбрать. */
    public record PolicyCandidate(UUID policyId) implements Detail {
    }

    /** {@code AMBIGUOUS_PRICE_CONTRACT}: договоры ЛПУ, претендующие на роль прайсового. */
    public record LpuContractCandidate(UUID lpuContractId) implements Detail {
    }
}
