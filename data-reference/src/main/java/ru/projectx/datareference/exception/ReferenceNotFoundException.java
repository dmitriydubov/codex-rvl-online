package ru.projectx.datareference.exception;

import java.io.Serial;
import java.util.UUID;

/**
 * Запрошенной записи справочника нет. Единственная ошибка, которую REST-чтение
 * справочников считает своей: остальное — либо неверный запрос, либо сбой.
 *
 * <p>Код ошибки называет справочник ({@code PROGRAM_NOT_FOUND}, а не общий
 * {@code NOT_FOUND}): так принято в контрактах контура, и потребителю по коду видно,
 * какой из трёх идентификаторов в его данных не сошёлся.
 */
public class ReferenceNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String PROGRAM_NOT_FOUND = "PROGRAM_NOT_FOUND";
    private static final String POLICY_NOT_FOUND = "POLICY_NOT_FOUND";
    private static final String INSURANCE_CONTRACT_NOT_FOUND = "INSURANCE_CONTRACT_NOT_FOUND";
    private static final String LPU_NOT_FOUND = "LPU_NOT_FOUND";

    private final String code;

    private ReferenceNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static ReferenceNotFoundException program(UUID id) {
        return new ReferenceNotFoundException(PROGRAM_NOT_FOUND, "программа страхования " + id + " не найдена");
    }

    public static ReferenceNotFoundException policy(UUID id) {
        return new ReferenceNotFoundException(POLICY_NOT_FOUND, "полис " + id + " не найден");
    }

    public static ReferenceNotFoundException policyByExternalId(UUID externalId) {
        return new ReferenceNotFoundException(POLICY_NOT_FOUND, "полис с external_id " + externalId + " не найден");
    }

    /** Номер в текст не идёт: он и так в запросе вызывающего, а лишнего в журнале не нужно. */
    public static ReferenceNotFoundException policyByNumber(UUID contractId) {
        return new ReferenceNotFoundException(POLICY_NOT_FOUND,
                "в договоре страхования " + contractId + " нет полиса с таким номером");
    }

    public static ReferenceNotFoundException insuranceContract(UUID id) {
        return new ReferenceNotFoundException(INSURANCE_CONTRACT_NOT_FOUND, "договор страхования " + id + " не найден");
    }

    public static ReferenceNotFoundException insuranceContractByExternalId(UUID externalId) {
        return new ReferenceNotFoundException(INSURANCE_CONTRACT_NOT_FOUND,
                "договор страхования с external_id " + externalId + " не найден");
    }

    /** ЛПУ нет либо оно в архиве: для каталога это одно и то же — учреждения нет. */
    public static ReferenceNotFoundException lpu(UUID id) {
        return new ReferenceNotFoundException(LPU_NOT_FOUND, "ЛПУ " + id + " не найдено");
    }

    public String code() {
        return code;
    }
}
