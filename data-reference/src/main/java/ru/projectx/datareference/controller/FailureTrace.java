package ru.projectx.datareference.controller;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Описание непредвиденной ошибки для журнала: каждая причина в цепочке с местом, где она возникла, но
 * без текстов исключений. Обёртки Spring прячут настоящую причину, поэтому одного верхнего типа для
 * разбора мало. Текст исключения в журнал не идёт: в нём могут быть SQL и значения строки с
 * персональными данными.
 *
 * <p>Место — это класс, метод и строка первого кадра стека причины. Цепочка обрезается на десяти
 * причинах, потому что причины могут замкнуться в кольцо.
 */
final class FailureTrace {

    private static final int MAX_CAUSES = 10;
    private static final String CAUSE_SEPARATOR = " <- ";
    private static final String UNKNOWN_ORIGIN = "место неизвестно";

    private FailureTrace() {
    }

    static String of(Throwable failure) {
        return Stream.iterate(failure, Objects::nonNull, Throwable::getCause)
                .limit(MAX_CAUSES)
                .map(cause -> cause.getClass().getName() + " at " + origin(cause))
                .collect(Collectors.joining(CAUSE_SEPARATOR));
    }

    private static String origin(Throwable cause) {
        return Stream.of(cause.getStackTrace())
                .findFirst()
                .map(frame -> frame.getClassName() + "#" + frame.getMethodName() + ":" + frame.getLineNumber())
                .orElse(UNKNOWN_ORIGIN);
    }
}
