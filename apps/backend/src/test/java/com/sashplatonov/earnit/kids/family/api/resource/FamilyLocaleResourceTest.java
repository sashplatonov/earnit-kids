package com.sashplatonov.earnit.kids.family.api.resource;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.family.api.request.UpdateFamilyLocaleRequest;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.shared.api.response.ErrorResponse;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FamilyLocaleResourceTest {
    @Test
    void adminCanNormalizeRegionalLocaleAndReadSetupState() {
        var familyRepository = mock(FamilyRepository.class);
        var family = FamilyEntity.builder().familyId("fam-1").locale(null).build();
        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(familyRepository.updateLocale("fam-1", FamilyLocale.ru)).thenAnswer(invocation -> {
            family.setLocale(FamilyLocale.ru);
            return true;
        });

        var resource = resource(familyRepository, familyAdmin());
        var response = resource.updateFamilyLocale(context(familyAdmin()), new UpdateFamilyLocaleRequest("ru-RU"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(new com.sashplatonov.earnit.kids.family.api.response.FamilyLocaleResponse("ru", false));
    }

    @Test
    void unsupportedLocaleIsRejected() {
        var familyRepository = mock(FamilyRepository.class);
        var auth = familyAdmin();
        var family = FamilyEntity.builder().familyId("fam-1").locale(FamilyLocale.ru).build();
        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        var resource = resource(familyRepository, auth);

        var response = resource.updateFamilyLocale(context(auth), new UpdateFamilyLocaleRequest("russian"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity()).isEqualTo(new ErrorResponse(
            "urn:earnit-kids:problem:unsupported-locale", "Bad Request", 400,
            "Unsupported locale", "UNSUPPORTED_LOCALE", java.util.Map.of("field", "locale"), null));
        assertThat(family.getLocale()).isEqualTo(FamilyLocale.ru);
        org.mockito.Mockito.verify(familyRepository, org.mockito.Mockito.never()).updateLocale("fam-1", FamilyLocale.en);
    }

    @Test
    void nonAdminCannotUpdateLocale() {
        var familyRepository = mock(FamilyRepository.class);
        var auth = new AuthContext("fam-1", null, "admin", "parent@example.com", "csrf", "editor");
        var resource = resource(familyRepository, auth);

        var response = resource.updateFamilyLocale(context(auth), new UpdateFamilyLocaleRequest("ru"));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    private static FamilyReadResource resource(FamilyRepository repository, AuthContext auth) {
        var resource = new FamilyReadResource(null, null);
        resource.familyRepository = repository;
        return resource;
    }

    private static ContainerRequestContext context(AuthContext auth) {
        var context = mock(ContainerRequestContext.class);
        when(context.getProperty("auth.context")).thenReturn(auth);
        return context;
    }

    private static AuthContext familyAdmin() {
        return new AuthContext("fam-1", null, "admin", "admin@example.com", "csrf", "family_admin");
    }
}
