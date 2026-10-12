package ru.projectx.datareference.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.InboxEntry;

@Repository
public interface InboxRepository extends JpaRepository<InboxEntry, UUID> {
}
