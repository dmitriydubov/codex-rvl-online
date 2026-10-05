package ru.projectx.datareference.repository;

import java.util.List;
import ru.projectx.datareference.entity.Policy;

/**
 * Поиск полисов для подсказки одним запросом: отбор, ранжирование и предел выдачи делает база.
 * Так ради десяти строк ответа не приходится поднимать из базы все подходящие полисы договора.
 *
 * <p>Порядок — ранг, затем фамилия и имя: внутри ранга тот же порядок, что давала выборка
 * с сортировкой по ФИО. Шаблоны {@code like} приходят готовыми, экранированными символом
 * {@code !} (см. {@link PolicySpecifications}).
 */
public interface PolicySearchRepository {

    /**
     * Номер содержит строку поиска; ранг — точный номер, затем номер с неё начинается, затем остальные.
     *
     * @param number          строка поиска в нижнем регистре, как есть — для сравнения на равенство
     * @param numberPrefix    шаблон «номер начинается со строки»
     * @param numberSubstring шаблон «номер содержит строку» — он и отбирает
     */
    List<Policy> findRankedByNumber(PolicyScope scope, String number, String numberPrefix, String numberSubstring);

    /**
     * Каждый шаблон — префикс фамилии, имени или отчества; ранг — совпали фамилия и имя, затем
     * фамилия, затем остальные.
     */
    List<Policy> findRankedByNames(PolicyScope scope, List<String> namePrefixes);
}
