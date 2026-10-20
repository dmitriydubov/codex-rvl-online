package ru.projectx.datareference.service.warmup;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public class LocalApiClient implements AutoCloseable {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID = "startup-warmup";

    private final URI address;
    private final HttpClient http;

    LocalApiClient(URI address, Duration connectTimeout) {
        this.address = address;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .proxy(HttpClient.Builder.NO_PROXY)
                .connectTimeout(connectTimeout)
                .build();
    }

    public URI address() {
        return address;
    }

    public int read(String path, Duration timeout) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(address.resolve(path))
                .timeout(timeout)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(REQUEST_ID_HEADER, REQUEST_ID)
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Override
    public void close() {
        http.close();
    }
}
