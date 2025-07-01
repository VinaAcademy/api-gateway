package vn.huuloc.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import vn.huuloc.apigateway.config.GatewaySecurityProperties;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static vn.huuloc.apigateway.filter.constant.FilterConstants.RATE_LIMIT_MINUTES;
import static vn.huuloc.apigateway.filter.constant.FilterConstants.RATE_LIMIT_PRECEDENCE;
import static vn.huuloc.apigateway.utils.RequestResponseUtils.getClientIp;
import static vn.huuloc.apigateway.utils.RequestResponseUtils.onError;

@Component
@Slf4j
public class RateLimitingFilter implements GlobalFilter, Ordered {

    @Autowired
    private GatewaySecurityProperties securityProperties;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private final ConcurrentHashMap<String, AtomicInteger> inMemoryCounter = new ConcurrentHashMap<>();

    private final ScheduledExecutorService schedule = Executors.newSingleThreadScheduledExecutor();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!securityProperties.getRateLimit().isEnabled()) {
            return chain.filter(exchange);
        }

        String clientIp = getClientIp(exchange.getRequest());
        String key = String.format("%s%s",
                securityProperties.getRateLimit().getRedisKeyPrefix(),
                clientIp);

        return checkRateLimit(key)
                .flatMap(allowed -> {
                    if (!allowed) {
                        log.warn("Rate limit exceeded for IP: {}", clientIp);
                        return onError(exchange, "Rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS);
                    }
                    log.debug("Rate limit check passed for IP: {}", clientIp);
                    return chain.filter(exchange)
                            .doOnSuccess(aVoid -> log.debug("Request from IP {} processed successfully", clientIp))
                            .doOnError(throwable -> log.error("Error processing request from IP {}: {}", clientIp, throwable.getMessage()));
                });
    }

    private Mono<Boolean> checkRateLimit(String key) {
        return redisTemplate == null ? checkInMemory(key)
                : checkInRedis(key);
    }

    private Mono<Boolean> checkInRedis(String key) {
        return Mono.fromCallable(() -> {
                    Long current = redisTemplate.opsForValue().increment(key);
                    if (current == null) return true;

                    if (current == 1) {
                        redisTemplate.expire(key, RATE_LIMIT_MINUTES, TimeUnit.MINUTES);
                    }

                    return current <= securityProperties.getRateLimit().getMaxRequestsPerMinute();
                })
                .onErrorResume(ex -> {
                    log.error("Error checking rate limit in Redis for key {}: {}", key, ex.getMessage());
                    return Mono.just(true); // fallback: allow if Redis fails
                });
    }

    private Mono<Boolean> checkInMemory(String key) {
        return Mono.fromCallable(() -> {
            AtomicInteger counter = inMemoryCounter.computeIfAbsent(key, k -> {
                scheduleCleanup(k);
                return new AtomicInteger(0);
            });
            int current = counter.getAndIncrement();
            return current <= securityProperties.getRateLimit().getMaxRequestsPerMinute();
        });
    }

    private void scheduleCleanup(String key) {
        schedule.schedule(() -> {
            inMemoryCounter.remove(key);
            log.debug("Rate limit counter for {} has been reset", key);
        }, RATE_LIMIT_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public int getOrder() {
        return RATE_LIMIT_PRECEDENCE;
    }
}