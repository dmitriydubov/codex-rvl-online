package ru.projectx.datareference.controller;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import ru.projectx.datareference.controller.request.ConcurrentRequestsInterceptor;

@Component
public class InternalApiInterceptors implements WebMvcConfigurer {

    static final String EVERY_INTERNAL_API_PATH = InternalApi.BASE_PATH + "/**";

    private final ConcurrentRequestsInterceptor concurrentRequests;

    public InternalApiInterceptors(ConcurrentRequestsInterceptor concurrentRequests) {
        this.concurrentRequests = concurrentRequests;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(concurrentRequests).addPathPatterns(EVERY_INTERNAL_API_PATH);
    }
}
