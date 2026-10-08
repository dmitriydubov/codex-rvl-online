package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import java.util.List;
import java.util.Map;
import ru.projectx.datareference.service.schedulers.ReferenceImportScheduler;

/**
 * Шаги «1С:УСК прислала …» и шаги разбора присланного. Разбор вызывает штатный проход планировщика, чтобы справочники
 * заполнялись тем же путём, что из 1С, а не записью прямо в их таблицы.
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

    @Дано("1С:УСК прислала вид обращения:")
    public void appealTypeSent(Map<String, String> appealType) {
        inbox.receive(OneCReferenceMessages.appealType(appealType));
    }

    @Дано("1С:УСК прислала код МКБ-10:")
    public void mkb10Sent(Map<String, String> code) {
        inbox.receive(OneCReferenceMessages.mkb10(code));
    }

    @Дано("1С:УСК прислала код МКБ-10 без наименования:")
    public void mkb10WithoutNameSent(Map<String, String> code) {
        inbox.receive(OneCReferenceMessages.mkb10WithoutName(code));
    }

    @Дано("1С:УСК прислала договор ЛПУ:")
    public void lpuContractSent(Map<String, String> contract) {
        inbox.receive(OneCReferenceMessages.lpuContract(contract));
    }

    @Дано("1С:УСК прислала программу РВЛ {string} с перечнем учреждений:")
    public void programWithLpuListSent(String externalId, List<Map<String, String>> lines) {
        inbox.receive(OneCReferenceMessages.programWithLpuList(externalId, lines));
    }

    @Дано("1С:УСК прислала адрес санатория:")
    public void sanatoriumAddressSent(Map<String, String> address) {
        inbox.receive(OneCReferenceMessages.sanatoriumAddress(address));
    }

    @Дано("1С:УСК прислала полис:")
    public void policySent(Map<String, String> policy) {
        inbox.receive(inbox.asNewDelivery(OneCMessages.policy(policy)));
    }

    /**
     * «Разобрано» значит, что каждое сообщение закончилось своим ожидаемым исходом в порядке приёма, в том числе
     * пропуском (страхователь не стал санаторием), и чужих строк сырья в базе нет.
     */
    @Дано("Дата-референс разобрал присланные сообщения")
    public void receivedMessagesImported() {
        scheduler.importPendingMessages();

        assertThat(inbox.statuses())
                .as("статусы сырья после разбора, в порядке приёма")
                .containsExactlyElementsOf(inbox.expectedStatuses());
    }

    /** Запускает тот же штатный проход разбора, но исход проверяют шаги «Тогда», а не этот шаг. */
    @Когда("Дата-референс разбирает присланные сообщения")
    public void receivedMessagesBeingImported() {
        scheduler.importPendingMessages();
    }
}
