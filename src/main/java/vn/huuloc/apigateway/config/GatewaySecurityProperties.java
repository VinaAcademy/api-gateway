package vn.huuloc.apigateway.config;

import java.util.ArrayList;
import java.util.List;
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

        // exceptions
        private Exceptions exceptions = new Exceptions();

        @Data
        public static class Exceptions {
            private List<String> ips = new ArrayList<>();
            private List<String> paths = new ArrayList<>();
            private List<String> apiKeys = new ArrayList<>();
        }
    }
}