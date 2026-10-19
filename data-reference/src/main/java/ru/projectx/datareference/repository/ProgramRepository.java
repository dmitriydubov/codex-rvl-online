package ru.projectx.datareference.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.catalog.Program;

@Repository
public interface ProgramRepository extends JpaRepository<Program, UUID> {

    Optional<Program> findByExternalId(UUID externalId);

    List<Program> findAllByExternalIdIn(Collection<UUID> externalIds);
}
