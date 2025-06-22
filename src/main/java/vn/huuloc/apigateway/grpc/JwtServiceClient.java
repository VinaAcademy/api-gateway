package vn.huuloc.apigateway.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import vn.vinaacademy.common.constant.ServiceNames;
import vn.vinaacademy.common.dto.GrpcServer;
import vn.vinaacademy.common.utils.GrpcUtils;
import vn.vinaacademy.grpc.JwtServiceGrpc;
import vn.vinaacademy.grpc.JwtServiceProto;

@Component
@Slf4j
public class JwtServiceClient {
    @Autowired
    private DiscoveryClient discoveryClient;

    private ManagedChannel channel;
    private JwtServiceGrpc.JwtServiceBlockingStub stub;

    @PostConstruct
    public void init() {
        try {
            GrpcServer grpcServer = GrpcUtils.getGrpcServer(discoveryClient, ServiceNames.USER_SERVICE);
            channel = ManagedChannelBuilder.forAddress(grpcServer.getHost(), grpcServer.getPort())
                    .usePlaintext() // Use plaintext for simplicity; consider using TLS in production
                    .build();
            stub = JwtServiceGrpc.newBlockingStub(channel);
            log.info("JwtServiceClient initialized with host: {}, port: {}", grpcServer.getHost(), grpcServer.getPort());
        } catch (Exception e) {
            log.error("Failed to initialize JwtServiceClient: {}", e.getMessage());
            throw new RuntimeException("Failed to initialize JwtServiceClient", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
            log.info("JwtServiceClient channel closed");
        }
    }

    public JwtServiceProto.ValidateTokenResponse validateToken(String token) {
        log.info("Validating token: {}", token);
        JwtServiceProto.Token request = JwtServiceProto.Token.newBuilder()
                .setToken(token)
                .build();
        return stub.validate(request);
    }
}
