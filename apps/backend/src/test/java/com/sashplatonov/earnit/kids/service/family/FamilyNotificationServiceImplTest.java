package com.sashplatonov.earnit.kids.service.family;

import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyNotificationPreferenceRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyNotificationServiceImplTest {

    @Mock FamilyRepository families;
    @Mock ChildRepository children;
    @Mock FamilyNotificationPreferenceRepository preferences;

    @InjectMocks FamilyNotificationServiceImpl service;

    @Test
    void setPreference_parentScope_normalizesChildIdToNull() {
        when(families.getDbId("fam-1")).thenReturn(Optional.of(1));

        OperationResult<Void> result = service.setPreference("fam-1", "parent", 15, "taskMarkedDone", false);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(preferences).setEnabled(1, "parent", null, "taskMarkedDone", false);
    }

    @Test
    void setPreference_childScope_rejectsForeignChild() {
        when(families.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(children.findByIdOptional(15)).thenReturn(Optional.empty());

        OperationResult<Void> result = service.setPreference("fam-1", "child", 15, "taskApproved", false);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void setPreference_rejectsUnknownParentKey() {
        when(families.getDbId("fam-1")).thenReturn(Optional.of(1));

        OperationResult<Void> result = service.setPreference("fam-1", "parent", null, "doesNotExist", true);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }
}
