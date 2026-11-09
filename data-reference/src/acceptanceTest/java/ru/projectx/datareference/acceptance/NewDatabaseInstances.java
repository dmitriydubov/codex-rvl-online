package ru.projectx.datareference.acceptance;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@Component
public class NewDatabaseInstances {

    private static final Duration START_TIMEOUT = Duration.ofMinutes(3);
    private static final String STARTER_NAME = "new-database-instance-";
    private static final String LISTENERS_AUTO_STARTUP = "spring.rabbitmq.listener.simple.auto-startup";

    private final RabbitMQContainer broker;
    private final List<StartingInstance> starting = new ArrayList<>();

    public NewDatabaseInstances(RabbitMQContainer broker) {
        this.broker = broker;
    }

    public void startTogether(int count, NewDatabase database) {
        CountDownLatch sameMoment = new CountDownLatch(1);
        for (int instance = 0; instance < count; instance++) {
            int apiPort = ServiceApplication.freePort();
            String[] arguments = arguments(database, apiPort);
            CompletableFuture<ConfigurableApplicationContext> launched = new CompletableFuture<>();
            Thread.ofPlatform().name(STARTER_NAME + instance).start(() -> launch(sameMoment, arguments, launched));
            starting.add(new StartingInstance(apiPort, launched));
        }
        sameMoment.countDown();
    }

    public void awaitStart() {
        starting.forEach(instance -> instance.launched().copy()
                .orTimeout(START_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                .handle((context, failure) -> context)
                .join());
    }

    public boolean noneFinished() {
        return starting.stream().map(StartingInstance::launched).noneMatch(Future::isDone);
    }

    public long startedWithoutFailure() {
        return startedPorts().size();
    }

    public List<Integer> startedPorts() {
        return starting.stream()
                .filter(instance -> instance.launched().state() == Future.State.SUCCESS)
                .map(StartingInstance::apiPort)
                .toList();
    }

    public void stop() {
        starting.forEach(instance -> instance.launched().thenAccept(ConfigurableApplicationContext::close));
        starting.clear();
    }

    private static void launch(CountDownLatch sameMoment, String[] arguments,
            CompletableFuture<ConfigurableApplicationContext> launched) {
        try {
            sameMoment.await();
            launched.complete(ServiceApplication.withoutAcceptanceStand().run(arguments));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            launched.completeExceptionally(interrupted);
        } catch (RuntimeException failed) {
            launched.completeExceptionally(failed);
        }
    }

    private String[] arguments(NewDatabase database, int apiPort) {
        Map<String, Object> properties = ServiceApplication.properties(
                new ServiceApplication.DatabaseLogin(database.url(), database.username(), database.password()),
                broker, apiPort);
        properties.put(LISTENERS_AUTO_STARTUP, false);
        return ServiceApplication.arguments(properties);
    }

    private record StartingInstance(int apiPort, CompletableFuture<ConfigurableApplicationContext> launched) {
    }
}
