package ru.projectx.datareference.service.leadership;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.jdbc.lock.DefaultLockRepository;
import org.springframework.integration.jdbc.lock.JdbcLockRegistry;
import org.springframework.integration.support.leader.LockRegistryLeaderInitiator;
import ru.projectx.datareference.config.LeadershipProperties;

@Configuration(proxyBeanMethods = false)
public class LeadershipConfiguration {

    static final String ROLE = "onec-background";
    static final String TABLE_PREFIX = "leader_";
    static final String SKIP_TAKEN_LOCK = " ON CONFLICT DO NOTHING";

    @Bean
    LeaderLock leaderLock(LeadershipProperties properties) {
        return LeaderLock.forNewInstance(properties.group(), ROLE);
    }

    @Bean
    BackgroundLeadership backgroundLeadership(LeaderLock lock) {
        return new BackgroundLeadership(lock);
    }

    @Bean
    DefaultLockRepository leaderLockStore(DataSource dataSource, LeaderLock lock) {
        DefaultLockRepository store = new DefaultLockRepository(dataSource, lock.instanceId());
        store.setPrefix(TABLE_PREFIX);
        store.setRegion(lock.group());
        store.setInsertQuery(store.getInsertQuery() + SKIP_TAKEN_LOCK);
        return store;
    }

    @Bean
    JdbcLockRegistry leaderLockRegistry(DefaultLockRepository leaderLockStore, LeadershipProperties properties) {
        JdbcLockRegistry registry = new JdbcLockRegistry(leaderLockStore, properties.lockTtl());
        registry.setIdleBetweenTries(properties.heartbeat());
        return registry;
    }

    @Bean
    LockRegistryLeaderInitiator leaderInitiator(JdbcLockRegistry leaderLockRegistry, BackgroundLeadership leadership,
            LeadershipProperties properties) {

        LockRegistryLeaderInitiator initiator = new LockRegistryLeaderInitiator(leaderLockRegistry, leadership);
        initiator.setHeartBeatMillis(properties.heartbeat().toMillis());
        return initiator;
    }
}
