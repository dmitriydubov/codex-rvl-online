package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.issuance.IssueError;

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
    void kmContractSubmissionNotPrepared_isNamedByItsOwnCodeWithoutTheFailureDetail() {
        assertThat(IssueErrors.kmContractSubmissionPreparationFailed()).isEqualTo(new IssueError(
                "KM_CONTRACT_SUBMISSION_PREPARATION_FAILED",
                "передача договора КМ в 1С:УСК не подготовлена: внутренняя ошибка сервиса Договоров"));
    }

    @Test
    void lmPolicyRequestNotPrepared_isNamedByItsOwnCodeWithoutTheFailureDetail() {
        assertThat(IssueErrors.lmPolicyRequestPreparationFailed()).isEqualTo(new IssueError(
                "LM_POLICY_REQUEST_PREPARATION_FAILED",
                "запрос на выпуск полиса ЛМ в 1С:УСК не подготовлен: внутренняя ошибка сервиса Договоров"));
    }

    @Test
    void paymentAfterCancellation_isNamedByItsOwnCodeAndWhatHappened() {
        assertThat(IssueErrors.kmContractPaidAfterCancellation()).isEqualTo(new IssueError(
                "KM_CONTRACT_PAID_AFTER_CANCELLATION", "оплата поступила после прекращения работы по договору"));
    }

    @Test
    void kmContractSubmissionErrors_areTheUndeliveredAndTheNotPreparedSubmission() {
        assertThat(IssueErrors.kmContractSubmission()).isEqualTo(new IssuanceErrors(
                IssueErrors.kmContractSubmissionUndelivered(), IssueErrors.kmContractSubmissionPreparationFailed()));
    }

    @Test
    void lmPolicyRequestErrors_areTheUndeliveredAndTheNotPreparedRequest() {
        assertThat(IssueErrors.lmPolicyRequest()).isEqualTo(new IssuanceErrors(
                IssueErrors.lmPolicyRequestUndelivered(), IssueErrors.lmPolicyRequestPreparationFailed()));
    }
}
