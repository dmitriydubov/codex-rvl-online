package ru.projectx.datareference.repository.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.catalog.Mkb10;

@Repository
public interface Mkb10Repository extends JpaRepository<Mkb10, UUID> {

    String ROWS = """
            select new ru.projectx.datareference.repository.catalog.Mkb10Row(
                       m.id, m.externalId, m.code, m.name, m.hierarchyLevel, parent.code, m.archive)
              from Mkb10 m
              left join Mkb10 parent on parent.externalId = m.externalParentId
            """;

    Optional<Mkb10> findByExternalId(UUID externalId);

    @Query(ROWS + " where m.archive = false and upper(m.code) like :prefix escape '!' order by m.code")
    List<Mkb10Row> findActiveByCodePrefix(String prefix, Limit limit);

    @Query(ROWS + " where m.archive = false and lower(m.name) like :pattern escape '!' order by m.code")
    List<Mkb10Row> findActiveByNamePattern(String pattern, Limit limit);

    @Query(ROWS + " where upper(m.code) in :codes order by m.code")
    List<Mkb10Row> findByCodes(Collection<String> codes);
}
