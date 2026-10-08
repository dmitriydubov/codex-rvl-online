package ru.projectx.datareference.service;

import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.ReferenceCaches;

/**
 * Ключ кэша для ответов «действующие на сегодня»: сегодняшний день контура, затем аргументы метода. Так в полночь
 * по Москве ответ считается заново, не дожидаясь конца срока жизни кэша.
 */
@Component(ReferenceCaches.TODAY_KEY)
public class TodayKeyGenerator implements KeyGenerator {

    private final ReferenceCalendar calendar;

    public TodayKeyGenerator(ReferenceCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public Object generate(Object target, Method method, Object... params) {
        return new SimpleKey(Stream.concat(Stream.of(calendar.today()), Stream.of(params)).toArray());
    }
}
