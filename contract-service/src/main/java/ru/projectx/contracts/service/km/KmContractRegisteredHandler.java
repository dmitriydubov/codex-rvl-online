package ru.projectx.contracts.service.km;

import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.service.inbox.parse.MessageBodies;
import ru.projectx.contracts.service.inbox.usk.UskResultParser;
import ru.projectx.contracts.service.reference.IssuedPolicyLookup;
import ru.projectx.contracts.service.usk.result.UskResultHandler;

@Component
public class KmContractRegisteredHandler extends UskResultHandler<Contract, ContractStatus> {

    public KmContractRegisteredHandler(MessageBodies bodies, IssuedPolicyLookup lookup,
            KmContractRegistrationRecording recording) {
        super(new UskResultParser(bodies, KmContractTexts.LAYOUT), KmContractKind.KIND, lookup, recording);
    }
}
