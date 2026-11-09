package ru.projectx.datareference.controller.request;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import ru.projectx.datareference.exception.RestOverloadException;
import ru.projectx.datareference.service.rest.RestReadAdmission;

@Component
public class ConcurrentRequestsInterceptor implements HandlerInterceptor {

    private final RestReadAdmission admission;

    public ConcurrentRequestsInterceptor(RestReadAdmission admission) {
        this.admission = admission;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!admission.tryEnter()) {
            throw new RestOverloadException(admission.limit());
        }
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception failure) {

        admission.leave();
    }
}
