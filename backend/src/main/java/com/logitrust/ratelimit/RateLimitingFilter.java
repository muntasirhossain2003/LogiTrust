package com.logitrust.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window per-IP rate limit on the auth endpoints (SRS 9.4: "rate
 * limiting on login and checkpoint-scan endpoints"). In-memory and
 * single-instance only — good enough for this deployment; a multi-instance
 * deployment should replace this with bucket4j backed by Redis so the
 * counters are shared across nodes.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_WINDOW = 20;
    private static final long WINDOW_SECONDS = 60;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String key = clientIp(request);
        Window window = windows.compute(key, (k, existing) -> {
            Instant now = Instant.now();
            if (existing == null || existing.windowStart.plusSeconds(WINDOW_SECONDS).isBefore(now)) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (window.count.get() > MAX_REQUESTS_PER_WINDOW) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(WINDOW_SECONDS));
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"too_many_requests\",\"message\":\"Rate limit exceeded, try again shortly.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record Window(Instant windowStart, AtomicInteger count) {
    }
}
