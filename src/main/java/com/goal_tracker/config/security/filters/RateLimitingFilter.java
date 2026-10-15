package com.goal_tracker.config.security.filters;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.goal_tracker.errors.ErrorCode;
import com.goal_tracker.errors.ErrorResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Filter responsible for intercepting HTTP requests and enforcing IP-based rate limiting
 * using Bucket4j and Redis for authentication and registration endpoints.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final ProxyManager<String> proxyManager;
    private final BucketConfiguration bucketConfiguration;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the RateLimitingFilter with the required Redis proxy manager,
     * Jackson ObjectMapper for error serialization, and configurable rate-limiting rules.
     *
     * @param proxyManager          the proxy manager for distributed token buckets
     * @param objectMapper          Jackson object mapper defined in JacksonConfig
     * @param capacity              maximum token capacity of the bucket
     * @param refillTokens          number of tokens added during each refill period
     * @param refillDurationMinutes duration in minutes for the refill period
     */
    public RateLimitingFilter(
            ProxyManager<String> proxyManager,
            ObjectMapper objectMapper,
            @Value("${app.security.rate-limit.capacity:5}") long capacity,
            @Value("${app.security.rate-limit.refill-tokens:5}") long refillTokens,
            @Value("${app.security.rate-limit.refill-duration-minutes:1}") long refillDurationMinutes) {
        
        this.proxyManager = proxyManager;
        this.objectMapper = objectMapper;
        this.bucketConfiguration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(refillTokens, Duration.ofMinutes(refillDurationMinutes))
                        .build())
                .build();
    }

    /**
     * Intercepts incoming HTTP requests to check and enforce rate limits for endpoints
     * starting with "/auth/". Extracts the client IP (supporting proxy headers), 
     * queries Redis via Bucket4j, and either allows the request or blocks it with an HTTP 429 status.
     *
     * @param request     the current HTTP servlet request
     * @param response    the current HTTP servlet response
     * @param filterChain the filter chain for invoking the next filter in the pipeline
     * @throws ServletException if an error occurs during filtering
     * @throws IOException      if an I/O error occurs during filtering
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith("/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        String bucketKey = "rate_limit:" + clientIp;

        boolean resolved = proxyManager.builder()
                .build(bucketKey, () -> bucketConfiguration)
                .tryConsume(1);
        
        if (!resolved) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            String trackingId = UUID.randomUUID().toString();
            ErrorResponse errorResponse = new ErrorResponse(
                    ErrorCode.ERR_TOO_MANY_REQUESTS.name(),
                    ErrorCode.ERR_TOO_MANY_REQUESTS.getDefaultMessage(),
                    trackingId
            );

            objectMapper.writeValue(response.getWriter(), errorResponse);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the client IP address, properly handling proxy headers such as X-Forwarded-For.
     *
     * @param request the HTTP servlet request
     * @return the resolved client IP address string
     */
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}