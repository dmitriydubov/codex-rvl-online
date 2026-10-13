package ru.projectx.contracts.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatusDocument(UUID documentId, String docType, String kind, @Nullable String url) {
}
