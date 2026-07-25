FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build

COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline -q

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 ./mvnw package -DskipTests -q

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser

COPY --from=builder /build/target/social-api-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
