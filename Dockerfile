FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build

WORKDIR /build
COPY pom.xml LICENSE ./
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:25-jre-noble@sha256:2f1da100788559b397bcf48c736169ea5b070bde84e55f203bbee8e83d87a175

LABEL org.opencontainers.image.title="PDF Text Layer Audit"
LABEL org.opencontainers.image.description="Audit changed PDF files in GitHub pull requests"
LABEL org.opencontainers.image.source="https://github.com/lMysticl/pdf-text-layer-auditor"
LABEL org.opencontainers.image.licenses="Apache-2.0"

WORKDIR /app
COPY --from=build /build/target/pdf-text-layer-auditor.jar /app/pdf-text-layer-auditor.jar

ENTRYPOINT ["java", "-cp", "/app/pdf-text-layer-auditor.jar", "dev.putrenkov.pdfaudit.github.GitHubActionMain"]
