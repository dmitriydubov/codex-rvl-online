package ru.projectx.datareference.exception;

import java.io.Serial;
import java.util.UUID;

/**
 * Запрошенной записи справочника нет, и сервис отвечает 404. Остальные отказы чтения справочников —
 * это неверный запрос, ошибка данных в 1С или сбой.
 *
 * <p>Код ошибки называет справочник ({@code PROGRAM_NOT_FOUND}, а не общий {@code NOT_FOUND}). Так
 * принято в контрактах соседних сервисов, и по коду потребителю видно, какой из идентификаторов в его
 * данных не сошёлся.
 */
public final class ReferenceNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String PROGRAM_NOT_FOUND = "PROGRAM_NOT_FOUND";
    private static final String POLICY_NOT_FOUND = "POLICY_NOT_FOUND";
    private static final String INSURANCE_CONTRACT_NOT_FOUND = "INSURANCE_CONTRACT_NOT_FOUND";
    private static final String LPU_NOT_FOUND = "LPU_NOT_FOUND";

    /** Чего не нашлось: код ошибки и текст, куда подставляется идентификатор из запроса. */
    private enum Missing {
        PROGRAM(PROGRAM_NOT_FOUND, "программа страхования %s не найдена"),
        POLICY(POLICY_NOT_FOUND, "полис %s не найден"),
        POLICY_BY_EXTERNAL_ID(POLICY_NOT_FOUND, "полис с external_id %s не найден"),
        POLICY_BY_NUMBER(POLICY_NOT_FOUND, "в договоре страхования %s нет полиса с таким номером"),
        INSURANCE_CONTRACT(INSURANCE_CONTRACT_NOT_FOUND, "договор страхования %s не найден"),
        INSURANCE_CONTRACT_BY_EXTERNAL_ID(INSURANCE_CONTRACT_NOT_FOUND, "договор страхования с external_id %s не найден"),
        LPU(LPU_NOT_FOUND, "ЛПУ %s не найдено");

        private final String code;
        private final String messageTemplate;

        Missing(String code, String messageTemplate) {
            this.code = code;
            this.messageTemplate = messageTemplate;
        }

        ReferenceNotFoundException notFound(UUID id) {
            return new ReferenceNotFoundException(code, messageTemplate.formatted(id));
        }
    }

    private final String code;

    private ReferenceNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static ReferenceNotFoundException program(UUID id) {
        return Missing.PROGRAM.notFound(id);
    }

    public static ReferenceNotFoundException policy(UUID id) {
        return Missing.POLICY.notFound(id);
    }

    public static ReferenceNotFoundException policyByExternalId(UUID externalId) {
        return Missing.POLICY_BY_EXTERNAL_ID.notFound(externalId);
    }

    /** Номер полиса в текст не идёт: вызывающий его и так знает, а в журнале он лишний. */
    public static ReferenceNotFoundException policyByNumber(UUID contractId) {
        return Missing.POLICY_BY_NUMBER.notFound(contractId);
    }

    public static ReferenceNotFoundException insuranceContract(UUID id) {
        return Missing.INSURANCE_CONTRACT.notFound(id);
    }

    public static ReferenceNotFoundException insuranceContractByExternalId(UUID externalId) {
        return Missing.INSURANCE_CONTRACT_BY_EXTERNAL_ID.notFound(externalId);
    }

    /** ЛПУ нет либо оно в архиве: для каталога это одно и то же, учреждения нет. */
    public static ReferenceNotFoundException lpu(UUID id) {
        return Missing.LPU.notFound(id);
    }

    public String code() {
        return code;
    }
}
