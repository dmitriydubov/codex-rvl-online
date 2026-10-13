package ru.projectx.contracts.acceptance;

import com.rabbitmq.client.AMQP;

interface StandQueue {

    String alias();

    String name();

    AMQP.Queue.DeclareOk state();

    void purge();
}
