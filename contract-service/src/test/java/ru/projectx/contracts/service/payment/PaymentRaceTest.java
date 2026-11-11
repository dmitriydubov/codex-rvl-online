package ru.projectx.contracts.service.payment;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import ru.projectx.contracts.support.IntegrationTest;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@IntegrationTest
@TestPropertySource(properties = {
        "projectx.payment.enabled=true",
        "projectx.ordering.url=http://ordering.test",
        "projectx.ordering.product=rvl",
        "projectx.ordering.channel=rvl-lk",
        "projectx.ordering.retry.delay=0s",
        "projectx.ordering.token.uri=http://keycloak.test/token",
        "projectx.ordering.token.client-id=contracts-test",
        "projectx.ordering.token.client-secret=no-keycloak",
        "projectx.brokers.marketing.connection.host=marketing.test",
        "projectx.brokers.marketing.connection.username=contracts-test",
        "projectx.brokers.marketing.connection.password=no-broker",
        "projectx.brokers.marketing.listener-auto-startup=false",
        "projectx.brokers.marketing.document-fixes.exchange=rvl.exchange",
        "projectx.brokers.marketing.document-fixes.routing-key=rvl-contract-fixed",
        "projectx.brokers.marketing.order-events.queue=order.event.rvl.topic"})
@Import({PaymentRaceStand.Scripted.class, PaymentRaceStand.class})
@interface PaymentRaceTest {
}
