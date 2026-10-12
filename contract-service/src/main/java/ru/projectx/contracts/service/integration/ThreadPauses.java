package ru.projectx.contracts.service.integration;

import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class ThreadPauses implements Pauses {

    @Override
    public boolean hold(Duration duration) {
        try {
            Thread.sleep(duration);
            return true;
        }
        catch (InterruptedException shuttingDown) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
