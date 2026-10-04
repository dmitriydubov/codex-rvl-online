package ru.projectx.datareference.controller;

import java.util.UUID;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Подключает строгое чтение UUID ко всем контроллерам сразу, поэтому сами контроллеры правило
 * не повторяют. Действие на все контроллеры приложения выбрано намеренно: правило одно для каждой
 * ручки, которая принимает UUID, в том числе для ручек, которые появятся позже.
 *
 * <p>Правило подключено как редактор типа UUID, а не как конвертер. В Spring Framework 7.0.8 метод
 * {@code TypeConverterDelegate#convertIfNecessary} сначала ищет редактор, назначенный для типа, и при
 * нём службу конвертации не вызывает. Если назначенного редактора нет и конвертер отказал, Spring
 * повторяет попытку стандартным редактором UUID, а тот читает неполную запись. Поэтому конвертер в
 * службе конвертации MVC неканоническую запись не остановил бы.
 *
 * <p>Редактор хранит прочитанное значение, но общим он не становится: Spring создаёт новый связыватель
 * на каждый аргумент запроса ({@code DefaultDataBinderFactory#createBinder}) и вызывает для него этот
 * метод. Общая служба конвертации приложения, через которую читаются настройки, при этом не меняется.
 */
@ControllerAdvice
public class UuidParameters {

    @InitBinder
    public void readUuidStrictly(WebDataBinder binder) {
        binder.registerCustomEditor(UUID.class, new CanonicalUuidEditor());
    }
}
