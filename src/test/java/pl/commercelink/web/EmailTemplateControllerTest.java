package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.templates.EmailTemplate;
import pl.commercelink.templates.EmailTemplatesRepository;
import pl.commercelink.web.dtos.EmailTemplateForm;
import pl.commercelink.web.settings.EmailTemplateView;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.commercelink.testsupport.SecurityContextLogin.logInAs;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailTemplateControllerTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");
    private static final String NAME = EmailNotificationType.ORDER_SHIPPING.getTemplateName();

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private EmailTemplatesRepository emailTemplatesRepository;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private EmailTemplateController controller;

    @BeforeEach
    void messagesAreTheirKeys() {
        when(messageSource.getMessage(anyString(), any(), eq(POLISH))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageSource.getMessage(anyString(), any(), anyString(), eq(POLISH))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        store.setClientNotificationsConfiguration(new ClientNotificationsConfiguration());
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    private static EmailTemplate template(String storeId, String subject, String body) {
        EmailTemplate template = new EmailTemplate();
        template.setStoreId(storeId);
        template.setTemplateName(NAME);
        template.setType(EmailNotificationType.ORDER_SHIPPING);
        template.setSubject(subject);
        template.setTextBody(body);
        return template;
    }

    private static EmailTemplateForm form(boolean enabled, String subject, String body) {
        EmailTemplateForm form = EmailTemplateForm.empty(enabled);
        form.setSubject(subject);
        form.setTextBody(body);
        return form;
    }

    private String save(EmailTemplateForm form, String requestedWith, ExtendedModelMap model, MockHttpServletResponse response) {
        return controller.saveTemplate("ORDER_SHIPPING", form, requestedWith, model, POLISH, new RedirectAttributesModelMap(),
                new MockHttpServletRequest(), response);
    }

    @Test
    void savingChangedContentWritesTheStoresOwnCopyOfThatEmailOnly() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        when(emailTemplatesRepository.findByTemplateName("default", NAME)).thenReturn(template("default", "Wysłane", "Treść"));
        ArgumentCaptor<EmailTemplate> saved = ArgumentCaptor.forClass(EmailTemplate.class);

        // when
        String view = save(form(true, "Wysłane {{orderId}}", "Nowa treść"), null, new ExtendedModelMap(), new MockHttpServletResponse());

        // then
        verify(emailTemplatesRepository).save(saved.capture());
        assertThat(saved.getAllValues()).singleElement().satisfies(template -> {
            assertThat(template.getStoreId()).isEqualTo("store-1");
            assertThat(template.getTemplateName()).isEqualTo(NAME);
            assertThat(template.getTextBody()).isEqualTo("Nowa treść");
        });
        assertThat(store.supportsNotification(EmailNotificationType.ORDER_SHIPPING)).isTrue();
        assertThat(view).isEqualTo("redirect:/dashboard/store/email-templates");
    }

    @Test
    void contentEqualToTheDefaultIsNotCopiedIntoTheStore() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        when(emailTemplatesRepository.findByTemplateName("default", NAME)).thenReturn(template("default", "Wysłane", "Treść"));

        // when
        save(form(true, "Wysłane", "Treść"), null, new ExtendedModelMap(), new MockHttpServletResponse());

        // then
        verify(emailTemplatesRepository, never()).save(any());
        verify(storesRepository).save(store);
        assertThat(store.supportsNotification(EmailNotificationType.ORDER_SHIPPING)).isTrue();
    }

    @Test
    void anEmailSentWithoutContentIsRejectedWithItsErrors() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = save(form(true, "", ""), "fetch", model, response);

        // then
        verify(storesRepository, never()).save(any());
        verify(emailTemplatesRepository, never()).save(any());
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(view).isEqualTo("store-email-template :: templateForm");
        assertThat(model.get("source")).isEqualTo(EmailTemplateView.Source.NONE);
    }

    @Test
    void switchingOffKeepsTheContentAndStopsTheEmail() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        store.getClientNotificationsConfiguration().enableNotification(EmailNotificationType.ORDER_SHIPPING, NAME);
        EmailTemplate own = template("store-1", "Wysłane", "Treść");
        when(emailTemplatesRepository.findByTemplateName("store-1", NAME)).thenReturn(own);

        // when
        save(form(false, "Wysłane", "Treść"), null, new ExtendedModelMap(), new MockHttpServletResponse());

        // then
        verify(emailTemplatesRepository).save(own);
        assertThat(store.supportsNotification(EmailNotificationType.ORDER_SHIPPING)).isFalse();
    }

    @Test
    void switchingOffWithAnEmptyFormLeavesTheStoresCopyAsItWas() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        store.getClientNotificationsConfiguration().enableNotification(EmailNotificationType.ORDER_SHIPPING, NAME);
        EmailTemplate own = template("store-1", "Wysłane", "Treść");
        when(emailTemplatesRepository.findByTemplateName("store-1", NAME)).thenReturn(own);

        // when
        save(form(false, "", ""), null, new ExtendedModelMap(), new MockHttpServletResponse());

        // then
        verify(emailTemplatesRepository, never()).save(any(EmailTemplate.class));
        assertThat(own.getSubject()).isEqualTo("Wysłane");
        assertThat(own.getTextBody()).isEqualTo("Treść");
        assertThat(store.supportsNotification(EmailNotificationType.ORDER_SHIPPING)).isFalse();
    }

    @Test
    void anEnabledEmailWithoutAnyTemplateOpensInsteadOfFailing() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        store.getClientNotificationsConfiguration().enableNotification(EmailNotificationType.ORDER_ASSEMBLY, "OrderAssemblyTemplate");
        when(emailTemplatesRepository.findAllOfStore(anyString())).thenReturn(List.of());
        ExtendedModelMap listModel = new ExtendedModelMap();
        ExtendedModelMap editModel = new ExtendedModelMap();

        // when
        String list = controller.templates(null, listModel);
        String edit = controller.editTemplate("ORDER_ASSEMBLY", editModel, POLISH);

        // then
        assertThat(list).isEqualTo("store-email-templates");
        assertThat(listModel.get("anyBroken")).isEqualTo(true);
        assertThat(edit).isEqualTo("store-email-template");
        assertThat(editModel.get("source")).isEqualTo(EmailTemplateView.Source.NONE);
    }

    @Test
    void aDefaultTemplateOfATypeRemovedFromTheEnumIsLeftOutOfTheList() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        EmailTemplate removedType = template(EmailTemplatesRepository.DEFAULT_STORE, "Rejected", "Body");
        removedType.setTemplateName("RMAItemsRejectedTemplate");
        removedType.setTypeName("RMA_ITEMS_REJECTED");
        when(emailTemplatesRepository.findAllOfStore("store-1")).thenReturn(List.of());
        when(emailTemplatesRepository.findAllOfStore(EmailTemplatesRepository.DEFAULT_STORE))
                .thenReturn(List.of(template(EmailTemplatesRepository.DEFAULT_STORE, "Shipped", "Body"), removedType));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.templates(null, model);

        // then
        assertThat(view).isEqualTo("store-email-templates");
        assertThat(model.get("totalCount")).isEqualTo(EmailNotificationType.values().length);
    }

    @Test
    void theAddressChangeEmailsWarnThatCustomersNeedThemWhileTheFeatureIsOn() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        FulfilmentConfiguration fulfilment = new FulfilmentConfiguration();
        fulfilment.setClientShippingAddressChangeEnabled(true);
        store.setFulfilmentConfiguration(fulfilment);
        ExtendedModelMap required = new ExtendedModelMap();
        ExtendedModelMap other = new ExtendedModelMap();

        // when
        controller.editTemplate("CLIENT_VERIFICATION_CODE", required, POLISH);
        controller.editTemplate("ORDER_SHIPPING", other, POLISH);

        // then
        assertThat(required.get("requiredForAddressChange")).isEqualTo(true);
        assertThat(other.get("requiredForAddressChange")).isEqualTo(false);
    }

    @Test
    void anOldLinkToASelectedTypeOpensItsPage() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");

        // when / then
        assertThat(controller.templates("ORDER_SHIPPING", new ExtendedModelMap()))
                .isEqualTo("redirect:/dashboard/store/email-templates/ORDER_SHIPPING");
    }

    @Test
    void anUnknownTypeIsNotFound() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");

        // when / then
        assertThatThrownBy(() -> controller.editTemplate("NOT_A_TYPE", new ExtendedModelMap(), POLISH))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void restoringDeletesTheStoresCopyOnlyWhenADefaultExists() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        EmailTemplate own = template("store-1", "Własny", "Treść");
        when(emailTemplatesRepository.findByTemplateName("store-1", NAME)).thenReturn(own);

        // when
        controller.restoreDefault("ORDER_SHIPPING", POLISH, new RedirectAttributesModelMap());
        when(emailTemplatesRepository.findByTemplateName("default", NAME)).thenReturn(template("default", "D", "D"));
        String view = controller.restoreDefault("ORDER_SHIPPING", POLISH, new RedirectAttributesModelMap());

        // then
        verify(emailTemplatesRepository).delete(own);
        assertThat(view).isEqualTo("redirect:/dashboard/store/email-templates/ORDER_SHIPPING");
    }

    @Test
    void superAdminSavesTheEmailOfTheStoreInThePath() {
        // given
        logInAs("SUPER_ADMIN", "own-store");
        Store store = store("store-7");

        // when
        String view = controller.superAdminSaveTemplate("store-7", "ORDER_SHIPPING", form(true, "S", "B"), null,
                new ExtendedModelMap(), POLISH, new RedirectAttributesModelMap(), new MockHttpServletRequest(),
                new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-7/email-templates");
    }
}
