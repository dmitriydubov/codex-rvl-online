package ru.projectx.datareference.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.catalog.Insurer;

@Repository
public interface InsurerRepository extends JpaRepository<Insurer, UUID> {

    Optional<Insurer> findByExternalId(UUID externalId);
}
