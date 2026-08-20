package com.sashplatonov.earnit.kids.util;

import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.dto.response.SimpleResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class OperationResultResponsesTest {

    @Test
    void toOk_success_returns200WithValue() {
        Response response = OperationResultResponses.toOk(OperationResult.success("saved"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo("saved");
    }

    @Test
    void toOk_failureWithErrorCode_returns400WithCode() {
        Response response = OperationResultResponses.toOk(OperationResult.failure("FAMILY_NOT_FOUND", "No family"));

        assertThat(response.getStatus()).isEqualTo(400);
        ErrorResponse entity = (ErrorResponse) response.getEntity();
        assertThat(entity.errorCode()).isEqualTo("FAMILY_NOT_FOUND");
        assertThat(entity.detail()).isEqualTo("No family");
    }

    @Test
    void toOk_failureWithNullErrorCode_defaultsToBadRequest() {
        Response response = OperationResultResponses.toOk(OperationResult.failure("Something went wrong"));

        assertThat(response.getStatus()).isEqualTo(400);
        ErrorResponse entity = (ErrorResponse) response.getEntity();
        assertThat(entity.errorCode()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void toVoidOk_success_returns200WithSimpleResponse() {
        Response response = OperationResultResponses.toVoidOk(OperationResult.success(null));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(SimpleResponse.ok());
    }

    @Test
    void toCreated_success_returns201WithLocation() {
        URI location = URI.create("/api/families/1");
        Response response = OperationResultResponses.toCreated(OperationResult.success("created"), location);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getLocation()).isEqualTo(location);
        assertThat(response.getEntity()).isEqualTo("created");
    }

    @Test
    void toOk_customFailureStatusResolver_returns404() {
        Response response = OperationResultResponses.toOk(
            OperationResult.failure("NOT_FOUND", "Missing"),
            failure -> 404);

        assertThat(response.getStatus()).isEqualTo(404);
        ErrorResponse entity = (ErrorResponse) response.getEntity();
        assertThat(entity.errorCode()).isEqualTo("NOT_FOUND");
        assertThat(entity.detail()).isEqualTo("Missing");
    }

    @Test
    void errorCodeOrBadRequest_returnsCodeWhenPresent() {
        assertThat(OperationResultResponses.errorCodeOrBadRequest("CODE")).isEqualTo("CODE");
    }

    @Test
    void errorCodeOrBadRequest_returnsBadRequestWhenNull() {
        assertThat(OperationResultResponses.errorCodeOrBadRequest(null)).isEqualTo("BAD_REQUEST");
    }

    @Test
    void toResponse_usesResourceOwnedSuccessCallback() {
        Response response = OperationResultResponses.toResponse(
            OperationResult.success("payload"),
            value -> Response.status(201).entity(value).build(),
            failure -> Response.serverError().build());

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getEntity()).isEqualTo("payload");
    }

    @Test
    void toResponse_usesResourceOwnedFailureCallback() {
        Response response = OperationResultResponses.toResponse(
            OperationResult.failure("failed"),
            value -> Response.ok(value).build(),
            failure -> Response.status(303).header("Location", "/login").build());

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getHeaderString("Location")).isEqualTo("/login");
    }
}
