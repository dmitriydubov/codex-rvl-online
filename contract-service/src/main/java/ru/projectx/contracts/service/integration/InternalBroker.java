package ru.projectx.contracts.service.integration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Бины межсервисного брокера контура: подключение, шаблон публикации и фабрика контейнеров слушателей. Без
 * квалификатора у бина два кандидата, и Spring не даст выбрать брокер молча.
 */
@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface InternalBroker {
}
