package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.projectx.datareference.support.TestLeadership;

class OneCConsumerExpectationTest {

    @Test
    void leaderWithIngestEnabled_expectsConsumers() {
        OneCConsumerExpectation expectation = QueueContainersFixture.expectation(
                TestLeadership.leader(), QueueContainersFixture.rabbitWithAutoStartup(true));

        assertThat(expectation.consumersExpected()).isTrue();
    }

    @Test
    void standby_expectsNoConsumers() {
        OneCConsumerExpectation expectation = QueueContainersFixture.expectation(
                TestLeadership.standby(), QueueContainersFixture.rabbitWithAutoStartup(true));

        assertThat(expectation.consumersExpected()).isFalse();
    }

    @Test
    void leaderWithIngestDisabledBySettings_expectsNoConsumers() {
        OneCConsumerExpectation expectation = QueueContainersFixture.expectation(
                TestLeadership.leader(), QueueContainersFixture.rabbitWithAutoStartup(false));

        assertThat(expectation.consumersExpected()).isFalse();
    }

    @Test
    void leaderWithoutBroker_expectsNoConsumers() {
        OneCConsumerExpectation expectation = QueueContainersFixture.expectation(
                TestLeadership.leader(), QueueContainersFixture.withoutBroker());

        assertThat(expectation.consumersExpected()).isFalse();
    }
}
