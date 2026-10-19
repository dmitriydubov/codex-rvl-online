package ru.projectx.datareference.repository.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PolicyContractRowTest {

    @Test
    void components_areOnlyIdsNumberPeriodAndArchiveFlag() {
        assertThat(Arrays.stream(PolicyContractRow.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("id", "externalId", "number", "beginDate", "endDate", "archive");
    }
}
