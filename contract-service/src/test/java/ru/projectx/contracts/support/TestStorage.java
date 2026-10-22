package ru.projectx.contracts.support;

import java.util.Optional;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;

public final class TestStorage {

    public static final String IMAGE_VARIABLE = "TEST_MINIO_IMAGE";
    public static final String DEFAULT_IMAGE =
            "chainguard/minio@sha256:ad134b2dd572ae0d989d6807c3e45516cc75a34883a8021db2ff04be5d1d496a";

    private static final String MINIO = "minio/minio";

    private TestStorage() {
    }

    public static MinIOContainer container() {
        String image = Optional.ofNullable(System.getenv(IMAGE_VARIABLE)).filter(name -> !name.isBlank())
                .orElse(DEFAULT_IMAGE);
        return new MinIOContainer(DockerImageName.parse(image).asCompatibleSubstituteFor(MINIO));
    }
}
