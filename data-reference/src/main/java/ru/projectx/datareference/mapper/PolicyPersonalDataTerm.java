package ru.projectx.datareference.mapper;

import java.time.Instant;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.config.ReferenceApiProperties;
import ru.projectx.datareference.entity.PersonalDataCutoff;

/**
 * Срок хранения персональных данных в полисах после окончания или перевода в архив. Граница нужна разбору полиса,
 * стиранию и метрике, поэтому считается в одном месте.
 */
@Component
public class PolicyPersonalDataTerm {

    private final ImportProperties properties;
    private final ReferenceApiProperties api;

    public PolicyPersonalDataTerm(ImportProperties properties, ReferenceApiProperties api) {
        this.properties = properties;
        this.api = api;
    }

    public PersonalDataCutoff cutoffAt(Instant now) {
        return PersonalDataCutoff.at(now, properties.policyPersonalDataGrace(), api.zone());
    }
}
