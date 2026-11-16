package ru.projectx.contracts.acceptance;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

final class StubScript {

    private final List<String> answers;
    private int next;

    StubScript(String answers) {
        this.answers = Arrays.stream(answers.split(";")).map(String::strip).toList();
    }

    Optional<String> firstUnknown(Pattern known) {
        return answers.stream().filter(answer -> !known.matcher(answer).matches()).findFirst();
    }

    synchronized String next() {
        String answer = answers.get(Math.min(next, answers.size() - 1));
        next++;
        return answer;
    }
}
