package ru.projectx.datareference.service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Транзакция REST-чтения справочников: только чтение и короткий лимит времени из свойства
 * {@code projectx.api.read-timeout-seconds}. Чтение, не уложившееся в лимит, обрывается и
 * отвечает 503, а потребитель повторяет запрос.
 *
 * <p>Зачем отдельный лимит: общий {@code statement_timeout} пула (30 секунд) рассчитан на разбор
 * сырья из 1С. Потребители REST ждут ответа куда меньше, а зависшее чтение держит соединение пула,
 * которое нужно и слушателю очередей, и планировщикам.
 *
 * <p>Как лимит действует на самом деле. Spring передаёт его Hibernate как таймаут транзакции, а
 * Hibernate ставит таймаут на каждый оператор JDBC отдельно, целыми секундами, двумя способами.
 * Запросы репозиториев получают остаток лимита в миллисекундах и округляют его до ближайшей секунды:
 * остаток 2,6 секунды даёт 3 секунды, а остаток меньше половины секунды даёт ноль, то есть оператор
 * идёт без таймаута JDBC и его сторожат только {@code lock_timeout} и {@code statement_timeout} сеанса.
 * Чтение по ключу и ленивая догрузка связей получают остаток, округлённый вниз, но не меньше одной
 * секунды. Когда остатка уже нет, следующий оператор не выполняется вовсе: Hibernate сообщает об
 * истёкшем лимите транзакции. Поэтому лимит — защита от зависания в пределах секунды-двух, а не
 * точный срок на весь запрос: чтение из нескольких операторов может занять чуть больше лимита.
 *
 * <p>Лимит действует только на транзакцию, которую метод начинает сам. Вызов из уже открытой
 * транзакции живёт по её лимиту. Значение — целые секунды: {@code timeoutString} разбирается как
 * {@code int} после подстановки свойства.
 *
 * <p>Уровень изоляции по умолчанию — базы. Чтению из нескольких запросов, которые должны сойтись в один ответ, нужен
 * {@link Isolation#REPEATABLE_READ}: в PostgreSQL тогда все запросы транзакции видят один снимок, а не каждый свой.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Transactional(readOnly = true, timeoutString = "${projectx.api.read-timeout-seconds}")
public @interface RestReadTransaction {

    /** Уровень изоляции транзакции чтения. */
    @AliasFor(annotation = Transactional.class)
    Isolation isolation() default Isolation.DEFAULT;
}
