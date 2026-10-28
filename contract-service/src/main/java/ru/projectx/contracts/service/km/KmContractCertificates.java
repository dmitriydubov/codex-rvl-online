package ru.projectx.contracts.service.km;

import java.util.List;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.CertificateKeyMissingException;
import ru.projectx.contracts.exception.CertificateMissingException;
import ru.projectx.contracts.exception.StorageUnavailableException;
import ru.projectx.contracts.service.storage.AttachmentMetrics;
import ru.projectx.contracts.service.storage.CertificateStore;
import ru.projectx.contracts.service.usk.Prerequisites;
import ru.projectx.contracts.service.usk.SubmissionPrerequisites;

@Component
public class KmContractCertificates implements SubmissionPrerequisites<Contract> {

    static final String STORAGE_UNAVAILABLE = "хранилище справок недоступно, передача отложена до следующего прохода";
    static final String CERTIFICATE_MISSING =
            "передача договора КМ не подготовлена: файла справки нет в хранилище, договор переведён в ERROR";

    private final InsuredCertificates certificates;
    private final CertificateStore store;
    private final AttachmentMetrics metrics;

    public KmContractCertificates(InsuredCertificates certificates, CertificateStore store, AttachmentMetrics metrics) {
        this.certificates = certificates;
        this.store = store;
        this.metrics = metrics;
    }

    @Override
    public Prerequisites check(Contract contract) {
        try {
            return verified(keys(contract));
        } catch (CertificateKeyMissingException broken) {
            return new Prerequisites.Broken(KmContractTexts.WORDING.preparationFailed(), broken);
        }
    }

    private Prerequisites verified(List<String> keys) {
        try {
            keys.forEach(store::verifyPresent);
            return new Prerequisites.Met();
        } catch (StorageUnavailableException unavailable) {
            metrics.countStorageUnavailable();
            return new Prerequisites.Postponed(STORAGE_UNAVAILABLE);
        } catch (CertificateMissingException missing) {
            return new Prerequisites.Broken(CERTIFICATE_MISSING, missing);
        }
    }

    private List<String> keys(Contract contract) {
        return certificates.of(contract).stream()
                .map(SubmittedCertificate::file)
                .map(StoredFile::key)
                .toList();
    }
}
