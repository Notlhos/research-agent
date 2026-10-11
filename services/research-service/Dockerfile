FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN bash gradlew dependencies --no-daemon -q > /dev/null
COPY src ./src
RUN bash gradlew bootJar --no-daemon -x test -q && cp build/libs/research-service-*.jar /app.jar

FROM eclipse-temurin:21-jre
RUN useradd --create-home --uid 10001 app
COPY --from=build /app.jar /opt/app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/opt/app/app.jar"]
