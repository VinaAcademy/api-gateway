package vn.huuloc.apigateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.web.exchanges.HttpExchange;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import vn.huuloc.apigateway.grpc.JwtServiceClient;
import vn.vinaacademy.common.response.ApiResponse;

@Component
@Slf4j
public class JwtAuthenticationGatewayFilterFactory
        extends AbstractGatewayFilterFactory<JwtAuthenticationGatewayFilterFactory.Config> {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtServiceClient jwtServiceClient; // blocking gRPC client

    public JwtAuthenticationGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            log.debug("Processing JWT filter for: {}", request.getURI());

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            return Mono.fromCallable(() -> jwtServiceClient.validateToken(token))
                    .subscribeOn(Schedulers.boundedElastic()) // offload to worker thread
                    .flatMap(validateToken -> {
                        if (!validateToken.getIsValid()) {
                            log.warn("Invalid JWT token: {}", token);
                            return onError(exchange, "Invalid JWT token", HttpStatus.UNAUTHORIZED);
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
                        log.error("JWT validation error: {}", ex.getMessage());
                        return onError(exchange, "JWT validation failed", HttpStatus.INTERNAL_SERVER_ERROR);
                    });
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String errorMessage, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        ApiResponse<?> apiResponse = ApiResponse.error(errorMessage);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(apiResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response: {}", e.getMessage());
            return response.setComplete();
        }
    }

    public static class Config {
        // Add config fields here if needed later
    }
}
