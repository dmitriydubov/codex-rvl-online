package ru.projectx.contracts.message.status;

import java.util.List;
import java.util.UUID;

public record RequestIssuanceStatus(UUID individualRequestId, RequestStatus status, List<ParticipantStatus> participants) {
}
