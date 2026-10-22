package ru.projectx.contracts.controller;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.projectx.contracts.dto.PackageResponse;
import ru.projectx.contracts.service.packages.RequestPackageReads;

@InternalApi
public class RequestPackageController {

    private final RequestPackageReads packages;

    public RequestPackageController(RequestPackageReads packages) {
        this.packages = packages;
    }

    @GetMapping("/requests/{individual_request_id}/package")
    public PackageResponse packageOf(@PathVariable("individual_request_id") UUID individualRequestId) {
        return packages.packageOf(individualRequestId);
    }
}
