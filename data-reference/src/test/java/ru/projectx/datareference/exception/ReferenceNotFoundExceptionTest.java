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
                missing(MissingReference.PROGRAM::notFound,
                        "PROGRAM_NOT_FOUND", "программа страхования " + ID + " не найдена"),
                missing(MissingReference.POLICY::notFound,
                        "POLICY_NOT_FOUND", "полис " + ID + " не найден"),
                missing(MissingReference.POLICY_BY_EXTERNAL_ID::notFound,
                        "POLICY_NOT_FOUND", "полис с external_id " + ID + " не найден"),
                missing(MissingReference.POLICY_BY_NUMBER::notFound,
                        "POLICY_NOT_FOUND", "в договоре страхования с external_id " + ID + " нет полиса с таким номером"),
                missing(MissingReference.INSURANCE_CONTRACT::notFound,
                        "INSURANCE_CONTRACT_NOT_FOUND", "договор страхования " + ID + " не найден"),
                missing(MissingReference.INSURANCE_CONTRACT_BY_EXTERNAL_ID::notFound,
                        "INSURANCE_CONTRACT_NOT_FOUND", "договор страхования с external_id " + ID + " не найден"),
                missing(MissingReference.ACTIVE_INSURANCE_CONTRACT_OF_INSURER::notFound,
                        "INSURANCE_CONTRACT_NOT_FOUND", "у страхователя " + ID + " нет действующего договора страхования"),
                missing(MissingReference.LPU::notFound,
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
