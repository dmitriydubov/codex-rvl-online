package ru.projectx.datareference.controller;

import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestController
@TestComponent
public class FailingController {

    public static final String FAILING = "/failing";
    public static final String NO_RESOURCE = "/no-resource";
    public static final String WITH_PARAMETER = "/with-parameter";
    public static final String READ_ONLY = "/read-only";
    public static final String WITH_ID = "/with-id/{id}";
    public static final String WITHOUT_ID_IN_TEMPLATE = "/without-id-in-template";

    private Exception failure = new IllegalStateException("отказ не задан");

    public void failWith(Exception next) {
        this.failure = next;
    }

    @GetMapping(FAILING)
    String fail() throws Exception {
        throw failure;
    }

    @GetMapping(NO_RESOURCE)
    String noResource() throws NoResourceFoundException {
        throw new NoResourceFoundException(HttpMethod.GET, NO_RESOURCE, "api/unknown");
    }

    @GetMapping(WITH_PARAMETER)
    String withParameter(@RequestParam String key) {
        return key;
    }

    @GetMapping(path = READ_ONLY, produces = MediaType.APPLICATION_JSON_VALUE)
    String readOnly() {
        return "{}";
    }

    @GetMapping(WITH_ID)
    String withId(@PathVariable UUID id) {
        return id.toString();
    }

    @GetMapping(WITHOUT_ID_IN_TEMPLATE)
    String withoutIdInTemplate(@PathVariable UUID id) {
        return id.toString();
    }
}
