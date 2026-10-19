package ru.projectx.datareference.service.rest;

import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.cache.ReferenceCaches;

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
