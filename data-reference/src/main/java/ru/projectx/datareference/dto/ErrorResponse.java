package ru.projectx.datareference.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Единый формат ошибки REST, общий с сервисом Договоров: машиночитаемый код, сообщение для журнала (не для
 * пользователя), подробности и идентификатор запроса для поиска в журнале. Подробности всегда массив объектов
 * своего типа на каждый код ошибки, потому что поле с плавающим типом ломает генерацию клиентов.
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
    public sealed interface Detail permits Violation, PolicyCandidate, LpuContractCandidate, PriceRowCandidate, UncoveredInterval {
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

    /**
     * {@code AMBIGUOUS_PRICE}: одна из строк прайса, которые действуют на одни и те же сутки. Строку называют её сроки
     * действия, включительно с обеих сторон; пустой {@code validTo} значит «бессрочно».
     */
    public record PriceRowCandidate(LocalDate validFrom, @Nullable LocalDate validTo) implements Detail {
    }

    /** {@code PRICE_NOT_COVERED}: сутки подряд без строки прайса, с {@code dateFrom} до {@code dateTo} не включительно. */
    public record UncoveredInterval(LocalDate dateFrom, LocalDate dateTo) implements Detail {
    }
}
