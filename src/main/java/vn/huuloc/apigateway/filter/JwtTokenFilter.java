package vn.huuloc.apigateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import vn.huuloc.apigateway.config.GatewaySecurityProperties;
import vn.huuloc.apigateway.filter.constant.FilterConstants;
import vn.huuloc.apigateway.grpc.JwtServiceClient;

import static vn.huuloc.apigateway.utils.RequestResponseUtils.onError;

@Component
@Slf4j
public class JwtTokenFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtServiceClient jwtServiceClient;

    @Autowired
    private GatewaySecurityProperties securityProperties;

    @Autowired
    private ObjectMapper objectMapper;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        log.debug("Processing JWT filter for path: {}", path);

        // Check if path is whitelisted
        if (isWhitelistedPath(path, exchange)) {
            log.debug("Path {} is whitelisted, skipping JWT validation", path);
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED,
                    objectMapper);
        }

        String token = authHeader.substring(7);

        return Mono.fromCallable(() -> jwtServiceClient.validateToken(token))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(validateToken -> {
                    if (!validateToken.getIsValid()) {
                        log.warn("Invalid JWT token for path: {}", path);
                        return onError(exchange, "Invalid JWT token", HttpStatus.UNAUTHORIZED,
                                objectMapper);
                    }

                    ServerHttpRequest mutatedRequest = request.mutate()
                            .header("X-User-Id", validateToken.getUserId())
                            .header("X-User-Email", validateToken.getEmail())
                            .header("X-User-Roles", validateToken.getRoles())
                            .build();

                    ServerWebExchange updatedExchange = exchange.mutate()
                            .request(mutatedRequest)
                            .build();

                    return chain.filter(updatedExchange);
                })
                .onErrorResume(ex -> {
                    log.error("JWT validation error for path {}: {}", path, ex.getMessage());
                    return onError(exchange, ex.getMessage(), HttpStatus.UNAUTHORIZED,
                            objectMapper);
                });
    }

    private boolean isWhitelistedPath(String path, ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod pathMethod = request.getMethod();
        return securityProperties.getJwt().getWhitelist().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern.getPath(), path) &&
                        (pattern.getMethods().isEmpty() ||
                                pattern.getMethods().contains(pathMethod)));
    }

    @Override
    public int getOrder() {
        return FilterConstants.JWT_TOKEN_PRECEDENCE;
    }
}