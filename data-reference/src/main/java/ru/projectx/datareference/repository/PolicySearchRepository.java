package ru.projectx.datareference.repository;

import java.util.List;
import ru.projectx.datareference.entity.Policy;

/**
 * Поиск полисов для подсказки одним запросом: отбор, ранжирование и предел выдачи делает база, чтобы ради десяти строк
 * не поднимать все полисы договора. Внутри ранга порядок по фамилии и имени; шаблоны {@code like} приходят
 * экранированными символом {@code !}.
 */
public interface PolicySearchRepository {

    /**
     * Номер содержит строку поиска ({@code numberSubstring}); ранг — точное совпадение с {@code number}, затем начало
     * номера ({@code numberPrefix}), затем остальные. {@code number} — строка поиска в нижнем регистре.
     */
    List<Policy> findRankedByNumber(PolicyScope scope, String number, String numberPrefix, String numberSubstring);

    /**
     * Каждый шаблон — префикс фамилии, имени или отчества; ранг — совпали фамилия и имя, затем
     * фамилия, затем остальные.
     */
    List<Policy> findRankedByNames(PolicyScope scope, List<String> namePrefixes);
}
