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
import ru.projectx.datareference.repository.Mkb10Repository;
import ru.projectx.datareference.repository.Mkb10Row;

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
public class Mkb10QueryService implements Mkb10Reads {

    private static final Pattern CODE_START = Pattern.compile("^[A-Za-z][0-9]");

    private final Mkb10Repository mkb10;
    private final ReferenceApiProperties api;

    public Mkb10QueryService(Mkb10Repository mkb10, ReferenceApiProperties api) {
        this.mkb10 = mkb10;
        this.api = api;
    }

    @Override
    @RestReadTransaction
    public Mkb10Response search(String rawQuery, @Nullable Integer requestedLimit) {
        Limit limit = Limit.of(ResultLimits.resolve(requestedLimit, api.mkb10().defaultLimit(), api.mkb10().maxLimit()));
        String query = rawQuery.trim();
        if (query.length() < api.mkb10().minQueryLength()) {
            throw new InvalidRequestException("query", "не короче " + api.mkb10().minQueryLength() + " символов");
        }
        return response(CODE_START.matcher(query).lookingAt() ? byCodePrefix(query, limit) : byName(query, limit));
    }

    @Override
    @RestReadTransaction
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
        return response(mkb10.findByCodes(codes));
    }

    private List<Mkb10Row> byCodePrefix(String query, Limit limit) {
        return mkb10.findActiveByCodePrefix(LikePatterns.prefix(query.toUpperCase(Locale.ROOT)), limit);
    }

    private List<Mkb10Row> byName(String query, Limit limit) {
        return mkb10.findActiveByNamePattern(LikePatterns.substring(query.toLowerCase(Locale.ROOT)), limit);
    }

    private static Mkb10Response response(List<Mkb10Row> rows) {
        return new Mkb10Response(rows.stream()
                .map(row -> new Mkb10Response.Item(row.id(), row.externalId(), row.code(), row.name(),
                        row.hierarchyLevel(), row.parentCode(), row.isArchive()))
                .toList());
    }
}
