package ru.projectx.datareference.service;

import java.util.List;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.dto.Mkb10Response;

/** Чтение справочника МКБ-10 для его контроллера. */
public interface Mkb10Reads {

    /** Без {@code limit} берётся предел выдачи из конфигурации. */
    Mkb10Response search(String query, @Nullable Integer limit);

    /** Архивные коды перечня тоже отдаются. */
    Mkb10Response byCodes(List<String> codes);
}
