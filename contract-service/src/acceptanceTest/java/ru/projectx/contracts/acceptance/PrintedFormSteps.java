package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Тогда;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.SoftAssertions;

public class PrintedFormSteps {

    private final PrintedFormRecords forms;
    private final IssuanceRecords records;

    public PrintedFormSteps(PrintedFormRecords forms, IssuanceRecords records) {
        this.forms = forms;
        this.records = records;
    }

    @Тогда("печатные формы полиса ЛМ участника {string} заявки {string}:")
    public void printedForms(String participantId, String requestId, List<Map<String, String>> expected) {
        List<PrintedFormRecords.Form> actual = forms.ofPolicy(records.latestLmPolicyId(participantId, requestId));
        assertThat(actual).as("печатные формы полиса ЛМ участника %s", participantId).hasSameSizeAs(expected);
        SoftAssertions softly = new SoftAssertions();
        for (int index = 0; index < expected.size(); index++) {
            form(softly, expected.get(index), actual.get(index));
        }
        softly.assertAll();
    }

    @Тогда("у полиса ЛМ участника {string} заявки {string} нет печатных форм")
    public void noPrintedForms(String participantId, String requestId) {
        assertThat(forms.ofPolicy(records.latestLmPolicyId(participantId, requestId)))
                .as("печатные формы полиса ЛМ участника %s", participantId).isEmpty();
    }

    private static void form(SoftAssertions softly, Map<String, String> expected, PrintedFormRecords.Form actual) {
        softly.assertThat(actual.kind()).as("kind").isEqualTo(StepTables.required(expected, "kind"));
        softly.assertThat(actual.docType()).as("doc_type").isEqualTo(StepTables.required(expected, "doc_type"));
        softly.assertThat(actual.url()).as("url").isEqualTo(StepTables.required(expected, "url"));
        softly.assertThat(Optional.ofNullable(actual.size()).map(String::valueOf).orElse(null)).as("размер")
                .isEqualTo(StepTables.required(expected, "размер"));
    }
}
