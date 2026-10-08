package ru.projectx.datareference.service;

import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.dto.Mkb10Code;
import ru.projectx.datareference.dto.Mkb10Response;
import ru.projectx.datareference.repository.Mkb10Repository;
import ru.projectx.datareference.repository.Mkb10Row;

/**
 * Читает МКБ-10 из базы по входу, который уже проверил {@link Mkb10QueryService}. Транзакция берёт соединение
 * при открытии, поэтому проверка идёт до неё: иначе при недоступной базе неверный запрос получил бы 503
 * и вызывающий повторял бы его без конца.
 */
@Service
class Mkb10Lookup {

    private final Mkb10Repository mkb10;

    Mkb10Lookup(Mkb10Repository mkb10) {
        this.mkb10 = mkb10;
    }

    @RestReadTransaction
    public Mkb10Response activeByCodePrefix(String code, Limit limit) {
        return response(mkb10.findActiveByCodePrefix(LikePatterns.prefix(code.toUpperCase(Locale.ROOT)), limit));
    }

    @RestReadTransaction
    public Mkb10Response activeByName(String namePart, Limit limit) {
        return response(mkb10.findActiveByNamePattern(LikePatterns.substring(namePart.toLowerCase(Locale.ROOT)), limit));
    }

    /** Архивные коды тоже отдаются: на них ссылаются старые заявки. */
    @RestReadTransaction
    public Mkb10Response byCodes(List<String> codes) {
        return response(mkb10.findByCodes(codes));
    }

    private static Mkb10Response response(List<Mkb10Row> rows) {
        return new Mkb10Response(rows.stream()
                .map(row -> new Mkb10Code(row.id(), row.externalId(), row.code(), row.name(),
                        row.hierarchyLevel(), row.parentCode(), row.isArchive()))
                .toList());
    }
}
