package ru.projectx.contracts.service.leadership;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.service.FlagGauges;

@Component
class LeaderRoleGauge implements MeterBinder {

    static final String LEADER = "contracts.leader";

    private final BackgroundLeadership leadership;

    LeaderRoleGauge(BackgroundLeadership leadership) {
        this.leadership = leadership;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        FlagGauges.register(registry, LEADER,
                "1 — экземпляр ведущий: читает очереди и выполняет проходы по расписанию; 0 — нет",
                leadership::leader);
    }
}
