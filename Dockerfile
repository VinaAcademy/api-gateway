# Stage 1: Build stage
FROM eclipse-temurin:17-jdk-alpine AS builder

# Thiết lập thư mục làm việc
WORKDIR /app

# Copy Maven wrapper và pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Cấp quyền thực thi cho Maven wrapper
RUN chmod +x ./mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code và build
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:17-jdk-alpine

# Thiết lập thông tin metadata
LABEL maintainer="VinaAcademy"
LABEL description="API Gateway Service using Spring Cloud Gateway"

# Cài đặt các packages cần thiết và tạo user
RUN apk add --no-cache \
    curl \
    && addgroup -g 1001 -S vinaacademy \
    && adduser -u 1001 -S vinaacademy -G vinaacademy

# Thiết lập thư mục làm việc
WORKDIR /app

# Copy JAR file từ build stage
COPY --from=builder /app/target/*.jar app.jar

# RSA keys storage directory
RUN mkdir -p /app/keys && chown -R vinaacademy:vinaacademy /app/keys

# Chuyển quyền sở hữu
RUN chown vinaacademy:vinaacademy app.jar

# Chuyển sang user non-root
USER vinaacademy

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Thiết lập JVM options
ENV JAVA_OPTS="-Xmx1024m -Xms512m -XX:+UseG1GC -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"

# Command để chạy ứng dụng
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
