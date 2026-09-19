package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.web.dtos.NotificationSenderForm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoreNotificationTemplateTest {

    private Store store() {
        Store store = new Store();
        store.setName("Sklep Demo");
        ClientNotificationsConfiguration configuration = new ClientNotificationsConfiguration();
        configuration.setSenderName("Sklep Demo");
        configuration.setReplyToEmail("kontakt@sklep-demo.pl");
        configuration.enableNotification(EmailNotificationType.ORDER_SHIPPING, "OrderShippingTemplate");
        store.setClientNotificationsConfiguration(configuration);
        return store;
    }

    private Map<String, Object> page(Store store, NotificationSenderForm form, Map<String, String> errors) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", SettingsPage.forRequest(UserRole.ADMIN, "/dashboard/store/notification"));
        variables.put("navigation", null);
        variables.put("form", form);
        variables.put("errors", errors);
        variables.put("formAction", "/dashboard/store/notification");
        variables.put("senderPreviewName", "Sklep Demo");
        variables.put("senderEmail", "noreply@commercelink.pl");
        variables.put("templatesHref", "/dashboard/store/email-templates");
        variables.put("fulfilmentHref", "/dashboard/store/fulfilment");
        variables.put("overview", NotificationOverview.of(store, "/dashboard/store/email-templates"));
        return variables;
    }

    @Test
    void showsTheSenderFieldsAsOptionalWithWhatTheCustomerSees() {
        // given
        Store store = store();

        // when
        String html = SettingsTemplateRenderer.render("store-notification", page(store, NotificationSenderForm.from(store), Map.of()));

        // then
        assertThat(html).contains("<h1 class=\"cl-page-title\">Powiadomienia</h1>");
        assertThat(html).contains("action=\"/dashboard/store/notification\"").contains("data-cl-async");
        assertThat(html).contains(">Nadawca e-maili</legend>");
        assertThat(html).contains("value=\"Sklep Demo\"").contains("value=\"kontakt@sklep-demo.pl\"");
        assertThat(html).contains("Klient zobaczy nadawcę: Sklep Demo &lt;noreply@commercelink.pl&gt;");
        assertThat(html).contains("type=\"email\"").contains("inputmode=\"email\"").contains("autocomplete=\"email\"");
        assertThat(html).contains("aria-describedby=\"senderName-help\"");
        assertThat(html.split("class=\"cl-optional\"", -1)).hasSize(3);
        assertThat(html).doesNotContain("??").doesNotContain("store.storeId").doesNotContain("type=\"checkbox\"");
    }

    @Test
    void listsCustomerEmailsByNameWithTheirStateAndTemplateLink() {
        // given
        Store store = store();

        // when
        String html = SettingsTemplateRenderer.render("store-notification", page(store, NotificationSenderForm.from(store), Map.of()));

        // then
        assertThat(html).contains("Wysyłane: 1 z 19.");
        assertThat(html).contains("Zamówienie wysłane").contains("Kod weryfikacyjny").doesNotContain(">ORDER_SHIPPING<");
        assertThat(html).containsPattern("Zamówienie wysłane</span>\\s*<span class=\"cl-status is-ok\">Wysyłane</span>");
        assertThat(html).containsPattern("Faktura proforma</span>\\s*<span class=\"cl-status is-neutral\">Wyłączone</span>");
        assertThat(html).contains("href=\"/dashboard/store/email-templates/ORDER_SHIPPING\"");
        assertThat(html).contains("aria-label=\"Edytuj szablon: Zamówienie wysłane\"");
        assertThat(html).contains("Wymagane do zmiany adresu dostawy przez klienta");
        assertThat(html).doesNotContain("cl-alert is-warn");
    }

    @Test
    void warnsWhenTheCustomerAddressChangeCannotWork() {
        // given
        Store store = store();
        FulfilmentConfiguration fulfilment = new FulfilmentConfiguration();
        fulfilment.setClientShippingAddressChangeEnabled(true);
        store.setFulfilmentConfiguration(fulfilment);

        // when
        String html = SettingsTemplateRenderer.render("store-notification", page(store, NotificationSenderForm.from(store), Map.of()));

        // then
        assertThat(html).contains("cl-alert is-warn").contains("Klient nie może zmienić adresu dostawy")
                .contains("href=\"/dashboard/store/fulfilment\"");
    }

    @Test
    void anInvalidReplyToAddressIsListedAboveTheFormAndNextToTheField() {
        // given
        Store store = store();
        NotificationSenderForm form = new NotificationSenderForm();
        form.setReplyToEmail("kontakt");

        // when
        String html = SettingsTemplateRenderer.render("store-notification",
                page(store, form, Map.of("replyToEmail", "store.notification.replyToEmail.invalid")));

        // then
        assertThat(html).contains("id=\"notification-sender-errors\"").contains("href=\"#replyToEmail\"");
        assertThat(html).contains("aria-invalid=\"true\"").contains("aria-describedby=\"replyToEmail-help replyToEmail-error\"");
        assertThat(html).contains("Podaj adres e-mail w formacie nazwa@domena.pl.");
    }

    @Test
    void theSenderFormRendersOnItsOwnForAsyncSaves() {
        // given
        Store store = store();
        Map<String, Object> variables = page(store, NotificationSenderForm.from(store), Map.of());
        variables.put("savedMessage", "Zapisano");

        // when
        String html = SettingsTemplateRenderer.render(
                "<div th:replace=\"~{store-notification :: senderForm}\"></div>", variables);

        // then
        assertThat(html).startsWith("<form").contains("id=\"notification-sender-form\"").contains("data-success-message=\"Zapisano\"");
        assertThat(html).doesNotContain("Wiadomości do klientów");
    }

    @Test
    void dropsBulmaMarkupTheSaveConfirmationAndTheOldEditEndpoint() throws Exception {
        // when
        String template = Files.readString(Path.of("src/main/resources/templates/store-notification.html"), StandardCharsets.UTF_8);

        // then
        assertThat(template).doesNotContain("confirmSave").doesNotContain("/notification/edit")
                .doesNotContain("class=\"box\"").doesNotContain("\"button is-primary").doesNotContain("style=");
        assertThat(template).contains("cl-button is-primary");
    }
}
