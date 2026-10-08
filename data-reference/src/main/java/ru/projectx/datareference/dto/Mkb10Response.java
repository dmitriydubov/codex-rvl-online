package ru.projectx.datareference.dto;

import java.util.List;

/** Коды МКБ-10: подсказка по строке или раскрытие перечня кодов. */
public record Mkb10Response(List<Mkb10Code> items) {
}
