package ru.projectx.datareference.controller.openapi;

import java.util.List;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@TestComponent
public class FailureController {

    static final String PATH = "/failures/{index}";

    private final List<Exception> failures;

    FailureController(List<Exception> failures) {
        this.failures = List.copyOf(failures);
    }

    @GetMapping(PATH)
    public String fail(@PathVariable int index) throws Exception {
        throw failures.get(index);
    }
}
