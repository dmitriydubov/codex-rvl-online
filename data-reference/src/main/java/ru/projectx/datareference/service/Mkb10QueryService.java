package ru.projectx.datareference.service;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.config.ReferenceApiProperties;
import ru.projectx.datareference.config.ReferenceCaches;
import ru.projectx.datareference.dto.Mkb10Response;
import ru.projectx.datareference.exception.InvalidRequestException;

/**
 * МКБ-10 для сервиса Заявок: подсказка по коду или наименованию и раскрытие перечня кодов. Здесь только проверка
 * входа, без транзакции, чтобы неверный параметр отклонялся и при недоступной базе. Фильтра по учреждению нет:
 * связь кодов с учреждениями ни один поток 1С не присылает.
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
    @Cacheable(ReferenceCaches.MKB10_SEARCH)
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
    @Cacheable(ReferenceCaches.MKB10_CODES)
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
     * Запятая в коде значит, что перечень через запятую пришёл в одном из нескольких параметров {@code codes}:
     * делится только единственный параметр. Молча искать такой код значило бы вернуть неполный ответ.
     */
    private static void requireOneCodePerValue(List<String> codes) {
        if (codes.stream().anyMatch(code -> code.contains(CODE_SEPARATOR))) {
            throw new InvalidRequestException(CODES, "коды через запятую передаются одним параметром codes");
        }
    }
}
