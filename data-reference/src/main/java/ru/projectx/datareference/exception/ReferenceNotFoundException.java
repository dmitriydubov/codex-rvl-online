package ru.projectx.datareference.exception;

import java.io.Serial;
import java.util.UUID;

/**
 * Запрошенной записи справочника нет, и сервис отвечает 404. Код ошибки называет справочник, например
 * {@code PROGRAM_NOT_FOUND}, а не общий {@code NOT_FOUND}: так принято в контрактах соседних сервисов,
 * и потребителю видно, какой из его идентификаторов не сошёлся.
 */
public final class ReferenceNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String PROGRAM_NOT_FOUND = "PROGRAM_NOT_FOUND";
    private static final String POLICY_NOT_FOUND = "POLICY_NOT_FOUND";
    private static final String INSURANCE_CONTRACT_NOT_FOUND = "INSURANCE_CONTRACT_NOT_FOUND";
    private static final String LPU_NOT_FOUND = "LPU_NOT_FOUND";
    private static final String SERVICE_NOT_FOUND = "SERVICE_NOT_FOUND";

    /** Чего не нашлось: код ошибки и текст, куда подставляется идентификатор из запроса. */
    private enum Missing {
        PROGRAM(PROGRAM_NOT_FOUND, "программа страхования %s не найдена"),
        POLICY(POLICY_NOT_FOUND, "полис %s не найден"),
        POLICY_BY_EXTERNAL_ID(POLICY_NOT_FOUND, "полис с external_id %s не найден"),
        POLICY_BY_NUMBER(POLICY_NOT_FOUND, "в договоре страхования с external_id %s нет полиса с таким номером"),
        INSURANCE_CONTRACT(INSURANCE_CONTRACT_NOT_FOUND, "договор страхования %s не найден"),
        INSURANCE_CONTRACT_BY_EXTERNAL_ID(INSURANCE_CONTRACT_NOT_FOUND, "договор страхования с external_id %s не найден"),
        LPU(LPU_NOT_FOUND, "ЛПУ %s не найдено"),
        SERVICE(SERVICE_NOT_FOUND, "услуга %s не найдена");

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
    public static ReferenceNotFoundException policyByNumber(UUID contractExternalId) {
        return Missing.POLICY_BY_NUMBER.notFound(contractExternalId);
    }

    public static ReferenceNotFoundException insuranceContract(UUID id) {
        return Missing.INSURANCE_CONTRACT.notFound(id);
    }

    public static ReferenceNotFoundException insuranceContractByExternalId(UUID externalId) {
        return Missing.INSURANCE_CONTRACT_BY_EXTERNAL_ID.notFound(externalId);
    }

    /** Лечебного учреждения нет или оно в архиве: для каталога это одно и то же. */
    public static ReferenceNotFoundException lpu(UUID id) {
        return Missing.LPU.notFound(id);
    }

    /**
     * Услуги ЛПУ нет, она в архиве или её нет среди услуг, которые учреждение предлагает по видам обращения
     * РВЛ. Для расчёта стоимости все три случая одинаковы: такую услугу вызывающий не мог получить из перечня.
     */
    public static ReferenceNotFoundException service(UUID id) {
        return Missing.SERVICE.notFound(id);
    }

    public String code() {
        return code;
    }
}
