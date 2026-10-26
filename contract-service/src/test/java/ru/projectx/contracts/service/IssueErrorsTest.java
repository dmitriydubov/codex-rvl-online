package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.IssueError;

class IssueErrorsTest {

    @Test
    void undeliveredLmPolicyRequest_isNamedByItsOwnCodeAndTheUskThatDidNotGetIt() {
        assertThat(IssueErrors.lmPolicyRequestUndelivered()).isEqualTo(new IssueError("LM_POLICY_REQUEST_UNDELIVERED",
                "запрос на выпуск полиса ЛМ не доставлен в 1С:УСК"));
    }

    @Test
    void undeliveredKmContractSubmission_isNamedByItsOwnCodeAndTheUskThatDidNotGetIt() {
        assertThat(IssueErrors.kmContractSubmissionUndelivered()).isEqualTo(new IssueError(
                "KM_CONTRACT_SUBMISSION_UNDELIVERED", "договор КМ не передан в 1С:УСК"));
    }

    @Test
    void missingProgram_isNamedByTheProgramRequiredCode() {
        assertThat(IssueErrors.programMissing()).isEqualTo(new IssueError("PROGRAM_REQUIRED",
                "у записи о выпуске нет программы страхования"));
    }

    @Test
    void tooLargeKmContractMessage_namesTheLimitAndTheNumberOfCertificates() {
        assertThat(IssueErrors.kmContractMessageTooLarge(1048576L, 3)).isEqualTo(new IssueError(
                "KM_CONTRACT_MESSAGE_TOO_LARGE", "сообщение о договоре КМ больше предела 1048576 байт, справок — 3"));
    }
}
