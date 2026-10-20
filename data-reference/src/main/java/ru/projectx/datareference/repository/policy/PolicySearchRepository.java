package ru.projectx.datareference.repository.policy;

import java.util.List;
import ru.projectx.datareference.entity.policy.Policy;

public interface PolicySearchRepository {

    List<Policy> findRankedByNumber(PolicyScope scope, String number, String numberPrefix, String numberSubstring);

    List<Policy> findRankedByNames(PolicyScope scope, List<String> namePrefixes);
}
