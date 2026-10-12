package ru.projectx.contracts.service.integration;

import java.net.http.HttpTimeoutException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.projectx.contracts.dto.ReferenceInsuranceContract;
import ru.projectx.contracts.dto.ReferenceProgram;
import ru.projectx.contracts.dto.ReferenceRecord;
import ru.projectx.contracts.dto.ReferencePolicy;
import ru.projectx.contracts.exception.ReferenceKind;
import ru.projectx.contracts.exception.ReferenceUnavailableException;
import ru.projectx.contracts.exception.ReferenceUnavailableException.Reason;
import ru.projectx.contracts.service.reference.InsuranceContractReads;
import ru.projectx.contracts.service.reference.LiveRecords;
import ru.projectx.contracts.service.reference.PolicyReads;
import ru.projectx.contracts.service.reference.ProgramReads;

/**
 * Чтение справочников по внутреннему API сервиса справочных данных. Единственное место, которое знает, как справочник
 * выглядит по HTTP: пути, формат ошибки, сетевые отказы. Наружу выходят только живые записи и исключения домена.
 */
public final class ReferenceHttpClient implements ProgramReads, InsuranceContractReads, PolicyReads {

    private static final String PROGRAM = "/programs/{id}";
    private static final String INSURANCE_CONTRACT = "/insurance-contracts/{id}";
    private static final String POLICY = "/policies/{id}/contract-view";
    private static final String POLICY_BY_EXTERNAL_ID = "/policies/contract-view?external_id={externalId}";

    private final RestClient http;
    private final ReferenceRetries retries;

    ReferenceHttpClient(RestClient http, ReferenceRetries retries) {
        this.http = http;
        this.retries = retries;
    }

    @Override
    public ReferenceProgram program(UUID id) {
        return readById(ReferenceKind.PROGRAM, PROGRAM, id, ReferenceProgram.class);
    }

    @Override
    public ReferenceInsuranceContract insuranceContract(UUID id) {
        return readById(ReferenceKind.INSURANCE_CONTRACT, INSURANCE_CONTRACT, id, ReferenceInsuranceContract.class);
    }

    @Override
    public ReferencePolicy policy(UUID id) {
        return readById(ReferenceKind.POLICY, POLICY, id, ReferencePolicy.class);
    }

    @Override
    public ReferencePolicy policyByExternalId(UUID externalId) {
        return retries.byExternalId(ReferenceKind.POLICY, externalId,
                () -> read(ReferenceKind.POLICY, POLICY_BY_EXTERNAL_ID, externalId, ReferencePolicy.class));
    }

    private <T extends ReferenceRecord> T readById(ReferenceKind kind, String uriTemplate, UUID id, Class<T> type) {
        return retries.byId(kind, id, () -> read(kind, uriTemplate, id, type));
    }

    private <T extends ReferenceRecord> T read(ReferenceKind kind, String uriTemplate, UUID key, Class<T> type) {
        return LiveRecords.requireLive(kind, fetch(uriTemplate, key, type));
    }

    private <T> T fetch(String uriTemplate, UUID key, Class<T> type) {
        try {
            return requireBody(http.get().uri(uriTemplate, key).retrieve().body(type));
        }
        catch (ResourceAccessException io) {
            throw new ReferenceUnavailableException(transportReason(io), io);
        }
        catch (RestClientException unreadable) {
            throw new ReferenceUnavailableException(Reason.UNEXPECTED_RESPONSE, unreadable);
        }
    }

    /** Пустое тело при успешном статусе — такой же неожиданный ответ, как чужой статус. */
    private static <T> T requireBody(@Nullable T body) {
        if (body == null) {
            throw new ReferenceUnavailableException(Reason.UNEXPECTED_RESPONSE);
        }
        return body;
    }

    private static Reason transportReason(ResourceAccessException io) {
        return io.getCause() instanceof HttpTimeoutException ? Reason.TIMEOUT : Reason.CONNECTION_FAILED;
    }
}
