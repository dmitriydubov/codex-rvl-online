package ru.projectx.contracts.service.packages;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.dto.PackageResponse;
import ru.projectx.contracts.entity.contract.ContractAcceptance;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.exception.RequestNotFoundException;
import ru.projectx.contracts.repository.issuance.ContractAcceptanceRepository;
import ru.projectx.contracts.repository.issuance.IssuanceRequestRepository;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.IssuanceSnapshots;

@Service
public class RequestPackages implements RequestPackageReads {

    private final IssuanceRequestRepository requests;
    private final IssuanceSnapshots snapshots;
    private final ContractAcceptanceRepository acceptances;

    public RequestPackages(IssuanceRequestRepository requests, IssuanceSnapshots snapshots,
            ContractAcceptanceRepository acceptances) {
        this.requests = requests;
        this.snapshots = snapshots;
        this.acceptances = acceptances;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PackageResponse packageOf(UUID individualRequestId) {
        if (!requests.existsById(individualRequestId)) {
            throw new RequestNotFoundException(individualRequestId);
        }
        IssuanceSnapshot snapshot = snapshots.current(individualRequestId);
        Map<UUID, ContractAcceptance> accepted = acceptancesOf(snapshot);
        return new PackageResponse(individualRequestId, snapshot.requestStatus().name(), snapshot.composition()
                .stream().map(participant -> PackageItems.of(participant, snapshot, accepted)).toList());
    }

    private Map<UUID, ContractAcceptance> acceptancesOf(IssuanceSnapshot snapshot) {
        List<UUID> contractIds = snapshot.composition().stream()
                .filter(participant -> participant.kind() == DocumentKind.KM_CONTRACT)
                .map(participant -> snapshot.contractOf(participant.participantId()).id())
                .toList();
        return acceptances.findByContractIdIn(contractIds).stream()
                .collect(Collectors.toUnmodifiableMap(ContractAcceptance::contractId, Function.identity()));
    }
}
