package ru.projectx.datareference.service;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.config.ReferenceApiProperties;
import ru.projectx.datareference.dto.PolicySearchResponse;

/**
 * Проверяет и нормализует строку поиска полисов по части ФИО или номера, без транзакции, чтобы неверный параметр
 * отклонялся и при недоступной базе. Строка поиска содержит фамилии, поэтому в журнал и текст ошибки не попадает.
 * Буква {@code ё} заменяется на {@code е}, и порядок слов не важен: «иван петр» найдёт и Петрова Ивана.
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
     * Вызывается после проверки длины: строчная форма бывает длиннее заглавной («İ» даёт два символа),
     * а длину считают по присланной строке.
     */
    private static String inSearchCase(String words) {
        return words.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }
}
