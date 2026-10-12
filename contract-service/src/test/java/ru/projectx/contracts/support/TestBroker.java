package ru.projectx.contracts.support;

import java.net.URI;

public final class TestBroker {

    public static final String URL_PROPERTY = "test.rabbitmq.url";

    private static final String DEFAULT_VHOST = "/";

    private TestBroker() {
    }

    public record Address(String host, int port, String virtualHost, String username, String password) {

        public static Address parse(String url) {
            URI uri = URI.create(url);
            String[] credentials = uri.getUserInfo() == null ? new String[0] : uri.getUserInfo().split(":", 2);
            if (credentials.length != 2 || uri.getHost() == null || uri.getPort() < 0) {
                throw new IllegalArgumentException(
                        "ожидается адрес вида amqp://логин:пароль@хост:порт/vhost в " + URL_PROPERTY);
            }
            String path = uri.getPath();
            String virtualHost = path.length() > 1 ? path.substring(1) : DEFAULT_VHOST;
            return new Address(uri.getHost(), uri.getPort(), virtualHost, credentials[0], credentials[1]);
        }

        public Address withVirtualHost(String otherVirtualHost) {
            return new Address(host, port, otherVirtualHost, username, password);
        }
    }
}
