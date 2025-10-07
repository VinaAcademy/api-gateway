# API Gateway - AI Coding Agent Instructions

## Architecture Overview

This is a **Spring Cloud Gateway** service that acts as the central entry point for the VinaAcademy microservices platform. It's part of a microservices architecture transitioning from a monolithic `VINAACADEMY-PLATFORM` to separate services (`CHAT-SERVICE`, `NOTIFICATION-SERVICE`).

**Key architectural decisions:**
- Uses **reactive programming** (WebFlux) - all filters must return `Mono<Void>` or work with reactive streams
- **Service discovery** via Eureka (`@EnableDiscoveryClient`) - routes use `lb://SERVICE-NAME` format
- **Redis-based rate limiting** with in-memory fallback when Redis unavailable
- **Dual route configuration**: declarative (YAML) + programmatic (beans)

## Critical Integration Points

### 1. Shared Common Library
- **Dependency**: `com.github.VinaAcademy:common-library:2.0.0` from JitPack
- Provides `ApiResponse<?>` wrapper used in error responses (see `FallbackController`, `RequestResponseUtils`)
- When creating error responses, always use: `ApiResponse.error("message")`

### 2. Service Routes & Migration Pattern
Routes defined in `application.yml` follow a **strangler fig pattern**:
```yaml
# Migrated services (specific paths first)
- chat-websocket: lb:ws://CHAT-SERVICE → /ws/chat/**
- chat-api: lb://CHAT-SERVICE → /api/v1/conversations/**, /api/v1/messages/**
- notification: lb://NOTIFICATION-SERVICE → /api/v1/notifications/**

# Fallback to monolith (must be LAST)
- platform-fallback: lb://VINAACADEMY-PLATFORM → /api/v1/**
```
**⚠️ Route order matters**: New service routes must come before `platform-fallback`.

### 3. WebSocket Handling
The chat service uses **STOMP over WebSocket** with **SockJS fallback**. Special handling required:

**Route Configuration:**
- Primary WebSocket route: `lb:ws://CHAT-SERVICE` → `/ws/chat/**`
- SockJS fallback route: `lb://CHAT-SERVICE` (HTTP) → `/ws/chat/**` (GET/POST/OPTIONS)
- Add `PreserveHostHeader` filter for handshake
- Remove `Sec-WebSocket-Protocol` header (conflicts with STOMP)
- Keep `Origin` header for SockJS CORS

**Authentication:**
- `WebSocketAuthFilter` extracts JWT token from query parameter (`?token=xxx`)
- Converts to `Authorization: Bearer xxx` header
- Runs at precedence `-3` (before rate limiting)
- Chat service validates via gRPC in `JwtHandshakeInterceptor`

**Client Connection Pattern:**
```javascript
const socket = new SockJS(`http://gateway:8080/ws/chat?token=${jwt}`);
const stomp = Stomp.over(socket);
stomp.connect({}, frame => {
  stomp.subscribe('/user/queue/messages', msg => {...});
  stomp.send('/app/pm', {}, JSON.stringify(message));
});
```

See `WEBSOCKET_SETUP.md` for comprehensive documentation.

## Project-Specific Patterns

### Filter Implementation
All global filters extend `GlobalFilter, Ordered`:
```java
@Component
public class MyFilter implements GlobalFilter, Ordered {
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Pre-processing
        return chain.filter(exchange)
            .doOnSuccess(v -> /* post-processing */)
            .doOnError(e -> /* error handling */);
    }
    
    public int getOrder() {
        return FilterConstants.MY_FILTER_PRECEDENCE; // Define in FilterConstants
    }
}
```
**Filter order precedence** (from `FilterConstants`):
- `WEBSOCKET_AUTH_PRECEDENCE = -3` (runs first for WebSocket auth)
- `RATE_LIMIT_PRECEDENCE = -2` (runs after auth setup)
- `REMOVE_DUPLICATE_HEADER_PRECEDENCE = -1`
- `Ordered.HIGHEST_PRECEDENCE` for logging (catches everything)

### IP Address Extraction
Always use `RequestResponseUtils.getClientIp(request)` which checks:
1. `X-Forwarded-For` header (proxy chains)
2. `X-Real-IP` header (direct proxy)
3. `RemoteAddress` (fallback)

### Error Response Pattern
```java
return RequestResponseUtils.onError(
    exchange, 
    "Error message", 
    HttpStatus.TOO_MANY_REQUESTS, 
    objectMapper
);
```
This ensures consistent JSON error responses matching the common library format.

## Configuration Conventions

### Rate Limiting
Configured via `GatewaySecurityProperties` (`@ConfigurationProperties`):
```yaml
gateway:
  security:
    rate-limit:
      enabled: true
      max-requests-per-minute: 100
      redis-key-prefix: "rate_limit:"
```
- **Dual storage**: Redis (preferred) + in-memory (fallback)
- IP-based keying via `ipKeyResolver` bean
- 1-minute sliding window with auto-cleanup

### CORS Strategy
**Hybrid approach**: 
- `CorsWebFilter` bean for reactive stack (handles preflight OPTIONS)
- YAML config commented out to avoid conflicts with WebFlux reactive handling
- Pattern: `allowedOriginPatterns: "*"` + `allowCredentials: true`

## Developer Workflows

### Running Locally
```powershell
# Prerequisites: Redis + Eureka server running
mvnw.cmd spring-boot:run
```
Default port: `8080`

### Testing Routes
```powershell
# Check health (actuator endpoints)
curl http://localhost:8080/actuator/health

# Test rate limiting (exceed 100 req/min)
for ($i=1; $i -le 101; $i++) { curl http://localhost:8080/api/v1/test }
```

### Building
```powershell
mvnw.cmd clean package
```

### Debugging Tips
- Enable debug logging: `logging.level.vn.huuloc.apigateway: DEBUG`
- Check `LoggingFilter` output for route resolution issues
- Redis connection failures auto-fallback to in-memory (check logs for warnings)

## Common Gotchas

1. **Reactive context required**: Never use blocking calls (`Thread.sleep()`, JDBC) in filters
2. **Parent POM dependency**: Project inherits from `vinaacademy-parent:2.0.0` - check JitPack if dependency resolution fails
3. **Gateway server version pinned**: Explicitly uses `spring-cloud-gateway-server:4.3.1` (exclusion + re-inclusion pattern in POM)
4. **Service naming**: Eureka service IDs must match route URIs (e.g., `CHAT-SERVICE` not `chat-service` unless using `lower-case-service-id: true`)
5. **Duplicate header handling**: `DedupeResponseHeader` with `RETAIN_FIRST` strategy for CORS headers

## When Adding New Routes

1. Define route in `application.yml` **before** `platform-fallback`
2. Add service to Eureka registry
3. Test WebSocket routes separately (different protocol handling)
4. Consider rate limits - adjust per-route if needed via filter args
5. Update `LoggingFilter` if route requires special logging logic
