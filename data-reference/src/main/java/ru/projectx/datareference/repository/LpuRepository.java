package ru.projectx.datareference.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.Lpu;

@Repository
public interface LpuRepository extends JpaRepository<Lpu, UUID> {

    Optional<Lpu> findByExternalId(UUID externalId);

    List<Lpu> findAllByExternalIdIn(Collection<UUID> externalIds);

    @Query("""
            select distinct lpu from ProgramLpu line
              join line.lpu lpu
              join line.program program
             where program.externalId in :programExternalIds
               and program.archive = false
               and lpu.archive = false
               and line.externalAppealTypeId in :appealTypeExternalIds
             order by lpu.name
            """)
    List<Lpu> findRvlCatalog(Collection<UUID> programExternalIds, Collection<UUID> appealTypeExternalIds);
}
