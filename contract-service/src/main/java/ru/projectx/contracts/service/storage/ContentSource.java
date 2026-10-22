package ru.projectx.contracts.service.storage;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
public interface ContentSource {

    InputStream open() throws IOException;
}
