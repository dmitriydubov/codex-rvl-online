package ru.projectx.datareference.service;

import java.util.List;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.dto.Mkb10Response;

/** Чтение МКБ-10 для R-07 — то, что нужно контроллеру справочника и ничего больше. */
public interface Mkb10Reads {

    /** Подсказка по коду или наименованию; {@code limit} не задан — умолчание из конфигурации. */
    Mkb10Response search(String query, @Nullable Integer limit);

    /** Раскрыть перечень кодов разом, вместе с архивными. */
    Mkb10Response byCodes(List<String> codes);
}
