package ru.projectx.contracts.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.projectx.contracts.entity.PrintedForm;

public interface PrintedFormRepository extends JpaRepository<PrintedForm, UUID> {

    List<PrintedForm> findByLmPolicyIdInOrderByCreatedAtAscIdAsc(Collection<UUID> lmPolicyIds);
}
