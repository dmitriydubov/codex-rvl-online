package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Лестница повторов — чистая арифметика по списку задержек, и проверяется она без
 * контекста и без базы.
 */
class InboundRetryPropertiesTest {

    private final InboundRetryProperties properties = new InboundRetryProperties(
            List.of(Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(30)));

    @Test
    void everyStepOfLadder_hasItsOwnDelay() {
        assertThat(properties.delayBeforeAttempt(1)).contains(Duration.ofSeconds(30));
        assertThat(properties.delayBeforeAttempt(2)).contains(Duration.ofMinutes(5));
        assertThat(properties.delayBeforeAttempt(3)).contains(Duration.ofMinutes(30));
    }

    /** Пусто означает «повторять больше нечем» — вызывающий переводит строку в отказ. */
    @Test
    void attemptBeyondLadder_hasNoDelay() {
        assertThat(properties.delayBeforeAttempt(4)).isEmpty();
    }

    /**
     * Нумерация попыток начинается с единицы. Ноль означал бы, что вызывающий передал
     * номер уже сделанной попытки вместо предстоящей, и лестница сдвинулась бы на шаг.
     */
    @Test
    void attemptBelowFirst_hasNoDelay() {
        assertThat(properties.delayBeforeAttempt(0)).isEmpty();
    }
}
