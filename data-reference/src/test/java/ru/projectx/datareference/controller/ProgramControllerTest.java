package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.dto.ProgramResponse;
import ru.projectx.datareference.service.ProgramReads;

@ExtendWith(MockitoExtension.class)
class ProgramControllerTest {

    private static final String PROGRAMS = InternalApi.BASE_PATH + "/programs/{id}";
    private static final UUID PROGRAM_ID = UUID.fromString("0a9e0000-0000-4000-8000-000000000001");
    private static final UUID PROGRAM_EXTERNAL_ID = UUID.fromString("0a9e0000-0000-4000-8000-000000000002");

    @Mock
    private ProgramReads programs;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = StandaloneMvc.of(new ProgramController(programs));
    }

    @Test
    void programById_returnsProgramFoundByService_asJson() {
        when(programs.byId(PROGRAM_ID)).thenReturn(
                new ProgramResponse(PROGRAM_ID, PROGRAM_EXTERNAL_ID, "Программа РВЛ", null, false, List.of()));

        MvcTestResult result = mvc.get().uri(PROGRAMS, PROGRAM_ID).exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(PROGRAM_ID.toString());
    }

    @Test
    void programById_withUnreadableId_isValidationError_withoutAskingService() {
        MvcTestResult result = mvc.get().uri(PROGRAMS, "not-a-uuid").exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("id");
        verifyNoInteractions(programs);
    }

    @Test
    void programById_acceptingOnlyXml_isNotAcceptable_withoutAskingService() {
        MvcTestResult result = mvc.get().uri(PROGRAMS, PROGRAM_ID).accept(MediaType.APPLICATION_XML).exchange();

        assertThat(result).hasStatus(406);
        verifyNoInteractions(programs);
    }
}
