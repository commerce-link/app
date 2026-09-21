package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.templates.EmailTemplatesRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.NotificationSenderForm;
import pl.commercelink.web.settings.NotificationOverview;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.commercelink.testsupport.SecurityContextLogin.logInAs;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreNotificationSettingsControllerTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;

    @Mock
    private EmailTemplatesRepository emailTemplatesRepository;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreNotificationSettingsController controller;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        store.setName("Sklep " + storeId);
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    private NotificationSenderForm form(String senderName, String replyToEmail) {
        NotificationSenderForm form = new NotificationSenderForm();
        form.setSenderName(senderName);
        form.setReplyToEmail(replyToEmail);
        return form;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> errors(ExtendedModelMap model) {
        return (Map<String, String>) model.get("errors");
    }

    @Test
    void rendersTheSenderFormAndTheOverviewOfCustomerMessages() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.notification(model);

        // then
        assertThat(view).isEqualTo("store-notification");
        assertThat(model.get("form")).isInstanceOf(NotificationSenderForm.class);
        assertThat(model.get("formAction")).isEqualTo("/dashboard/store/notification");
        assertThat(((NotificationOverview) model.get("overview")).totalCount()).isEqualTo(EmailNotificationType.values().length);
        assertThat(model.get("senderPreviewName")).isEqualTo("Sklep store-1");
        assertThat(errors(model)).isEmpty();
    }

    @Test
    void anUnknownStoreIsNotFound() {
        // when / then
        assertThatThrownBy(() -> controller.superAdminNotification("missing", new ExtendedModelMap()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void storeAdminAlwaysSavesTheStoreFromTheirSession() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        store("other-store");
        when(messageSource.getMessage(eq("store.notification.update.success"), any(), eq(POLISH))).thenReturn("Zapisano");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.saveNotification(form("Sklep Demo", "kontakt@sklep-demo.pl"), null, new ExtendedModelMap(),
                POLISH, redirect, new MockHttpServletResponse());

        // then
        ArgumentCaptor<Store> saved = ArgumentCaptor.forClass(Store.class);
        verify(storesRepository).save(saved.capture());
        assertThat(saved.getValue().getStoreId()).isEqualTo("store-1");
        assertThat(saved.getValue().getClientNotificationsConfiguration().getSenderName()).isEqualTo("Sklep Demo");
        verify(storesRepository, never()).findById("other-store");
        assertThat(view).isEqualTo("redirect:/dashboard/store/notification");
        assertThat(redirect.getFlashAttributes().get("settingsSavedMessage")).isEqualTo("Zapisano");
    }

    @Test
    void superAdminSavesTheStoreFromThePathAndReturnsToItsPage() {
        // given
        logInAs("SUPER_ADMIN", "none");
        Store store = store("store-9");

        // when
        String view = controller.superAdminSaveNotification("store-9", form("Sklep 9", null), null, new ExtendedModelMap(),
                POLISH, new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-9/notification");
    }

    @Test
    void anInvalidFormIsNotSavedAndComesBackWithItsErrors() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.saveNotification(form("Sklep", "not-an-email"), null, model, POLISH,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        verify(storesRepository, never()).save(any());
        assertThat(view).isEqualTo("store-notification");
        assertThat(errors(model)).containsKey("replyToEmail");
        assertThat(((NotificationSenderForm) model.get("form")).getReplyToEmail()).isEqualTo("not-an-email");
    }

    @Test
    void asyncSaveAnswersWithTheFormAndTheSuccessMessage() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        store.setClientNotificationsConfiguration(new ClientNotificationsConfiguration());
        when(messageSource.getMessage(eq("store.notification.update.success"), any(), eq(POLISH))).thenReturn("Zapisano");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.saveNotification(form(" Sklep Demo ", ""), "fetch", model, POLISH,
                new RedirectAttributesModelMap(), response);

        // then
        assertThat(view).isEqualTo("store-notification :: senderForm");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(model.get("savedMessage")).isEqualTo("Zapisano");
        assertThat(((NotificationSenderForm) model.get("form")).getSenderName()).isEqualTo("Sklep Demo");
    }

    @Test
    void asyncSaveWithErrorsAnswersUnprocessable() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.saveNotification(form("Sklep <x>", null), "fetch", new ExtendedModelMap(), POLISH,
                new RedirectAttributesModelMap(), response);

        // then
        assertThat(view).isEqualTo("store-notification :: senderForm");
        assertThat(response.getStatus()).isEqualTo(422);
        verify(storesRepository, never()).save(any());
    }
}
