package ru.projectx.datareference.acceptance;

import io.cucumber.java.ru.Когда;

/**
 * Шаги о времени и ручной работе с сырьём 1С:УСК: наступивший срок повтора и возврат сообщения к разбору дежурным.
 * Оба меняют строки сырья запросом, как это сделали бы время и дежурный, а разбирает их потом штатный проход.
 */
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
}
