FROM maven:3.9.15-eclipse-temurin-26@sha256:029a8e2838ae68238ffb8be407cddbb3f07d4d839c60c6f26c619a69fd184531 AS build

WORKDIR /build
COPY pom.xml LICENSE ./
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-noble@sha256:373787d1d45a87f084fda43e7de0e9acf5eedee049446efac738f13587ec4c64

LABEL org.opencontainers.image.title="PDF Text Layer Audit"
LABEL org.opencontainers.image.description="Audit changed PDF files in GitHub pull requests"
LABEL org.opencontainers.image.source="https://github.com/lMysticl/pdf-text-layer-auditor"
LABEL org.opencontainers.image.licenses="Apache-2.0"

WORKDIR /app
COPY --from=build /build/target/pdf-text-layer-auditor.jar /app/pdf-text-layer-auditor.jar

ENTRYPOINT ["java", "-cp", "/app/pdf-text-layer-auditor.jar", "dev.putrenkov.pdfaudit.github.GitHubActionMain"]
