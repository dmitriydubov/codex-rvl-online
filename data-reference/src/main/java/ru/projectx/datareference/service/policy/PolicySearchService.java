package ru.projectx.datareference.service.policy;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.config.ReferenceApiProperties;
import ru.projectx.datareference.dto.PolicySearchResponse;
import ru.projectx.datareference.service.rest.ResultLimits;
import ru.projectx.datareference.service.rest.SearchInputLimits;

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

    private static String inSearchCase(String words) {
        return words.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }
}
