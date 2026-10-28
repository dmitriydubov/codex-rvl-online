package ru.projectx.datareference.service.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.ref.WeakReference;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.config.ReferenceApiProperties;
import ru.projectx.datareference.support.TestApiProperties;

class RestReadAdmissionTest {

    private static final int LIMIT = 2;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final RestReadAdmission admission = new RestReadAdmission(withLimit(LIMIT), meters);

    @Test
    void admitsExactlyTheLimit_thenRejects() {
        assertThat(admission.tryEnter()).isTrue();
        assertThat(admission.tryEnter()).isTrue();

        assertThat(admission.tryEnter()).isFalse();
        assertThat(admission.tryEnter()).isFalse();
    }

    @Test
    void limit_isTheConfiguredNumberOfConcurrentRequests() {
        assertThat(admission.limit()).isEqualTo(LIMIT);
        assertThat(new RestReadAdmission(withLimit(1), meters).limit()).isEqualTo(1);
    }

    @Test
    void leaving_freesAPlaceForTheNextRequest() {
        admission.tryEnter();
        admission.tryEnter();

        admission.leave();

        assertThat(admission.tryEnter()).isTrue();
        assertThat(admission.tryEnter()).isFalse();
    }

    @Test
    void rejectedCounter_countsOnlyRejections() {
        admission.tryEnter();
        admission.tryEnter();
        assertThat(rejected()).isZero();

        admission.tryEnter();
        admission.tryEnter();
        admission.leave();
        admission.tryEnter();

        assertThat(rejected()).isEqualTo(2.0);
        assertThat(meters.get(RestReadAdmission.REJECTED).counter().getId().getDescription())
                .isEqualTo("Запросы к /internal/v1, отклонённые по пределу одновременно обрабатываемых запросов");
    }

    @Test
    void inFlightGauge_showsAdmittedRequestsStillInside() {
        assertThat(inFlight()).isZero();
        admission.tryEnter();
        assertThat(inFlight()).isEqualTo(1.0);
        admission.tryEnter();
        admission.tryEnter();
        assertThat(inFlight()).isEqualTo(2.0);
        admission.leave();
        assertThat(inFlight()).isEqualTo(1.0);
        assertThat(meters.get(RestReadAdmission.IN_FLIGHT).gauge().getId().getDescription())
                .isEqualTo("Запросы к /internal/v1 в обработке");
    }

    @Test
    void inFlightGauge_keepsItsSource_afterGarbageCollection_whenOnlyTheRegistryRemains() {
        SimpleMeterRegistry survivingMeters = new SimpleMeterRegistry();
        enterOnceAndDropTheAdmission(survivingMeters);
        WeakReference<Object> canary = new WeakReference<>(new Object());

        await().atMost(Duration.ofSeconds(10)).until(() -> {
            System.gc();
            return canary.get() == null;
        });

        assertThat(survivingMeters.get(RestReadAdmission.IN_FLIGHT).gauge().value()).isEqualTo(1.0);
    }

    private static void enterOnceAndDropTheAdmission(SimpleMeterRegistry meters) {
        new RestReadAdmission(withLimit(LIMIT), meters).tryEnter();
    }

    private double rejected() {
        return meters.get(RestReadAdmission.REJECTED).counter().count();
    }

    private double inFlight() {
        return meters.get(RestReadAdmission.IN_FLIGHT).gauge().value();
    }

    private static ReferenceApiProperties withLimit(int limit) {
        return TestApiProperties.withMaxConcurrentRequests(limit);
    }
}
