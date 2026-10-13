FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN sh mvnw -B -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/payment-event-lab-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--server.address=0.0.0.0"]
