package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.sashplatonov.earnit.kids.telegram.config.TelegramConfig;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestType;
import com.sashplatonov.earnit.kids.family.api.response.RequestDto;
import com.sashplatonov.earnit.kids.telegram.api.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.telegram.application.identity.TelegramIdentityService;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramReplyKeyboardNavigatorTest {
    @Test
    void editorCannotOpenOrSubmitLanguageAction() throws Exception {
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        FamilyRepository families = mock(FamilyRepository.class);
        FamilyParentMembershipRepository memberships = mock(FamilyParentMembershipRepository.class);
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        when(quickActions.load(77L, null)).thenReturn(Optional.of(parentView()));
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.of(
            new TelegramIdentityService.TelegramIdentity(1, 7, null, 77L, "parent", 9)));
        FamilyParentMembershipEntity membership = mock(FamilyParentMembershipEntity.class);
        when(membership.getPermission()).thenReturn(FamilyParentMembershipEntity.Permission.editor);
        when(memberships.findByParentAndFamily(9, 7)).thenReturn(Optional.of(membership));

        TelegramReplyKeyboardNavigator navigator = navigator(quickActions, null, config(), apiClient, families,
            memberships, identities);
        navigator.handle(message(TelegramCopy.language(FamilyLocale.ru)), 44L, 77L);
        navigator.handle(message(TelegramCopy.languageEnglish(FamilyLocale.ru)), 44L, 77L);

        verify(families, never()).updateLocale(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        verify(apiClient).sendMessage(eq(44L), eq(TelegramCopy.languageError(FamilyLocale.en)), eq(List.of()));
        verify(apiClient, never()).sendMessageWithReplyKeyboard(eq(44L), eq(TelegramCopy.languagePrompt(FamilyLocale.ru)),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void familyAdminCanOpenLanguageActionAndPersistAgainstIdentityFamily() throws Exception {
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        FamilyRepository families = mock(FamilyRepository.class);
        FamilyParentMembershipRepository memberships = mock(FamilyParentMembershipRepository.class);
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        when(quickActions.load(77L, null)).thenReturn(Optional.of(parentView()));
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.of(
            new TelegramIdentityService.TelegramIdentity(1, 7, null, 77L, "parent", 9)));
        FamilyParentMembershipEntity membership = mock(FamilyParentMembershipEntity.class);
        when(membership.getPermission()).thenReturn(FamilyParentMembershipEntity.Permission.family_admin);
        when(memberships.findByParentAndFamily(9, 7)).thenReturn(Optional.of(membership));
        when(families.updateLocale("7", FamilyLocale.en)).thenReturn(true);

        TelegramReplyKeyboardNavigator navigator = navigator(quickActions, null, config(), apiClient, families,
            memberships, identities);
        navigator.handle(message(TelegramCopy.language(FamilyLocale.ru)), 44L, 77L);
        navigator.handle(message(TelegramCopy.languageEnglish(FamilyLocale.ru)), 44L, 77L);

        verify(families).updateLocale("7", FamilyLocale.en);
        verify(apiClient).sendMessageWithReplyKeyboard(eq(44L), eq(TelegramCopy.languageUpdated(FamilyLocale.en)),
            org.mockito.ArgumentMatchers.any());
    }
    @Test
    void parentLanguageActionSendsExactlyTwoLocalizedChoices() throws Exception {
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        when(quickActions.load(77L, null)).thenReturn(Optional.of(parentView()));

        navigator(quickActions, null, config(), apiClient).handle(
            message(TelegramCopy.language(FamilyLocale.ru)), 44L, 77L);

        verify(apiClient).sendMessageWithReplyKeyboard(eq(44L), eq(TelegramCopy.languagePrompt(FamilyLocale.ru)),
            argThat(keyboard -> keyboard.rows().size() == 2
                && keyboard.rows().get(0).buttons().get(0).label().equals(TelegramCopy.languageEnglish(FamilyLocale.ru))
                && keyboard.rows().get(1).buttons().get(0).label().equals(TelegramCopy.languageRussian(FamilyLocale.ru))));
    }

    @Test
    void parentLanguageChoicePersistsAndRefreshesKeyboardInSelectedLocale() throws Exception {
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository families =
            mock(com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository.class);
        when(quickActions.load(77L, null)).thenReturn(Optional.of(parentView()));
        when(families.updateLocale("family", FamilyLocale.en)).thenReturn(true);
        navigator(quickActions, null, config(), apiClient, families).handle(
            message(TelegramCopy.languageEnglish(FamilyLocale.ru)), 44L, 77L);

        verify(families).updateLocale("family", FamilyLocale.en);
        verify(apiClient).sendMessageWithReplyKeyboard(eq(44L), eq(TelegramCopy.languageUpdated(FamilyLocale.en)),
            argThat(keyboard -> keyboard.rows().get(2).buttons().get(0).label().equals(TelegramCopy.language(FamilyLocale.en))));
    }

    @Test
    void childLanguageChoiceDoesNotMutateFamily() throws Exception {
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository families =
            mock(com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository.class);
        when(quickActions.load(77L, null)).thenReturn(Optional.of(view(FamilyLocale.ru, "child")));

        navigator(quickActions, null, config(), apiClient, families).handle(
            message(TelegramCopy.languageEnglish(FamilyLocale.ru)), 44L, 77L);

        verify(families, never()).updateLocale(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void parentRequestsNavigationSendsDecisionButtons() throws Exception {
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramConfig config = config();
        TelegramQuickActionResponse view = parentView();
        List<TelegramBotApiClient.InlineButton> buttons = List.of(
            TelegramBotApiClient.InlineButton.callback(TelegramCopy.approve(com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale.ru), "approve"),
            TelegramBotApiClient.InlineButton.callback(TelegramCopy.reject(com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale.ru), "reject"));
        when(quickActions.load(77L, null)).thenReturn(Optional.of(view));
        when(menuBuilder.parentRequestQueue(view, null)).thenReturn(buttons);

        navigator(quickActions, menuBuilder, config, apiClient).handle(
            message(TelegramCopy.requests(com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale.ru)), 44L, 77L);

        verify(apiClient).sendMessage(eq(44L), eq(TelegramCopy.requestQueueText(
            "Alex", "Morning task", 2, true, 1, 1)), eq(buttons));
    }

    @Test
    void parentCoinsNavigationSendsCoinButtons() throws Exception {
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramConfig config = config();
        TelegramQuickActionResponse view = parentView();
        List<TelegramBotApiClient.InlineButton> buttons = List.of(
            TelegramBotApiClient.InlineButton.callback(TelegramCopy.coinAdd(1), "add"));
        when(quickActions.load(77L, null)).thenReturn(Optional.of(view));
        when(menuBuilder.parentCoins(view, "https://mini.example.test")).thenReturn(buttons);

        navigator(quickActions, menuBuilder, config, apiClient).handle(
            message(TelegramCopy.coins(com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale.ru)), 44L, 77L);

        verify(apiClient).sendMessage(eq(44L), eq(TelegramCopy.parentCoins("Alex", 20)), eq(buttons));
    }

    @Test
    void parentSelectChildNavigationSendsChildPickerButtons() throws Exception {
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramConfig config = config();
        TelegramQuickActionResponse view = parentView();
        List<TelegramBotApiClient.InlineButton> buttons = List.of(
            TelegramBotApiClient.InlineButton.callback("👧 Alex · 20", "child"));
        when(quickActions.load(77L, null)).thenReturn(Optional.of(view));
        when(menuBuilder.parentChildPicker(view, "https://site.example.test")).thenReturn(buttons);

        navigator(quickActions, menuBuilder, config, apiClient).handle(
            message(TelegramCopy.switchChild(com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale.ru)), 44L, 77L);

        verify(apiClient).sendMessage(eq(44L), eq(TelegramCopy.chooseChildTitle()), eq(buttons));
    }

    @Test
    void englishSiteReplyUsesFamilyLocaleForMessageAndButton() throws Exception {
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramConfig config = config();
        when(config.publicSiteUrl()).thenReturn(Optional.of(" https://site.example.test/path "));
        when(quickActions.load(77L, null)).thenReturn(Optional.of(view(FamilyLocale.en)));

        navigator(quickActions, null, config, apiClient).handle(
            message(TelegramCopy.site(FamilyLocale.en)), 44L, 77L);

        String label = TelegramCopy.site(FamilyLocale.en);
        verify(apiClient).sendMessage(eq(44L), eq(label), eq(List.of(
            TelegramBotApiClient.InlineButton.url(label, "https://site.example.test", null))));
    }

    @Test
    void russianSiteReplyUsesFamilyLocaleForMessageAndButton() throws Exception {
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        when(quickActions.load(77L, null)).thenReturn(Optional.of(view(FamilyLocale.ru)));

        navigator(quickActions, null, config(), apiClient).handle(
            message(TelegramCopy.site(FamilyLocale.ru)), 44L, 77L);

        String label = TelegramCopy.site(FamilyLocale.ru);
        verify(apiClient).sendMessage(eq(44L), eq(label), eq(List.of(
            TelegramBotApiClient.InlineButton.url(label, "https://site.example.test", null))));
    }

    @Test
    void englishSiteLabelStillRoutesToRussianFamily() throws Exception {
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        when(quickActions.load(77L, null)).thenReturn(Optional.of(view(FamilyLocale.ru)));

        navigator(quickActions, null, config(), apiClient).handle(
            message(TelegramCopy.site(FamilyLocale.en)), 44L, 77L);

        String label = TelegramCopy.site(FamilyLocale.ru);
        verify(apiClient).sendMessage(eq(44L), eq(label), eq(List.of(
            TelegramBotApiClient.InlineButton.url(label, "https://site.example.test", null))));
    }

    @Test
    void unresolvedFamilyUsesNeutralEnglishSiteCopy() throws Exception {
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        when(quickActions.load(77L, null)).thenReturn(Optional.empty());

        navigator(quickActions, null, config(), apiClient).handle(
            message(TelegramCopy.site(FamilyLocale.ru)), 44L, 77L);

        String label = TelegramCopy.site(FamilyLocale.en);
        verify(apiClient).sendMessage(eq(44L), eq(label), eq(List.of(
            TelegramBotApiClient.InlineButton.url(label, "https://site.example.test", null))));
    }

    @Test
    void invalidPublicSiteConfigurationDoesNotSendLink() throws Exception {
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramConfig config = config();
        when(config.publicSiteUrl()).thenReturn(Optional.of("not a URL"));

        navigator(quickActions, null, config, apiClient).handle(
            message(TelegramCopy.site(FamilyLocale.en)), 44L, 77L);

        org.mockito.Mockito.verifyNoInteractions(apiClient, quickActions);
    }

    private static TelegramReplyKeyboardNavigator navigator(TelegramQuickActionService quickActions,
                                                              TelegramMenuBuilder menuBuilder,
                                                              TelegramConfig config,
                                                              TelegramBotApiClient apiClient) {
        return new TelegramReplyKeyboardNavigator(quickActions, menuBuilder, config, apiClient);
    }

    private static TelegramReplyKeyboardNavigator navigator(TelegramQuickActionService quickActions,
                                                              TelegramMenuBuilder menuBuilder,
                                                              TelegramConfig config,
                                                              TelegramBotApiClient apiClient,
                                                              com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository families,
                                                              FamilyParentMembershipRepository memberships,
                                                              TelegramIdentityService identities) {
        return new TelegramReplyKeyboardNavigator(quickActions, menuBuilder, config, apiClient, families, memberships,
            identities);
    }

    private static TelegramReplyKeyboardNavigator navigator(TelegramQuickActionService quickActions,
                                                              TelegramMenuBuilder menuBuilder,
                                                              TelegramConfig config,
                                                              TelegramBotApiClient apiClient,
                                                              com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository families) {
        return new TelegramReplyKeyboardNavigator(quickActions, menuBuilder, config, apiClient, families);
    }

    private static TelegramConfig config() {
        TelegramConfig config = mock(TelegramConfig.class);
        when(config.miniAppUrl()).thenReturn(Optional.of("https://mini.example.test"));
        when(config.publicSiteUrl()).thenReturn(Optional.of("https://site.example.test"));
        return config;
    }

    private static TelegramQuickActionResponse parentView() {
        return view(FamilyLocale.ru);
    }

    private static TelegramQuickActionResponse view(FamilyLocale locale, String role) {
        TelegramQuickActionResponse original = view(locale);
        return new TelegramQuickActionResponse(original.familyId(), role, original.childId(), original.childName(),
            original.balance(), original.children(), original.tasks(), original.rewards(), original.requests(),
            original.history(), original.locale());
    }

    private static TelegramQuickActionResponse view(FamilyLocale locale) {
        RequestDto request = new RequestDto(19L, 2L, "Morning task", null, null, null,
            null, null, null, null, 2, PurchaseRequestStatus.pending, PurchaseRequestType.earn,
            0, "2026-08-20T19:51:00Z", 1, null, null, null, null);
        return new TelegramQuickActionResponse("family", "parent", 1, "Alex", 20,
            List.of(), List.of(), List.of(), List.of(request), List.of(), locale);
    }

    private static JsonNode message(String text) {
        return new ObjectMapper().createObjectNode().put("text", text);
    }
}
