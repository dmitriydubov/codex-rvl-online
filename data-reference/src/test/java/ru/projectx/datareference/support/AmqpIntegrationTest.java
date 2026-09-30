package ru.projectx.datareference.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * То же приложение, что и в {@link IntegrationTest}, но с настоящим брокером
 * и работающими слушателями очередей.
 *
 * <p>Отдельно от {@link IntegrationTest}, потому что брокер нужен ровно двум тестам,
 * а платят за него все: контейнер поднимается на контекст, и добавить его в общую
 * аннотацию значит замедлить весь прогон ради проверки, которая к остальным тестам
 * отношения не имеет.
 *
 * <p>Шедулер разбора и здесь усыплён: проверяется приём, а не то, что произойдёт
 * с принятым сырьём дальше.
 *
 * <p><b>TLS выключен явно, и это не мелочь.</b> Адрес, порт и учётные данные тесту
 * подставляет {@code @ServiceConnection} контейнера, а вот {@code ssl.enabled} берётся
 * только из свойств — то есть из {@code application.yml}, где оно читает переменную
 * окружения контура. На машине, где выставлен {@code RABBITMQ_SSL_ENABLED=true} для
 * подключения к 1С:УСК, тест шёл бы по TLS в открытый порт контейнера и падал бы
 * на рукопожатии. Свой брокер тест приносит с собой — значит и способ подключения
 * к нему задаёт сам, а не окружение запускающего.
 *
 * <p>Слушатели включены здесь явно по той же причине: умолчание в {@code application.yml}
 * их не стартует, потому что локальный брокер — не брокер 1С. Этому тесту слушатели
 * и нужны, а брокер у него свой.
 *
 * <p>Без Docker тест требует обоих внешних адресов, {@code TEST_RABBITMQ_URL} и
 * {@code TEST_POSTGRES_URL}; иначе он падает, а не пропускается, см. {@link TestBroker}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "projectx.import.poll-interval=1h",
        "spring.rabbitmq.ssl.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=true"})
@Import({PostgresTestcontainer.class, RabbitTestcontainer.class, FixedClockConfig.class, TestSupportConfig.class})
public @interface AmqpIntegrationTest {
}
