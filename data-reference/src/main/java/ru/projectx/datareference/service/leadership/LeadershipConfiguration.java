package ru.projectx.datareference.service.leadership;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.jdbc.lock.DefaultLockRepository;
import org.springframework.integration.jdbc.lock.JdbcLockRegistry;
import org.springframework.integration.support.leader.LockRegistryLeaderInitiator;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import ru.projectx.datareference.config.LeadershipProperties;
import ru.projectx.datareference.config.leadership.LeaderLockPoolConfig;

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
    DefaultLockRepository leaderLockStore(
            @Qualifier(LeaderLockPoolConfig.LEADER_LOCK_POOL) DataSource leaderLockPool, LeaderLock lock) {

        DefaultLockRepository store = new DefaultLockRepository(leaderLockPool, lock.instanceId());
        store.setPrefix(TABLE_PREFIX);
        store.setRegion(lock.group());
        store.setInsertQuery(store.getInsertQuery() + SKIP_TAKEN_LOCK);
        store.setTransactionManager(new DataSourceTransactionManager(leaderLockPool));
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
