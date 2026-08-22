package com.sashplatonov.earnit.kids.service.auth;

import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.domain.model.MembershipStatus;
import com.sashplatonov.earnit.kids.domain.model.ParentAccountEntity;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.ParentAccountRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;


@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
class AuthLifecycleService {
    private final FamilyRepository familyRepository;
    private final ParentAccountRepository parentAccountRepository;
    private final FamilyParentMembershipRepository membershipRepository;
    private final AuthSupportService supportService;

    @Transactional
    OperationResult<AuthPayload> registerFamily(String email, String adminPassword) {
        if (parentAccountRepository.findByEmail(email).isPresent()) {
            return OperationResult.failure(BackendMessages.message("auth.emailRegistered"));
        }
        if (!supportService.isValidPassword(adminPassword)) {
            return OperationResult.failure(BackendMessages.message("auth.weakParentPassword"));
        }

        String familyId = "fam_" + supportService.generateHexToken(16);
        String hashedPassword = supportService.hashPassword(adminPassword);

        try {
            var parent = ParentAccountEntity.builder()
                .email(email)
                .passwordHash(hashedPassword)
                .build();
            parentAccountRepository.persistAndFlush(parent);

            var family = FamilyEntity.builder()
                .familyId(familyId)
                .email(email)
                .adminPassword(hashedPassword)
                .build();
            familyRepository.persistAndFlush(family);

            var membership = FamilyParentMembershipEntity.builder()
                .parentAccountId(parent.getId())
                .familyId(family.getId())
                .permission(FamilyParentMembershipEntity.Permission.family_admin)
                .status(MembershipStatus.active)
                .build();
            membershipRepository.persistAndFlush(membership);

            return OperationResult.success(
                new AuthPayload(familyId, email, "admin", null, null, "family_admin", null, false));
        } catch (Exception ex) {
            return OperationResult.failure(BackendMessages.message("auth.registrationFailed"));
        }
    }

    OperationResult<Void> changeAdminPassword(String familyId, String oldPassword, String newPassword) {
        if (familyId == null || familyId.isBlank()) {
            return OperationResult.failure(BackendMessages.message("auth.familyNotFound"));
        }
        if (!supportService.isValidPassword(newPassword)) {
            return OperationResult.failure(BackendMessages.message("auth.weakPassword"));
        }

        var familyOpt = familyRepository.findById(familyId);
        if (familyOpt.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("auth.familyNotFound"));
        }

        var family = familyOpt.get();
        if (!supportService.isPasswordValid(family.getEmail(), oldPassword, family.getAdminPassword())) {
            return OperationResult.failure(BackendMessages.message("auth.invalidPassword"));
        }
        if (oldPassword != null && oldPassword.equals(newPassword)) {
            return OperationResult.failure(BackendMessages.message("auth.newPasswordMustDiffer"));
        }

        String newHash = supportService.hashPassword(newPassword);
        boolean updated = familyRepository.updatePassword(familyId, newHash);
        if (!updated) {
            return OperationResult.failure(BackendMessages.message("auth.passwordUpdateFailed"));
        }

        return OperationResult.success(null);
    }

}
