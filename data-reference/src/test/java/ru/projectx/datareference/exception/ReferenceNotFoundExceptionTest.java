package ru.projectx.datareference.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReferenceNotFoundExceptionTest {

    private static final UUID ID = UUID.fromString("0a9e0000-0000-4000-8000-000000000001");

    static Stream<Arguments> factories() {
        return Stream.of(
                missing(ReferenceNotFoundException::program,
                        "PROGRAM_NOT_FOUND", "программа страхования " + ID + " не найдена"),
                missing(ReferenceNotFoundException::policy,
                        "POLICY_NOT_FOUND", "полис " + ID + " не найден"),
                missing(ReferenceNotFoundException::policyByExternalId,
                        "POLICY_NOT_FOUND", "полис с external_id " + ID + " не найден"),
                missing(ReferenceNotFoundException::policyByNumber,
                        "POLICY_NOT_FOUND", "в договоре страхования с external_id " + ID + " нет полиса с таким номером"),
                missing(ReferenceNotFoundException::insuranceContract,
                        "INSURANCE_CONTRACT_NOT_FOUND", "договор страхования " + ID + " не найден"),
                missing(ReferenceNotFoundException::insuranceContractByExternalId,
                        "INSURANCE_CONTRACT_NOT_FOUND", "договор страхования с external_id " + ID + " не найден"),
                missing(ReferenceNotFoundException::lpu,
                        "LPU_NOT_FOUND", "ЛПУ " + ID + " не найдено"));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void factory_namesReferenceInCode_andRecordInMessage(
            Function<UUID, ReferenceNotFoundException> factory, String code, String message) {

        ReferenceNotFoundException notFound = factory.apply(ID);

        assertThat(notFound.code()).isEqualTo(code);
        assertThat(notFound).hasMessage(message);
    }

    private static Arguments missing(
            Function<UUID, ReferenceNotFoundException> factory, String code, String message) {

        return Arguments.of(factory, code, message);
    }
}
