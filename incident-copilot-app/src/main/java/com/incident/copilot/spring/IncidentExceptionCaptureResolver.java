package com.incident.copilot.spring;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Observes exceptions thrown from MVC handlers and hands them to the recorder.
 * Returns {@code null} so the existing {@code @ControllerAdvice} chain still
 * produces the HTTP response — this resolver never resolves, it only records.
 */
public class IncidentExceptionCaptureResolver implements HandlerExceptionResolver, Ordered {

    private final IncidentSignalRecorder recorder;

    public IncidentExceptionCaptureResolver(IncidentSignalRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public ModelAndView resolveException(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Object handler,
                                         Exception ex) {
        recorder.capture(ex);
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
