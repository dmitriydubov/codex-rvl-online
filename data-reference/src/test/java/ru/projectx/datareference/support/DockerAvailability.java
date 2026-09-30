package ru.projectx.datareference.support;

import org.testcontainers.DockerClientFactory;

/**
 * Есть ли на этой машине Docker.
 *
 * <p>Нужно, чтобы тесты на Testcontainers пропускались, а не падали там, где демона нет:
 * в песочнице, на машине без прав или в конвейере без docker-in-docker. Красная сборка
 * по причине «здесь нет Docker» ничего не сообщает о коде, но приучает не смотреть
 * на её результат.
 *
 * <p>Проверка кэшируется: {@code isDockerAvailable()} пытается открыть соединение,
 * и при отсутствии демона это несколько секунд ожидания. Умножать их на число тестовых
 * классов незачем — ответ за время прогона не меняется.
 */
public final class DockerAvailability {

    private static final boolean AVAILABLE = probe();

    public static boolean isDockerAvailable() {
        return AVAILABLE;
    }

    private static boolean probe() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        }
        catch (RuntimeException dockerNotReachable) {
            return false;
        }
    }

    private DockerAvailability() {
    }
}
