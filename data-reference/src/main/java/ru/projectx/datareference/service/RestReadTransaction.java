package ru.projectx.datareference.service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Transactional(readOnly = true, timeoutString = "${projectx.api.read-timeout-seconds}")
public @interface RestReadTransaction {

    @AliasFor(annotation = Transactional.class)
    Isolation isolation() default Isolation.DEFAULT;
}
