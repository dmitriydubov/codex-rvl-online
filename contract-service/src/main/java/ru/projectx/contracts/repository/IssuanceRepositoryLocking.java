package ru.projectx.contracts.repository;

import java.util.List;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;

public interface IssuanceRepositoryLocking<R extends IssuanceRecord> {

    void lockFresh(List<R> records);
}
