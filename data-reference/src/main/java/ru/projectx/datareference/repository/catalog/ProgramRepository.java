package ru.projectx.datareference.repository.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.catalog.Program;
import ru.projectx.datareference.repository.ReferenceId;

@Repository
public interface ProgramRepository extends JpaRepository<Program, UUID> {

    Optional<Program> findByExternalId(UUID externalId);

    List<Program> findAllByExternalIdIn(Collection<UUID> externalIds);

    List<ReferenceId> findByArchiveFalseOrderByIdAsc(Limit limit);
}
