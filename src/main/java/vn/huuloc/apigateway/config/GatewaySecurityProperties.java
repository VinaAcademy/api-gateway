package vn.huuloc.apigateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "gateway.security")
public class GatewaySecurityProperties {
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class RateLimit {
        private boolean enabled = true;
        private int maxRequestsPerMinute = 100;
        private String redisKeyPrefix = "rate_limit:";
    }
}