package ru.projectx.datareference.support;

import jakarta.servlet.Filter;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.context.WebApplicationContext;
import ru.projectx.datareference.service.leadership.BackgroundLeadership;

@TestConfiguration(proxyBeanMethods = false)
public class TestSupportConfig {

    @Bean
    public DatabaseCleaner databaseCleaner(DataSource dataSource, CacheManager cacheManager) {
        return new DatabaseCleaner(dataSource, cacheManager);
    }

    @Bean
    public LeadershipAwait leadershipAwait(BackgroundLeadership leadership) {
        return new LeadershipAwait(leadership);
    }

    @Bean
    public TestJdbc testJdbc(DataSource dataSource) {
        return new TestJdbc(dataSource);
    }

    @Bean
    public MockMvcTester mockMvcTester(WebApplicationContext context, List<Filter> filters) {
        return MockMvcTester.from(context, builder -> builder
                .addFilters(filters.toArray(Filter[]::new))
                .build());
    }
}
