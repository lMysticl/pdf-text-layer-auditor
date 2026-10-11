FROM maven:3-eclipse-temurin-27@sha256:d0d38ebffce1172d5c9c0be2e28691f598f9a91f578858a77d133abeb91434f9 AS build

WORKDIR /build
COPY pom.xml LICENSE ./
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-noble@sha256:000fd431958bc81a24abe1e8e5f0f0fd3ae365a594bd50aadb20696805f9408c

LABEL org.opencontainers.image.title="PDF Text Layer Audit"
LABEL org.opencontainers.image.description="Audit changed PDF files in GitHub pull requests"
LABEL org.opencontainers.image.source="https://github.com/lMysticl/pdf-text-layer-auditor"
LABEL org.opencontainers.image.licenses="Apache-2.0"

WORKDIR /app
COPY --from=build /build/target/pdf-text-layer-auditor.jar /app/pdf-text-layer-auditor.jar

ENTRYPOINT ["java", "-cp", "/app/pdf-text-layer-auditor.jar", "dev.putrenkov.pdfaudit.github.GitHubActionMain"]
