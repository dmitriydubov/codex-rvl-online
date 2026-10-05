package ru.projectx.datareference.service;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.config.ReferenceApiProperties;
import ru.projectx.datareference.dto.PolicySearchResponse;

/**
 * Поиск полисов договора по части ФИО или номера: подсказка для поля «найти сотрудника». Ищется среди
 * действующих полисов договора с программами восстановительного лечения; это условие стоит в запросе
 * репозитория.
 *
 * <p>Строка нормализуется: обрезаются пробелы по краям, подряд идущие пробелы схлопываются, буквы приводятся
 * к нижнему регистру, {@code ё} заменяется на {@code е}. Так «иван петр» находит и Петрова Ивана, и Иванова
 * Петра. Сначала идёт точный номер, затем совпавшие фамилия и имя, затем фамилия, затем остальное; внутри
 * группы — по фамилии и имени.
 *
 * <p>Здесь проверяются строка и пределы, а ищет в базе {@link PolicySearchLookup}. Сам этот класс транзакцию
 * не открывает намеренно: неверный параметр отклоняется ошибкой вызывающего, даже когда база недоступна. Длина
 * строки сверху проверяется первой, по строке как пришла (см. {@link SearchInputLimits}).
 *
 * <p>Строка поиска — это фамилии. Она не попадает ни в журнал, ни в текст ошибки: отказ называет правило,
 * а не значение.
 */
@Service
public class PolicySearchService implements PolicySearchReads {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final PolicySearchLookup lookup;
    private final ReferenceApiProperties api;

    public PolicySearchService(PolicySearchLookup lookup, ReferenceApiProperties api) {
        this.lookup = lookup;
        this.api = api;
    }

    @Override
    public PolicySearchResponse search(UUID contractExternalId, String rawQuery, @Nullable Integer requestedLimit) {
        SearchInputLimits.requireQueryNotLongerThan(rawQuery, api.search().maxQueryLength());
        int limit = ResultLimits.resolve(requestedLimit, api.search().defaultLimit(), api.search().maxLimit());
        String words = WHITESPACE.matcher(rawQuery.trim()).replaceAll(PolicySearchLookup.WORD_SEPARATOR);
        SearchInputLimits.requireQueryNotShorterThan(words, api.search().minQueryLength());
        return lookup.search(contractExternalId, inSearchCase(words), limit);
    }

    /**
     * Регистр меняется после проверки нижнего предела: у некоторых букв строчная форма длиннее заглавной
     * («İ» становится двумя символами), а длина считается по тому, что прислал вызывающий.
     */
    private static String inSearchCase(String words) {
        return words.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }
}
