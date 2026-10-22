package ru.projectx.contracts.service.packages;

import java.util.List;
import ru.projectx.contracts.dto.PackageItem.PackageDocument;
import ru.projectx.contracts.entity.PrintedForm;

final class PackageDocuments {

    private PackageDocuments() {
    }

    static List<PackageDocument> of(List<PrintedForm> forms) {
        return forms.stream()
                .map(form -> new PackageDocument(form.id(), form.docType().name(), form.kind().name(), form.version(),
                        form.uskFileUrl().orElse(null)))
                .toList();
    }
}
