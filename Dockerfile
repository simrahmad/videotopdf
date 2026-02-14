# Stage 1: Build Java app
FROM maven:3.9.5-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime with Python + Java
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y \
    python3.10 \
    python3-pip \
    ffmpeg \
    curl \
    && rm -rf /var/lib/apt/lists/*

RUN pip3 install \
    youtube-transcript-api \
    SpeechRecognition \
    pydub \
    deep-translator \
    supadata

COPY --from=build /app/target/*.jar app.jar
COPY transcribe.py .
COPY get_transcript.py .
COPY transliterate_text.py .

EXPOSE 10000
ENTRYPOINT ["java", "-jar", "app.jar"]
