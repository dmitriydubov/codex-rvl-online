package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Когда;
import java.util.Map;
import ru.projectx.datareference.service.integration.OneCConsumerPause;
import ru.projectx.datareference.service.metrics.InboundBacklogSchedule;
import ru.projectx.datareference.service.schedulers.ScheduledPasses;

public class ScheduledPassSteps {

    private static final String STUCK_TRANSFER_CHECK = "проверка зависших передач прайсов";

    private final Map<String, Runnable> passes;
    private final OneCStuckTransferSteps stuckTransfers;
    private final CacheNotificationsDelivered notifications;

    public ScheduledPassSteps(ScheduledPasses scheduled, InboundBacklogSchedule backlog, OneCConsumerPause pause,
            OneCStuckTransferSteps stuckTransfers, CacheNotificationsDelivered notifications) {

        this.passes = Map.of(
                "разбор сообщений 1С:УСК", scheduled::importOnSchedule,
                "стирание персональных данных", scheduled::retentionOnSchedule,
                STUCK_TRANSFER_CHECK, scheduled::stuckTransferCheckOnSchedule,
                "снимок метрик из базы", backlog::refreshOnSchedule,
                "проба хранилища при паузе приёма", pause::resumeIfStorageAvailable);
        this.stuckTransfers = stuckTransfers;
        this.notifications = notifications;
    }

    @Когда("по расписанию наступил проход {string}")
    public void scheduledPass(String pass) {
        assertThat(passes).as("проходы по расписанию, которые умеет запускать шаг").containsKey(pass);
        if (STUCK_TRANSFER_CHECK.equals(pass)) {
            stuckTransfers.journalCheck(passes.get(pass));
            return;
        }
        passes.get(pass).run();
        notifications.awaitDelivered();
    }
}
