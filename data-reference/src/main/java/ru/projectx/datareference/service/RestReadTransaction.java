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
 * Транзакция REST-чтения: только чтение и короткий лимит в целых секундах, после которого ответ 503. Общий таймаут
 * пула рассчитан на разбор сырья, а зависшее чтение держит нужное другим соединение. Лимит действует только на
 * транзакцию, которую метод начинает сам, и защищает от зависания, а не задаёт точный срок всему запросу.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Transactional(readOnly = true, timeoutString = "${projectx.api.read-timeout-seconds}")
public @interface RestReadTransaction {

    /** Чтению из нескольких запросов, которые должны сойтись в один ответ, нужен один снимок базы. */
    @AliasFor(annotation = Transactional.class)
    Isolation isolation() default Isolation.DEFAULT;
}
