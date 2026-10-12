package ru.projectx.contracts.service.integration;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Шаблон публикации одного брокера. Каждое сообщение уходит как mandatory: немаршрутизируемое брокер возвращает
 * издателю, и публикатор видит возврат в {@code CorrelationData.getReturned()} ещё до подтверждения. Для привязки
 * возврата к сообщению у {@code CorrelationData} должен быть идентификатор.
 */
final class BrokerTemplates {

    private BrokerTemplates() {
    }

    static RabbitTemplate create(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        return template;
    }
}
