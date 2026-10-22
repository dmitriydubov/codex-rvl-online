package ru.projectx.contracts.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class ContentDigests {

    private static final String SHA_256 = "SHA-256";

    private ContentDigests() {
    }

    static String sha256(ContentSource content) {
        MessageDigest digest = sha256Digest();
        try (InputStream stream = new DigestInputStream(content.open(), digest)) {
            stream.transferTo(OutputStream.nullOutputStream());
        } catch (IOException failure) {
            throw new UncheckedIOException("файл справки не прочитан", failure);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("в JVM нет алгоритма " + SHA_256, missing);
        }
    }
}
