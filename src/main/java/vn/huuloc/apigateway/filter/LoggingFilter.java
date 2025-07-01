package vn.huuloc.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static vn.huuloc.apigateway.utils.RequestResponseUtils.getClientIp;

@Component
@Slf4j
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. log request
        ServerHttpRequest request = exchange.getRequest();
        long startTime = System.currentTimeMillis();

        log.debug("[REQUEST] {} {} - Request IP: {} - UserAgent: {}",
                request.getMethod(),
                request.getURI(),
                getClientIp(request),
                request.getHeaders().getFirst("User-Agent"));

        // 2. log route
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        if (route != null) {
            URI uri = route.getUri();
            log.debug("[ROUTE] {} - Route ID: {} - URI: {}",
                    route.getId(),
                    uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : ""),
                    uri);
        }

        // 3. log the response
        return chain.filter(exchange)
                .doOnSuccess(aVoid -> {
                    ServerHttpResponse response = exchange.getResponse();
                    long endTime = System.currentTimeMillis() - startTime;

                    log.info("[RESPONSE] {} {} - Status: {} - Time taken: {} ms",
                            request.getMethod(),
                            request.getURI(),
                            response.getStatusCode(),
                            endTime);
                })
                .doOnError(throwable -> {
                    log.error("[ERROR] {} {} - Error: {}",
                            request.getMethod(),
                            request.getURI(),
                            throwable.getMessage());
                })
                .doFinally(signalType -> {
                    log.info("[CLIENT IP] {} - UserAgent: {}",
                            getClientIp(request),
                            request.getHeaders().get("User-Agent"));
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}