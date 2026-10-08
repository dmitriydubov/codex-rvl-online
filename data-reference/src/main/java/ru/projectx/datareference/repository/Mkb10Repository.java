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
 * Запись обновляется по {@code external_id}, GUID объекта в 1С. Поиск отдаёт {@link Mkb10Row}, где код родителя
 * находится тем же соединением, а не запросом на строку. Символ экранирования в {@code like} — {@code !}: он не спорит
 * ни с HQL, ни с SQL; экранирует вызывающий.
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

    /** Поиск по перечню кодов: архивные отдаются, потому что на них ссылаются старые заявки. */
    @Query(ROWS + " where upper(m.code) in :codes order by m.code")
    List<Mkb10Row> findByCodes(Collection<String> codes);
}
