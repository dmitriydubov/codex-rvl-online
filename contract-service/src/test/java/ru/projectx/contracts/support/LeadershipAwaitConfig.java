package ru.projectx.contracts.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;

@TestConfiguration(proxyBeanMethods = false)
public class LeadershipAwaitConfig {

    @Bean
    public LeadershipAwait leadershipAwait(BackgroundLeadership leadership) {
        return new LeadershipAwait(leadership);
    }
}
