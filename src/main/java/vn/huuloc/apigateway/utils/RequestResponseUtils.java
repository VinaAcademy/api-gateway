package vn.huuloc.apigateway.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@UtilityClass
public class RequestResponseUtils {

  public static String getClientIp(ServerHttpRequest request) {
    String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      return xForwardedFor.split(",")[0].trim();
    }

    String xRealIp = request.getHeaders().getFirst("X-Real-IP");
    if (xRealIp != null && !xRealIp.isEmpty()) {
      return xRealIp;
    }

    return request.getRemoteAddress() != null ?
        request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
  }


  public static Mono<Void> onError(ServerWebExchange exchange, String errorMessage,
      HttpStatus status,
      ObjectMapper objectMapper) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(status);
    response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

    Map<String, String> apiResponse = Map.of(
        "message", errorMessage,
        "status", String.valueOf(status.value())
    );

    try {
      byte[] bytes = objectMapper.writeValueAsBytes(apiResponse);
      DataBuffer buffer = response.bufferFactory().wrap(bytes);
      return response.writeWith(Mono.just(buffer));
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize error response: {}", e.getMessage());
      return response.setComplete();
    }
  }
}
