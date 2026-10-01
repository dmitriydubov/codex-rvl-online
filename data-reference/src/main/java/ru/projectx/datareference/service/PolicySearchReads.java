package ru.projectx.datareference.service;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.dto.PolicySearchResponse;

/** Подсказка R-04 «найти сотрудника» — то, что нужно контроллеру полисов от поиска. */
public interface PolicySearchReads {

    /** Действующие полисы договора по части ФИО или номера; {@code limit} не задан — умолчание. */
    PolicySearchResponse search(UUID contractExternalId, String query, @Nullable Integer limit);
}
