package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Дано;
import java.util.Map;
import ru.projectx.datareference.service.schedulers.ReferenceImportScheduler;

/**
 * Шаги «1С:УСК прислала …»: сырьё ложится так, как его сохранил бы приём из брокера,
 * а шаг разбора вызывает штатный проход планировщика — справочники получаются тем же
 * путём, что из 1С (решение Р6, как на стенде потребителя).
 */
public class OneCSteps {

    private final OneCInbox inbox;
    private final ReferenceImportScheduler scheduler;

    public OneCSteps(OneCInbox inbox, ReferenceImportScheduler scheduler) {
        this.inbox = inbox;
        this.scheduler = scheduler;
    }

    @Дано("1С:УСК прислала страхователя:")
    public void insurerSent(Map<String, String> insurer) {
        inbox.receive(OneCMessages.insurer(insurer));
    }

    @Дано("1С:УСК прислала санаторий РВЛ:")
    public void sanatoriumSent(Map<String, String> sanatorium) {
        inbox.receive(OneCMessages.sanatorium(sanatorium));
    }

    @Дано("1С:УСК прислала договор страхования ДМС:")
    public void insuranceContractSent(Map<String, String> contract) {
        inbox.receive(OneCMessages.insuranceContract(contract));
    }

    @Дано("1С:УСК прислала программу РВЛ с одной услугой:")
    public void programSent(Map<String, String> program) {
        inbox.receive(OneCMessages.program(program));
    }

    @Дано("1С:УСК прислала полис:")
    public void policySent(Map<String, String> policy) {
        inbox.receive(OneCMessages.policy(policy));
    }

    /**
     * Разобрано — значит, каждое сообщение закончилось своим исходом, в порядке приёма:
     * справочник пополнился ({@code PROCESSED}), а страхователь ТЗ-3 ушёл в пропуск
     * с причиной — санаторием он не стал. Строк сырья ровно столько, сколько прислал
     * сценарий: чужих строк в базе нет.
     */
    @Дано("Дата-референс разобрал присланные сообщения")
    public void receivedMessagesImported() {
        scheduler.importPendingMessages();

        assertThat(inbox.statuses())
                .as("статусы сырья после разбора, в порядке приёма")
                .containsExactlyElementsOf(inbox.expectedStatuses());
    }
}
