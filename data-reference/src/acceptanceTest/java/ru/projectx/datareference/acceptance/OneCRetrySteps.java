package ru.projectx.datareference.acceptance;

import io.cucumber.java.ru.Когда;
import java.util.UUID;

public class OneCRetrySteps {

    private final OneCInbox inbox;

    public OneCRetrySteps(OneCInbox inbox) {
        this.inbox = inbox;
    }

    @Когда("у сообщений 1С:УСК, отложенных до повтора, наступил срок повтора")
    public void retryDue() {
        inbox.retryDueNow();
    }

    @Когда("дежурный вернул последнее сообщение 1С:УСК к разбору")
    public void lastReturnedToParsing() {
        inbox.returnLastToParsing();
    }

    @Когда("дежурный вернул к разбору все сообщения передачи прайса {string}")
    public void transferReturnedToParsing(String cid) {
        inbox.returnTransferToParsing(UUID.fromString(cid));
    }
}
