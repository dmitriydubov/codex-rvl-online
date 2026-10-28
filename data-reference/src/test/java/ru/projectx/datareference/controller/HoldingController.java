package ru.projectx.datareference.controller;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HoldingController {

    public static final String HELD = InternalApi.BASE_PATH + "/held";

    private final AtomicInteger arrived = new AtomicInteger();
    private volatile CountDownLatch gate = new CountDownLatch(0);
    private volatile boolean failing;

    public void hold() {
        arrived.set(0);
        failing = false;
        gate = new CountDownLatch(1);
    }

    public void release() {
        gate.countDown();
    }

    public void releaseWithFailure() {
        failing = true;
        gate.countDown();
    }

    public int arrived() {
        return arrived.get();
    }

    @GetMapping(path = HELD, produces = MediaType.APPLICATION_JSON_VALUE)
    String held() throws InterruptedException {
        arrived.incrementAndGet();
        gate.await();
        if (failing) {
            throw new IllegalStateException("стенд: удержанный запрос закончился отказом");
        }
        return "{}";
    }
}
