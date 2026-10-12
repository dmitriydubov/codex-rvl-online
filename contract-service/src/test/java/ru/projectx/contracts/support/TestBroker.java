package ru.projectx.contracts.support;

import java.net.URI;

/**
 * Откуда тестам взять RabbitMQ: контейнер Testcontainers, а без Docker — отдельный тестовый брокер из
 * {@code TEST_RABBITMQ_URL} (свойство {@value #URL_PROPERTY}, вид {@code amqp://логин:пароль@хост:порт/vhost}).
 * Без Docker и адреса тесты с брокером падают, а не пропускаются: пропущенный тест выглядит в отчёте как проверенный.
 */
public final class TestBroker {

    public static final String URL_PROPERTY = "test.rabbitmq.url";

    private static final String DEFAULT_VHOST = "/";

    private TestBroker() {
    }

    /** Адрес одного брокера для подстановки в настройки подключения. */
    public record Address(String host, int port, String virtualHost, String username, String password) {

        /** Адрес без логина, пароля, хоста или порта отклоняется с подсказкой формы; сам адрес в ошибку не идёт. */
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
