package ru.projectx.contracts.service.integration.ordering;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

final class OrderingRestClients {

    private OrderingRestClients() {
    }

    static JdkClientHttpRequestFactory requests(Duration connectTimeout, Duration readTimeout) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        requests.setReadTimeout(readTimeout);
        return requests;
    }
}
