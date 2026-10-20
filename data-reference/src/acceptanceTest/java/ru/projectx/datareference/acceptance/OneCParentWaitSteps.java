package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.entity.inbound.InboundStatus;
import ru.projectx.datareference.service.schedulers.ReferenceImportScheduler;

public class OneCParentWaitSteps {

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Map<String, UnaryOperator<Duration>> AGE_AROUND_TIMEOUT = Map.of(
            "больше", timeout -> timeout.plus(HOUR),
            "меньше", timeout -> timeout.minus(HOUR));

    private final OneCInbox inbox;
    private final ReferenceImportScheduler scheduler;
    private final ImportProperties properties;

    public OneCParentWaitSteps(OneCInbox inbox, ReferenceImportScheduler scheduler, ImportProperties properties) {
        this.inbox = inbox;
        this.scheduler = scheduler;
        this.properties = properties;
    }

    @Дано("1С:УСК прислала контрагента ЛПУ без вида деятельности РВЛ:")
    public void sanatoriumWithoutRvlSent(Map<String, String> contragent) {
        inbox.receive(OneCParentWaitMessages.sanatoriumWithoutRvl(contragent));
    }

    @Дано("1С:УСК прислала контактную информацию контрагента:")
    public void contragentContactsSent(Map<String, String> contacts) {
        inbox.receive(OneCParentWaitMessages.contragentContacts(contacts));
    }

    @Дано("1С:УСК прислала договоры ЛПУ числом {int}, каждый своего неизвестного санатория")
    public void contractsOfUnknownSanatoriumsSent(int count) {
        assertThat(count).as("число договоров в шаге").isPositive();
        OneCParentWaitMessages.contractsOfUnknownSanatoriums(count).forEach(inbox::receive);
    }

    @Когда("Дата-референс разбирает присланные сообщения ещё {int} раза подряд")
    public void importPasses(int passes) {
        assertThat(passes).as("число проходов в шаге").isPositive();
        for (int pass = 0; pass < passes; pass++) {
            scheduler.importPendingMessages();
        }
    }

    @Дано("с начала ожидания родителя прошло на час {word} срока ожидания родителя")
    public void parentWaitsAgedAroundTimeout(String moreOrLess) {
        assertThat(AGE_AROUND_TIMEOUT).as("давность, которую умеет изображать шаг").containsKey(moreOrLess);
        inbox.ageParentWaits(AGE_AROUND_TIMEOUT.get(moreOrLess).apply(properties.parentWaitTimeout()));
    }

    @Тогда("сообщение 1С:УСК {string} применено")
    public void messageApplied(String messageId) {
        assertApplied(inbox.row(UUID.fromString(messageId)), messageId);
    }

    @Тогда("последнее сообщение 1С:УСК применено")
    public void lastMessageApplied() {
        assertApplied(inbox.last(), "последнего");
    }

    @Тогда("последнее сообщение 1С:УСК ждёт родителя с причиной {string}")
    public void lastMessageAwaitsParent(String reason) {
        assertAwaiting(inbox.last(), "последнего", reason);
    }

    @Тогда("сообщение 1С:УСК {string} ждёт родителя с причиной {string}")
    public void messageAwaitsParent(String messageId, String reason) {
        assertAwaiting(inbox.row(UUID.fromString(messageId)), messageId, reason);
    }

    private static void assertApplied(InboundRow row, String which) {
        assertThat(row.status()).as("статус сообщения %s", which).isEqualTo(InboundStatus.PROCESSED.name());
    }

    private static void assertAwaiting(InboundRow row, String which, String reason) {
        assertThat(row.status()).as("статус сообщения %s", which).isEqualTo(InboundStatus.AWAITING_PARENT.name());
        assertThat(row.lastError()).as("причина ожидания сообщения %s", which).isEqualTo(reason);
    }
}
