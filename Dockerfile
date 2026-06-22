FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY --from=builder /app/target/inventory-service.jar .
EXPOSE 7390
ENTRYPOINT ["java", "-jar", "/app/inventory-service.jar"]
