package ru.projectx.contracts.service.km;

import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.service.inbox.MessageBodies;
import ru.projectx.contracts.service.inbox.UskResultParser;
import ru.projectx.contracts.service.reference.IssuedPolicyLookup;
import ru.projectx.contracts.service.usk.UskResultHandler;

@Component
public class KmContractRegisteredHandler extends UskResultHandler<Contract, ContractStatus> {

    public KmContractRegisteredHandler(MessageBodies bodies, IssuedPolicyLookup lookup,
            KmContractRegistrationRecording recording) {
        super(new UskResultParser(bodies, KmContractTexts.LAYOUT), KmContractKind.KIND, lookup, recording);
    }
}
