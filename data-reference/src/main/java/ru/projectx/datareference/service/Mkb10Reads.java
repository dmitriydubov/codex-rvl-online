package ru.projectx.datareference.service;

import java.util.List;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.dto.Mkb10Response;

public interface Mkb10Reads {

    Mkb10Response search(String query, @Nullable Integer limit);

    Mkb10Response byCodes(List<String> codes);
}
