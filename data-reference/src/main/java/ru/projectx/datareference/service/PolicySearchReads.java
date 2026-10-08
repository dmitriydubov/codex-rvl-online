package ru.projectx.datareference.service;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.dto.PolicySearchResponse;

/** Поиск полисов для подсказки «найти сотрудника» в контроллере полисов. */
public interface PolicySearchReads {

    /** Ищет действующие полисы договора по части ФИО или номера; без {@code limit} берётся умолчание. */
    PolicySearchResponse search(UUID contractExternalId, String query, @Nullable Integer limit);
}
