package ru.projectx.datareference.config.restread;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import ru.projectx.datareference.config.leadership.LeaderLockPoolConfig;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class ApplicationPoolDependenciesTest {

    @Autowired
    private ConfigurableListableBeanFactory beans;

    @Test
    void applicationPoolBean_dependsOnTheDataSource_soPoolsCloseBeforeTheApplicationPool() {
        assertThat(beans.getDependentBeans("dataSource")).contains("applicationPool", RestReadPoolConfig.REST_READ_POOL);
        assertThat(beans.getDependentBeans("applicationPool")).contains(LeaderLockPoolConfig.LEADER_LOCK_POOL,
                "referenceCacheListenerConnections", "dbHealthContributor");
    }
}
