package ru.projectx.contracts.service.reference;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.exception.ReferenceKind;

class ReferenceMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ReferenceMetrics metrics = new ReferenceMetrics(registry);

    @Test
    void countUnavailable_incrementsCounterTaggedWithLowercaseKind() {
        metrics.countUnavailable(ReferenceKind.PROGRAM);
        metrics.countUnavailable(ReferenceKind.PROGRAM);
        metrics.countUnavailable(ReferenceKind.INSURANCE_CONTRACT);
        metrics.countUnavailable(ReferenceKind.POLICY);

        assertThat(registry.get("contracts.reference.unavailable").tag("kind", "program").counter().count()).isEqualTo(2);
        assertThat(registry.get("contracts.reference.unavailable").tag("kind", "insurance_contract").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("contracts.reference.unavailable").tag("kind", "policy").counter().count()).isEqualTo(1);
        assertThat(registry.get("contracts.reference.unavailable").counter().getId().getDescription())
                .isEqualTo("обращения к справочному сервису, не получившие ответа после всех повторов");
    }
}
