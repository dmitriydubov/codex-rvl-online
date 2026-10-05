package ru.projectx.datareference.service;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.config.ReferenceApiProperties;
import ru.projectx.datareference.dto.Mkb10Response;
import ru.projectx.datareference.exception.InvalidRequestException;

/**
 * Справочник МКБ-10 для сервиса Заявок в двух режимах: подсказка по строке и раскрытие перечня кодов разом.
 *
 * <p>Строка, которая начинается с буквы и цифры, — это код ({@code I25}, {@code Q06.0}). Он ищется по
 * началу кода без учёта регистра. Любая другая строка ищется как часть наименования. Архивные коды в
 * подсказку не попадают, а в раскрытии перечня отдаются с признаком архива: старые заявки ссылаются на них,
 * и показать их надо. Фильтра по ЛПУ (лечебно-профилактическому учреждению) нет: связь кодов с ЛПУ
 * не приходит ни одним потоком из 1С.
 *
 * <p>Здесь проверяется вход, а читает базу {@link Mkb10Lookup}. Сам этот класс транзакцию не открывает
 * намеренно: неверный параметр отклоняется ошибкой вызывающего, даже когда база недоступна. Верхние пределы
 * строки и перечня проверяются первыми, до обрезки пробелов и удаления повторов (см. {@link SearchInputLimits}).
 */
@Service
public class Mkb10QueryService implements Mkb10Reads {

    private static final Pattern CODE_START = Pattern.compile("^[A-Za-z][0-9]");
    private static final String CODES = "codes";
    private static final String CODE_SEPARATOR = ",";

    private final Mkb10Lookup lookup;
    private final ReferenceApiProperties api;

    public Mkb10QueryService(Mkb10Lookup lookup, ReferenceApiProperties api) {
        this.lookup = lookup;
        this.api = api;
    }

    @Override
    public Mkb10Response search(String rawQuery, @Nullable Integer requestedLimit) {
        SearchInputLimits.requireQueryNotLongerThan(rawQuery, api.mkb10().maxQueryLength());
        Limit limit = Limit.of(ResultLimits.resolve(requestedLimit, api.mkb10().defaultLimit(), api.mkb10().maxLimit()));
        String query = rawQuery.trim();
        SearchInputLimits.requireQueryNotShorterThan(query, api.mkb10().minQueryLength());
        return CODE_START.matcher(query).lookingAt()
                ? lookup.activeByCodePrefix(query, limit)
                : lookup.activeByName(query, limit);
    }

    @Override
    public Mkb10Response byCodes(List<String> rawCodes) {
        SearchInputLimits.requireCodesNotMoreThan(rawCodes, api.mkb10().maxCodes());
        requireOneCodePerValue(rawCodes);
        List<String> codes = rawCodes.stream()
                .map(String::trim)
                .filter(Predicate.not(String::isEmpty))
                .map(code -> code.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            throw new InvalidRequestException(CODES, "хотя бы один код");
        }
        return lookup.byCodes(codes);
    }

    /**
     * Перечень через запятую приходит одним параметром и делится на коды ещё до сервиса. Если параметр повторён,
     * значения не делятся, и запятая остаётся внутри кода. Такого кода в справочнике нет, а молча искать его
     * значит вернуть неполный ответ, поэтому это ошибка вызывающего.
     */
    private static void requireOneCodePerValue(List<String> codes) {
        if (codes.stream().anyMatch(code -> code.contains(CODE_SEPARATOR))) {
            throw new InvalidRequestException(CODES, "коды через запятую передаются одним параметром codes");
        }
    }
}
