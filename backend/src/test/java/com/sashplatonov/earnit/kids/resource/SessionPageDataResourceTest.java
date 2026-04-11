package com.sashplatonov.earnit.kids.resource;

import io.quarkus.test.junit.QuarkusTest;
import com.sashplatonov.earnit.kids.config.JwtCompatVerifier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class SessionPageDataResourceTest {
    @Test
    void returnsUnauthenticatedSnapshotWithoutCookies() {
        given()
            .when()
            .get("/api/page-data/session")
            .then()
            .statusCode(200)
            .body("authenticated", equalTo(false))
            .body("role", nullValue());
    }

    @Test
    void returnsDecodedSnapshotForCompatJwt() {
        String token = JwtCompatVerifier.sign(Map.of(
            "familyId", "family-1",
            "role", "admin",
            "email", "parent@example.com",
            "csrfToken", "csrf-123"
        ), "test-secret-key-for-unit-tests", 300);

        given()
            .header("Cookie", "app_auth=" + token + "; csrf_token=csrf-123")
            .when()
            .get("/api/page-data/session")
            .then()
            .statusCode(200)
            .body("authenticated", equalTo(true))
            .body("role", equalTo("admin"))
            .body("familyId", equalTo("family-1"))
            .body("email", equalTo("parent@example.com"))
            .body("csrfToken", equalTo("csrf-123"));
    }
}