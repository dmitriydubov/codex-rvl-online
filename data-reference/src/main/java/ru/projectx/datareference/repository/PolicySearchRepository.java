package ru.projectx.datareference.repository;

import java.util.List;
import ru.projectx.datareference.entity.Policy;

public interface PolicySearchRepository {

    List<Policy> findRankedByNumber(PolicyScope scope, String number, String numberPrefix, String numberSubstring);

    List<Policy> findRankedByNames(PolicyScope scope, List<String> namePrefixes);
}
