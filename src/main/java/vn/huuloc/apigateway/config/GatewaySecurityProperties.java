package vn.huuloc.apigateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "gateway.security")
public class GatewaySecurityProperties {

    private Jwt jwt = new Jwt();
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Jwt {
        private List<WhiteListEntry> whitelist = List.of(
                new WhiteListEntry("/api/v1/auth/login"),
                new WhiteListEntry("/api/v1/auth/register"),
                new WhiteListEntry("/api/v1/auth/refresh"),
                new WhiteListEntry("/actuator/health"),
                new WhiteListEntry("/actuator/info"),
                new WhiteListEntry("/favicon.ico"),
                new WhiteListEntry("/error")
        );
    }

    @Data
    public static class WhiteListEntry {
        private String path;
        private Set<HttpMethod> methods = Set.of();

        public WhiteListEntry() {
        }

        public WhiteListEntry(String path) {
            this.path = path;
            methods = Set.of();
        }

        public WhiteListEntry(String path, Set<HttpMethod> methods) {
            this.path = path;
            this.methods = methods;
        }
    }

    @Data
    public static class RateLimit {
        private boolean enabled = true;
        private int maxRequestsPerMinute = 100;
        private String redisKeyPrefix = "rate_limit:";
    }
}