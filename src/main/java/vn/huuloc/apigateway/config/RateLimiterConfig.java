package vn.huuloc.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import vn.huuloc.apigateway.utils.RequestResponseUtils;

import java.util.Objects;

@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            var request = exchange.getRequest();
            // Extract the IP address from the request
            String ip = RequestResponseUtils.getClientIp(request);
            return Mono.just(ip);
        };
    }
}
