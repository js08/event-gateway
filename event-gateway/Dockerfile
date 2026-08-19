# STEP 1: Choose a lightweight Java 17 base image that contains the Java Runtime Environment (JRE)
FROM eclipse-temurin:17-jre-alpine

# STEP 2: Set the working folder inside the container where commands will run
WORKDIR /app

# STEP 3: Copy the compiled Spring Boot JAR file from your target folder into the container and name it app.jar
COPY target/event-gateway-*.jar app.jar

# STEP 4: Tell Docker that this container listens on port 8080 at runtime
EXPOSE 8080

# STEP 5: Define the default command to execute when the container starts up (runs 'java -jar app.jar')
ENTRYPOINT ["java", "-jar", "app.jar"]