package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import java.util.Map;
import java.util.Optional;
import ru.projectx.contracts.config.AttachmentProperties;
import ru.projectx.contracts.config.StorageProperties;

public class CertificateStorageSteps {

    private static final String SEPARATOR = "/";

    private final StandStorage storage;
    private final StorageProperties settings;
    private final AttachmentProperties attachments;
    private final CertificateRecords records;
    private final AttachmentCalls calls;

    private final Map<String, Runnable> outages;

    public CertificateStorageSteps(StandStorage storage, StorageProperties settings, AttachmentProperties attachments,
            CertificateRecords records, AttachmentCalls calls) {
        this.storage = storage;
        this.settings = settings;
        this.attachments = attachments;
        this.records = records;
        this.calls = calls;
        this.outages = Map.of("остановлено", storage::stop, "не отвечает", storage::pause);
    }

    @After
    public void recoverStorage() {
        storage.recover();
    }

    @Дано("хранилище справок Договоров — бакет {string}, base-path {string}, предельный размер файла {long} байт")
    public void storageSettings(String bucket, String basePath, long maxFileSize) throws Exception {
        assertThat(settings.bucket()).as("бакет в настройках стенда").isEqualTo(bucket);
        assertThat(settings.basePath()).as("base-path в настройках стенда").isEqualTo(basePath);
        assertThat(attachments.maxFileSize().toBytes()).as("предельный размер файла").isEqualTo(maxFileSize);
        storage.recreateBucket(bucket);
    }

    @Дано("хранилище справок недоступно: {string}")
    public void storageDown(String outage) {
        Optional.ofNullable(outages.get(outage))
                .orElseThrow(() -> new AssertionError("недоступности «" + outage + "» нет в перечне шага"))
                .run();
    }

    @Когда("хранилище справок снова доступно")
    public void storageBack() throws Exception {
        storage.recover();
        assertThat(storage.bucketExists(settings.bucket())).as("бакет после восстановления").isTrue();
    }

    @Дано("в хранилище справок нет бакета Договоров")
    public void bucketRemoved() throws Exception {
        storage.removeBucket(settings.bucket());
    }

    @Тогда("в хранилище справок бакета Договоров по-прежнему нет")
    public void bucketStillMissing() throws Exception {
        assertThat(storage.bucketExists(settings.bucket())).as("бакет %s", settings.bucket()).isFalse();
    }

    @Тогда("в хранилище справок за сценарий появилось объектов — {int}")
    public void objectCount(int count) throws Exception {
        assertThat(storage.keys(settings.bucket(), settings.basePath() + SEPARATOR)).as("объекты бакета").hasSize(count);
    }

    @Тогда("содержимое объекта справки по вложению {string} в хранилище совпадает с отправленным")
    public void objectContent(String attachment) throws Exception {
        String key = records.byAttachment(attachment).key();
        assertThat(storage.object(settings.bucket(), key)).as("объект %s", key)
                .isEqualTo(calls.acceptedContent(attachment));
    }

    @Тогда("папки объектов справок по вложениям {string} и {string} разные")
    public void separateFolders(String first, String second) {
        assertThat(folder(records.byAttachment(first).key())).isNotEqualTo(folder(records.byAttachment(second).key()));
    }

    private String folder(String key) {
        String[] segments = key.substring(settings.basePath().length() + 1).split(SEPARATOR);
        assertThat(segments).as("сегменты ключа %s", key).hasSize(2);
        return segments[0];
    }
}
