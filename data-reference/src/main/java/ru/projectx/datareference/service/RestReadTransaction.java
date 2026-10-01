package ru.projectx.datareference.service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.transaction.annotation.Transactional;

/**
 * Транзакция REST-чтения справочников: только чтение и короткий лимит времени
 * {@code projectx.api.read-timeout-seconds} (решение 4 владельца по PR #15, Ш41).
 *
 * <p>Общий {@code statement_timeout} пула (30 с) рассчитан на разбор сырья; потребители REST
 * ждут ответа меньше, а зависшее чтение держит соединение пула, нужное и слушателю,
 * и планировщикам. Чтение, не уложившееся в лимит, обрывается и отвечает 503: Spring Framework
 * 7.0.8 передаёт таймаут транзакции в Hibernate ({@code Transaction.setTimeout}), а тот —
 * в {@code Statement.setQueryTimeout} каждого оператора остатком в целых секундах, не меньше
 * одной (Hibernate 7.2.19, {@code JdbcCoordinatorImpl}).
 *
 * <p>Таймаут действует только на транзакцию, которую метод начинает сам ({@code REQUIRED}):
 * вызов из уже открытой транзакции живёт по её лимиту. Значение — целые секунды:
 * {@code timeoutString} разбирается как {@code int} после подстановки свойства.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Transactional(readOnly = true, timeoutString = "${projectx.api.read-timeout-seconds}")
public @interface RestReadTransaction {
}
