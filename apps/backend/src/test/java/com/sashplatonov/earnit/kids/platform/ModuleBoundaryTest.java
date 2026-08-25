package com.sashplatonov.earnit.kids.platform;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleBoundaryTest {

    private static final String PACKAGE_ROOT = "com.sashplatonov.earnit.kids.";
    private static final Set<String> LEGACY_ROOTS = Set.of(
        "domain", "dto", "repository", "resource", "service"
    );
    private static final Set<String> ALLOWED_LEGACY_TYPES = Set.of(
        "dto.response.AccountConnectionResponse",
        "dto.response.AdminActivationFunnelResponse",
        "dto.response.AdminAnalyticsResponse",
        "dto.response.AdminChildBehaviorResponse",
        "dto.response.AdminCoinEconomyResponse",
        "dto.response.AdminDashboardResponse",
        "dto.response.AdminParentBehaviorResponse",
        "dto.response.AdminRetentionResponse",
        "dto.response.AdminRewardsResponse",
        "dto.response.AdminTasksResponse",
        "dto.response.AdminTrendsResponse",
        "dto.response.AuthConfigResponse",
        "dto.response.AuthPayload",
        "dto.response.AuthResponse",
        "dto.response.DatabaseHealthResponse",
        "dto.response.HttpMetricsResponse",
        "dto.response.SessionPageDataResponse",
        "dto.response.TokenResponse",
        "resource.common.ClientErrorMessage",
        "resource.common.ClientErrorResource",
        "resource.common.ResourceAuthSupport",
        "service.event.ApplicationEventPublisher"
    );
    private static final Set<String> ALLOWED_LEGACY_PACKAGES = Set.of(
        "dto.response", "resource.common", "service.event"
    );

    @Test
    void legacySourceRootsContainOnlyExplicitlyRetainedContracts() throws IOException {
        assertThat(legacyFiles("src/main/java"))
            .containsExactlyInAnyOrder(
                "dto/response/AccountConnectionResponse.java",
                "dto/response/AdminActivationFunnelResponse.java",
                "dto/response/AdminAnalyticsResponse.java",
                "dto/response/AdminChildBehaviorResponse.java",
                "dto/response/AdminCoinEconomyResponse.java",
                "dto/response/AdminDashboardResponse.java",
                "dto/response/AdminParentBehaviorResponse.java",
                "dto/response/AdminRetentionResponse.java",
                "dto/response/AdminRewardsResponse.java",
                "dto/response/AdminTasksResponse.java",
                "dto/response/AdminTrendsResponse.java",
                "dto/response/AuthConfigResponse.java",
                "dto/response/AuthPayload.java",
                "dto/response/AuthResponse.java",
                "dto/response/DatabaseHealthResponse.java",
                "dto/response/HttpMetricsResponse.java",
                "dto/response/SessionPageDataResponse.java",
                "dto/response/TokenResponse.java",
                "resource/common/ClientErrorMessage.java",
                "resource/common/ClientErrorResource.java",
                "resource/common/ResourceAuthSupport.java",
                "service/event/ApplicationEventPublisher.java"
            );
        assertThat(legacyFiles("src/test/java")).isEmpty();
    }

    @Test
    void legacyPackageDeclarationsAndImportsRemainExplicitlyAllowlisted() throws IOException {
        assertThat(legacyReferences("src/main/java"))
            .allMatch(this::isAllowedLegacyReference);
        assertThat(legacyReferences("src/test/java"))
            .allMatch(this::isAllowedLegacyReference);
    }

    private List<String> legacyFiles(String sourceRoot) throws IOException {
        Path root = Path.of(sourceRoot, PACKAGE_ROOT.replace('.', '/'));
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .map(root::relativize)
                .filter(path -> LEGACY_ROOTS.contains(path.getName(0).toString()))
                .map(Path::toString)
                .toList();
        }
    }

    private List<String> legacyReferences(String sourceRoot) throws IOException {
        Path root = Path.of(sourceRoot, PACKAGE_ROOT.replace('.', '/'));
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .flatMap(this::legacyReferencesIn)
                .toList();
        }
    }

    private Stream<String> legacyReferencesIn(Path path) {
        try {
            return Files.readAllLines(path).stream()
                .filter(line -> line.startsWith("package ") || line.startsWith("import "))
                .filter(line -> LEGACY_ROOTS.stream().anyMatch(root ->
                    line.contains(PACKAGE_ROOT + root + ".")));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect " + path, exception);
        }
    }

    private boolean isAllowedLegacyReference(String reference) {
        if (reference.startsWith("package ")) {
            return ALLOWED_LEGACY_PACKAGES.stream().anyMatch(packageName ->
                reference.contains(PACKAGE_ROOT + packageName + ";"));
        }
        return ALLOWED_LEGACY_TYPES.stream().anyMatch(type ->
            reference.contains(PACKAGE_ROOT + type));
    }
}
