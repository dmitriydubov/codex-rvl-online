package ru.projectx.datareference.service.leadership;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
class LeaderRoleGauge implements MeterBinder {

    static final String LEADER = "onec.leader";

    private final BackgroundLeadership leadership;

    LeaderRoleGauge(BackgroundLeadership leadership) {
        this.leadership = leadership;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(LEADER, leadership, LeaderRoleGauge::leaderFlag)
                .description("1 — экземпляр ведущий: принимает сообщения 1С:УСК и выполняет задачи по расписанию; 0 — нет")
                .register(registry);
    }

    private static double leaderFlag(BackgroundLeadership leadership) {
        return leadership.leader() ? 1 : 0;
    }
}
