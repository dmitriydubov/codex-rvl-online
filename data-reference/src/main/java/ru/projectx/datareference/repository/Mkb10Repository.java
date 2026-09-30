package ru.projectx.datareference.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.Mkb10;

/**
 * Upsert идёт по {@code external_id} — GUID объекта в 1С, а не по нашему первичному
 * ключу: другого устойчивого идентификатора у сообщения источника нет.
 *
 * <p>Поиск для R-07 отдаёт проекцию {@link Mkb10Row} с кодом родителя: родитель хранится
 * GUID'ом источника, и разрешается он здесь, одним соединением, а не по строке.
 * Символ экранирования в {@code like} — {@code !}: он не спорит ни с HQL, ни с SQL,
 * а искомую строку экранирует вызывающий сервис.
 */
@Repository
public interface Mkb10Repository extends JpaRepository<Mkb10, UUID> {

    String ROWS = """
            select new ru.projectx.datareference.repository.Mkb10Row(
                       m.id, m.externalId, m.code, m.name, m.hierarchyLevel, parent.code, m.archive)
              from Mkb10 m
              left join Mkb10 parent on parent.externalId = m.externalParentId
            """;

    Optional<Mkb10> findByExternalId(UUID externalId);

    /** Подсказка по коду: префикс без учёта регистра, архивные не показываются. */
    @Query(ROWS + " where m.archive = false and upper(m.code) like :prefix escape '!' order by m.code")
    List<Mkb10Row> findActiveByCodePrefix(String prefix, Limit limit);

    /** Подсказка по наименованию: подстрока без учёта регистра, архивные не показываются. */
    @Query(ROWS + " where m.archive = false and lower(m.name) like :pattern escape '!' order by m.code")
    List<Mkb10Row> findActiveByNamePattern(String pattern, Limit limit);

    /** Батч по перечню кодов: архивные отдаются — на них ссылаются старые заявки. */
    @Query(ROWS + " where upper(m.code) in :codes order by m.code")
    List<Mkb10Row> findByCodes(Collection<String> codes);
}
