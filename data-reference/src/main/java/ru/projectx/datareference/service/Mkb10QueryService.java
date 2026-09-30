package ru.projectx.datareference.service;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.config.ReferenceApiProperties;
import ru.projectx.datareference.dto.Mkb10Response;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.repository.Mkb10Repository;

/**
 * МКБ-10 для R-07 в двух режимах: подсказка по строке и батч по перечню кодов.
 *
 * <p>Строка, которая начинается с буквы и цифры, — это код ({@code I25}, {@code Q06.0}),
 * ищется префиксом без учёта регистра; любая другая — подстрока наименования. Архивные
 * коды в подсказку не попадают, а батчу отдаются с признаком: старые заявки ссылаются
 * на них, и показать их надо. Фильтра по ЛПУ нет: {@code lpu_mkb} не наполняется
 * ни одним потоком ({@code OQ-25}).
 */
@Service
public class Mkb10QueryService {

    private static final Pattern CODE_START = Pattern.compile("^[A-Za-z][0-9]");

    private final Mkb10Repository mkb10;
    private final ReferenceApiProperties api;

    public Mkb10QueryService(Mkb10Repository mkb10, ReferenceApiProperties api) {
        this.mkb10 = mkb10;
        this.api = api;
    }

    @Transactional(readOnly = true)
    public Mkb10Response search(String rawQuery, @Nullable Integer requestedLimit) {
        Limit limit = Limit.of(limitOf(requestedLimit));
        String query = rawQuery.trim();
        if (query.length() < api.mkb10().minQueryLength()) {
            throw new InvalidRequestException("query", "не короче " + api.mkb10().minQueryLength() + " символов");
        }
        String escaped = PolicySearchService.escapeLike(query);
        return Mkb10Response.of(CODE_START.matcher(query).lookingAt()
                ? mkb10.findActiveByCodePrefix(escaped.toUpperCase(Locale.ROOT) + "%", limit)
                : mkb10.findActiveByNamePattern("%" + escaped.toLowerCase(Locale.ROOT) + "%", limit));
    }

    @Transactional(readOnly = true)
    public Mkb10Response byCodes(List<String> rawCodes) {
        List<String> codes = rawCodes.stream()
                .map(String::trim)
                .filter(Predicate.not(String::isEmpty))
                .map(code -> code.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            throw new InvalidRequestException("codes", "хотя бы один код");
        }
        return Mkb10Response.of(mkb10.findByCodes(codes));
    }

    private int limitOf(@Nullable Integer requested) {
        int limit = requested == null ? api.mkb10().defaultLimit() : requested;
        if (limit < 1 || limit > api.mkb10().maxLimit()) {
            throw new InvalidRequestException("limit", "от 1 до " + api.mkb10().maxLimit());
        }
        return limit;
    }
}
