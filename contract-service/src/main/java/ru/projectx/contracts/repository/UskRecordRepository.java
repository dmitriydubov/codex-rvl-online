package ru.projectx.contracts.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;
import ru.projectx.contracts.entity.issuance.IssuanceStatus;

@NoRepositoryBean
public interface UskRecordRepository<R extends IssuanceRecord, S extends IssuanceStatus> extends CrudRepository<R, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<R> findLockedById(UUID id);

    List<R> findByStatusOrderByCreatedAtAscIdAsc(S status, Limit limit);

    Optional<R> findFirstByStatusOrderByUpdatedAtAscIdAsc(S status);

    long countByStatusAndUpdatedAtBefore(S status, Instant moment);
}
