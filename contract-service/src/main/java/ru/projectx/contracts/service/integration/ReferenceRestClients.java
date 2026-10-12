package ru.projectx.contracts.service.integration;

import java.net.http.HttpClient;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import ru.projectx.contracts.config.ReferenceProperties;
import ru.projectx.contracts.service.RequestIds;
import tools.jackson.databind.json.JsonMapper;

final class ReferenceRestClients {

    private ReferenceRestClients() {
    }

    static RestClient.Builder builder(ReferenceProperties settings, JsonMapper json) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(settings.connectTimeout()).build());
        requests.setReadTimeout(settings.readTimeout());
        return RestClient.builder()
                .baseUrl(settings.url())
                .requestFactory(requests)
                .configureMessageConverters(
                        converters -> converters.withJsonConverter(new JacksonJsonHttpMessageConverter(json)))
                .defaultStatusHandler(HttpStatusCode::isError, new ReferenceErrorHandler(new ReferenceErrorBodies(json)))
                .requestInitializer(ReferenceRestClients::traceRequest);
    }

    private static void traceRequest(ClientHttpRequest request) {
        request.getHeaders().set(RequestIds.HEADER, RequestIds.current());
    }
}
