package ru.projectx.datareference.service;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.config.ReferenceApiProperties;
import ru.projectx.datareference.dto.Mkb10Code;
import ru.projectx.datareference.dto.Mkb10Response;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.repository.Mkb10Repository;
import ru.projectx.datareference.repository.Mkb10Row;

/**
 * Справочник МКБ-10 для сервиса Заявок в двух режимах: подсказка по строке и раскрытие перечня кодов разом.
 *
 * <p>Строка, которая начинается с буквы и цифры, — это код ({@code I25}, {@code Q06.0}). Он ищется по
 * началу кода без учёта регистра. Любая другая строка ищется как часть наименования. Архивные коды в
 * подсказку не попадают, а в раскрытии перечня отдаются с признаком архива: старые заявки ссылаются на них,
 * и показать их надо. Фильтра по ЛПУ (лечебно-профилактическому учреждению) нет: связь кодов с ЛПУ
 * не приходит ни одним потоком из 1С.
 *
 * <p>Строка поиска и перечень кодов ограничены сверху по объёму (см. {@link SearchInputLimits}); эти
 * пределы проверяются первыми, до обрезки пробелов и удаления повторов.
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
        SearchInputLimits.requireQueryNotLongerThan(rawQuery, api.mkb10().maxQueryLength());
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
        SearchInputLimits.requireCodesNotMoreThan(rawCodes, api.mkb10().maxCodes());
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
                .map(row -> new Mkb10Code(row.id(), row.externalId(), row.code(), row.name(),
                        row.hierarchyLevel(), row.parentCode(), row.isArchive()))
                .toList());
    }
}
