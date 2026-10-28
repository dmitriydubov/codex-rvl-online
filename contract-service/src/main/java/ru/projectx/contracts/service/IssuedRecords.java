package ru.projectx.contracts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.projectx.contracts.message.task.ContractTask;

final class IssuedRecords {

    static final String ISSUED = "выпущен";

    private static final Logger log = LoggerFactory.getLogger(IssuedRecords.class);

    private IssuedRecords() {
    }

    static void keepOnKindChange(ActiveIssuance issued, ContractTask task) {
        warn(issued, task, "смена вида документа из задачи версии " + task.entityVersion() + " не применена");
    }

    static void keepOnExclusion(ActiveIssuance issued, ContractTask task) {
        warn(issued, task, "исключение участника задачей версии " + task.entityVersion() + " не применено");
    }

    private static void warn(ActiveIssuance issued, ContractTask task, String change) {
        log.warn("{} {} участника {} заявки {}: {} {}, {}", issued.name(), issued.record().id(),
                issued.record().participantId(), task.individualRequestId(), issued.name(), ISSUED, change);
    }
}
