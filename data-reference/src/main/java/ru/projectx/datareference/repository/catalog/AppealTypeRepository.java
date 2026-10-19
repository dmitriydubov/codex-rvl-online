package ru.projectx.datareference.repository.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.catalog.AppealType;

@Repository
public interface AppealTypeRepository extends JpaRepository<AppealType, UUID> {

    Optional<AppealType> findByExternalId(UUID externalId);

    @Query("select a from AppealType a where a.archive = false and lower(trim(a.name)) in :names")
    List<AppealType> findActiveByNormalizedNameIn(Collection<String> names);
}
