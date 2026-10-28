package ru.projectx.contracts.acceptance;

import io.cucumber.java.ru.Когда;
import ru.projectx.contracts.service.lm.awaiting.LmPolicyAwaiting;

public class LmPolicyAwaitingSteps {

    private final LmPolicyAwaiting awaiting;

    public LmPolicyAwaitingSteps(LmPolicyAwaiting awaiting) {
        this.awaiting = awaiting;
    }

    @Когда("Договоры обновляют сводку полисов ЛМ, ждущих ответа 1С:УСК")
    public void summaryRefreshed() {
        awaiting.refresh();
    }
}
