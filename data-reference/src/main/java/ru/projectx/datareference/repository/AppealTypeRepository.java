package ru.projectx.datareference.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.AppealType;

/**
 * Upsert идёт по {@code external_id} — GUID объекта в 1С, а не по нашему первичному
 * ключу: другого устойчивого идентификатора у сообщения источника нет.
 */
@Repository
public interface AppealTypeRepository extends JpaRepository<AppealType, UUID> {

    Optional<AppealType> findByExternalId(UUID externalId);

    /**
     * Действующие виды обращения по наименованиям без учёта регистра и краевых пробелов —
     * так R-01 и R-03 находят виды обращения РВЛ. {@code names} уже в нижнем регистре
     * и без краевых пробелов: нормализует вызывающий, здесь только колонка.
     */
    @Query("select a from AppealType a where a.archive = false and lower(trim(a.name)) in :names")
    List<AppealType> findActiveByNormalizedNameIn(Collection<String> names);
}
