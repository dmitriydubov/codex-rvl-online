package ru.projectx.contracts.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.WebRequest;
import ru.projectx.contracts.exception.AttachmentBusyException;
import ru.projectx.contracts.exception.AttachmentLockedException;
import ru.projectx.contracts.exception.AttachmentNotFoundException;
import ru.projectx.contracts.exception.StorageUnavailableException;
import ru.projectx.contracts.service.RequestIds;
import ru.projectx.contracts.service.storage.AttachmentContent;
import ru.projectx.contracts.service.storage.AttachmentContentReads;
import ru.projectx.contracts.service.storage.AttachmentDeletes;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class AttachmentContentControllerTest {

    private static final String PATH = "/internal/v1/attachments/{id}";
    private static final String CONTENT = PATH + "/content";
    private static final String REQUEST_ID = "attachment-content-test";
    private static final UUID ATTACHMENT = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
    private static final byte[] BYTES = {(byte) 0xff, (byte) 0xd8, 0, 22};
    private static final String ETAG = "\"abc0123\"";

    private final AttachmentContentReads reads = mock(AttachmentContentReads.class);
    private final AttachmentDeletes deletes = mock(AttachmentDeletes.class);
    private final AttachmentContentController controller = new AttachmentContentController(reads, deletes);
    private final AtomicBoolean closed = new AtomicBoolean();

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new DomainErrorAdvice())
            .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()))
            .addFilters(new RequestIdFilter())
            .build();

    @Test
    void download_returnsTheBytesAndHeadersAndClosesTheStream() throws Exception {
        prepareContent();

        mvc.perform(get(CONTENT, ATTACHMENT).header(RequestIds.HEADER, REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(content().bytes(BYTES))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, BYTES.length))
                .andExpect(header().string(HttpHeaders.ETAG, ETAG))
                .andExpect(header().string(RequestIds.HEADER, REQUEST_ID));

        assertThat(closed).isTrue();
    }

    @Test
    void matchingConditionalRequest_closesTheStreamAndReturnsAnEmpty304() throws Exception {
        prepareContent();

        mvc.perform(get(CONTENT, ATTACHMENT).header(HttpHeaders.IF_NONE_MATCH, ETAG))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, ETAG))
                .andExpect(content().bytes(new byte[0]));

        assertThat(closed).isTrue();
    }

    @Test
    void failedWrite_closesTheStreamAndKeepsRawIoDetailsOutOfTheException() throws Exception {
        prepareContent();
        var response = mock(HttpServletResponse.class);
        var output = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(output);
        doThrow(new IOException("private/test-object.jpg")).when(output).write(any(byte[].class), anyInt(), anyInt());

        assertThatThrownBy(() -> controller.download(ATTACHMENT.toString(), mock(WebRequest.class), response))
                .isInstanceOf(IOException.class).hasMessageNotContaining("private").hasNoCause();

        assertThat(closed).isTrue();
    }

    @Test
    void invalidIds_areFieldErrorsBeforeReachingTheService() throws Exception {
        mvc.perform(get(CONTENT, "invalid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.length()").value(1))
                .andExpect(jsonPath("$.error.details[0].field").value("attachment_id"));
        mvc.perform(delete(PATH, "1-1-1-1-1")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("attachment_id"));

        verifyNoInteractions(reads, deletes);
    }

    @Test
    void downloadMapsMissingAttachmentsAndUnavailableStorage() throws Exception {
        when(reads.open(ATTACHMENT)).thenThrow(new AttachmentNotFoundException(), new StorageUnavailableException());

        mvc.perform(get(CONTENT, ATTACHMENT)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ATTACHMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.details.length()").value(0));
        mvc.perform(get(CONTENT, ATTACHMENT)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("STORAGE_UNAVAILABLE"));
    }

    @Test
    void deletionAnswers204OrMapsTheSpecificRefusal() throws Exception {
        doNothing().doThrow(new AttachmentLockedException(), new AttachmentBusyException(),
                new StorageUnavailableException()).when(deletes).delete(ATTACHMENT);

        mvc.perform(delete(PATH, ATTACHMENT).header(RequestIds.HEADER, REQUEST_ID)).andExpect(status().isNoContent())
                .andExpect(content().bytes(new byte[0])).andExpect(header().string(RequestIds.HEADER, REQUEST_ID));
        mvc.perform(delete(PATH, ATTACHMENT)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ATTACHMENT_LOCKED"));
        mvc.perform(delete(PATH, ATTACHMENT)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"));
        mvc.perform(delete(PATH, ATTACHMENT)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("STORAGE_UNAVAILABLE"));
        verify(deletes, times(4)).delete(ATTACHMENT);
    }

    private void prepareContent() {
        var bytes = new ByteArrayInputStream(BYTES) {
            @Override
            public void close() {
                closed.set(true);
            }
        };
        when(reads.open(ATTACHMENT)).thenReturn(new AttachmentContent(bytes, "image/jpeg", BYTES.length, "abc0123"));
    }
}
