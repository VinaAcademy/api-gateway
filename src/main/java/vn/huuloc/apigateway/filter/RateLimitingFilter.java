package vn.huuloc.apigateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
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

  @Autowired
  private ObjectMapper objectMapper;

  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  private final ConcurrentHashMap<String, AtomicInteger> inMemoryCounter = new ConcurrentHashMap<>();

  private final ScheduledExecutorService schedule = Executors.newSingleThreadScheduledExecutor();

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!securityProperties.getRateLimit().isEnabled()) {
      return chain.filter(exchange);
    }

    String clientIp = getClientIp(exchange.getRequest());
    String requestPath = exchange.getRequest().getPath().pathWithinApplication().value();
    if (isExcluded(exchange.getRequest(), clientIp, requestPath)) {
      log.debug("Request excluded from rate limit: ip={}, path={}", clientIp, requestPath);
      return chain.filter(exchange);
    }

    String key = String.format("%s%s",
        securityProperties.getRateLimit().getRedisKeyPrefix(),
        clientIp);

    return checkRateLimit(key)
        .flatMap(allowed -> {
          if (!allowed) {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            return onError(exchange, "Rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS,
                objectMapper);
          }
          log.debug("Rate limit check passed for IP: {}", clientIp);
          return chain.filter(exchange)
              .doOnSuccess(
                  aVoid -> log.debug("Request from IP {} processed successfully", clientIp))
              .doOnError(throwable -> log.error("Error processing request from IP {}: {}", clientIp,
                  throwable.getMessage()));
        });
  }

  private boolean isExcluded(ServerHttpRequest request, String clientIp, String path) {
    GatewaySecurityProperties.RateLimit.Exceptions ex = securityProperties.getRateLimit().getExceptions();
    if (ex == null) return false;

    // 1) Check exact IP list (you can extend for CIDR)
    List<String> ips = ex.getIps();
    if (ips != null && !ips.isEmpty()) {
      for (String ipPattern : ips) {
        if (ipPattern.contains("/")) {
          // Optional: CIDR check (simple placeholder) - implement proper CIDR check if needed
          if (isIpInCidr(clientIp, ipPattern)) return true;
        } else if (ipPattern.equals(clientIp)) {
          return true;
        }
      }
    }

    // 2) Check path patterns
    List<String> paths = ex.getPaths();
    if (paths != null) {
      for (String pattern : paths) {
        if (pathMatcher.match(pattern, path)) {
          return true;
        }
      }
    }

    // 3) Check API key header (e.g. X-API-KEY)
    List<String> apiKeys = ex.getApiKeys();
    if (apiKeys != null && !apiKeys.isEmpty()) {
      List<String> headerValues = request.getHeaders().getOrEmpty("X-API-KEY");
      for (String hv : headerValues) {
        for (String allowed : apiKeys) {
          if (allowed.equals(hv)) return true;
        }
      }
      // also support query param
      String keyParam = request.getQueryParams().getFirst("api_key");
      return keyParam != null && apiKeys.contains(keyParam);
    }

    return false;
  }

  // Simple CIDR check placeholder — replace with robust implementation (e.g. use apache commons-net or ipaddress library)
  private boolean isIpInCidr(String ip, String cidr) {
    try {
      // naive: only handle /32 or /24 quick-case for demo
      if (cidr.endsWith("/32")) {
        return ip.equals(cidr.split("/")[0]);
      }
      if (cidr.endsWith("/24")) {
        String prefix = cidr.split("/")[0].substring(0, cidr.split("/")[0].lastIndexOf('.'));
        return ip.startsWith(prefix + ".");
      }
    } catch (Exception e) {
      // ignore and treat as not matched
    }
    return false;
  }


  private Mono<Boolean> checkRateLimit(String key) {
    return redisTemplate == null ? checkInMemory(key)
        : checkInRedis(key);
  }

  private Mono<Boolean> checkInRedis(String key) {
    return Mono.fromCallable(() -> {
          Long current = redisTemplate.opsForValue().increment(key);
          if (current == null) {
            return true;
          }

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
      int current = counter.incrementAndGet();
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