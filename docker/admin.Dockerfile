# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY ivr-common/pom.xml ivr-common/
COPY ivr-engine/pom.xml ivr-engine/
COPY ivr-ai/pom.xml      ivr-ai/
COPY ivr-call/pom.xml    ivr-call/
COPY ivr-admin/pom.xml   ivr-admin/
RUN mvn dependency:go-offline -B || true
COPY . .
RUN mvn clean package -DskipTests -pl ivr-admin -am

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
ENV JAVA_OPTS="-Xms256m -Xmx1g"
COPY --from=build /build/ivr-admin/target/ivr-admin.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
