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
 * Чтение справочника МКБ-10 из базы для подсказки и раскрытия перечня кодов. Каждый метод — своя транзакция
 * REST-чтения с коротким лимитом времени.
 *
 * <p>Сюда приходит уже проверенный вход: строка без пробелов по краям, предел выдачи и непустой перечень
 * кодов без повторов. Проверяет его {@link Mkb10QueryService} до вызова, потому что транзакция берёт соединение
 * с базой в момент открытия. Иначе при недоступной базе запрос с неверным параметром получил бы 503, и
 * вызывающий повторял бы запрос, который не пройдёт никогда.
 */
@Service
public class Mkb10Lookup {

    private final Mkb10Repository mkb10;

    public Mkb10Lookup(Mkb10Repository mkb10) {
        this.mkb10 = mkb10;
    }

    /** Действующие коды, которые начинаются с {@code code} без учёта регистра. */
    @RestReadTransaction
    public Mkb10Response activeByCodePrefix(String code, Limit limit) {
        return response(mkb10.findActiveByCodePrefix(LikePatterns.prefix(code.toUpperCase(Locale.ROOT)), limit));
    }

    /** Действующие коды, в наименовании которых есть {@code namePart} без учёта регистра. */
    @RestReadTransaction
    public Mkb10Response activeByName(String namePart, Limit limit) {
        return response(mkb10.findActiveByNamePattern(LikePatterns.substring(namePart.toLowerCase(Locale.ROOT)), limit));
    }

    /** Коды перечня вместе с архивными: старые заявки ссылаются и на них. */
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
