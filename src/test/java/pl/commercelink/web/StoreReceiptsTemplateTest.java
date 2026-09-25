package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.Context;
import pl.commercelink.web.dtos.ReceiptSettingsForm;
import pl.commercelink.web.settings.IntegrationStatus;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The "Automatyczne e-paragony" form (checkbox: automatic e-receipts) is only ever usable with a configured
 *  system -- saving it otherwise is rejected server-side ({@code store.receipts.enabled.noSystem}) -- so it is
 *  shown only then; every other state leaves only the status card and its "Wybierz system" / "Uzupełnij" / "Zmień"
 *  action. */
class StoreReceiptsTemplateTest {

    @Test
    void theFormIsHiddenWithoutAConfiguredSystem() {
        String html = render(new IntegrationStatus(null, null, false, false));

        assertThat(html).doesNotContain("id=\"receipts-form\"");
    }

    @Test
    void theFormIsHiddenWhenTheSystemIsChosenButNotConfigured() {
        String html = render(new IntegrationStatus("test-receipts", "Test receipts", true, false));

        assertThat(html).doesNotContain("id=\"receipts-form\"");
    }

    @Test
    void theFormIsShownOnceTheSystemIsConfigured() {
        String html = render(new IntegrationStatus("test-receipts", "Test receipts", true, true));

        assertThat(html).contains("id=\"receipts-form\"");
    }

    /** The async save (both the settings page's own POST and the system subpage's redirect-with-flash) re-renders
     *  this exact fragment selector; it must still produce the checkbox once the system is configured. */
    @Test
    void theStandaloneFragmentStillRendersOnceConfigured() {
        String html = renderFragment(new IntegrationStatus("test-receipts", "Test receipts", true, true));

        assertThat(html).contains("id=\"receipts-form\"").contains("type=\"checkbox\"").contains("id=\"enabled\"");
    }

    /** A stale form (enabled=true) submitted after the system was disconnected in another tab still reaches
     *  saveReceipts with the noSystem error, but re-renders with systemStatus.configured()==false: the error
     *  summary lives inside the now-hidden form, so the focus script must not run getElementById().focus() on it
     *  (it would return null and throw). */
    @Test
    void theFocusScriptIsAbsentWhenTheFormIsHiddenEvenWithErrors() {
        String html = render(new IntegrationStatus(null, null, false, false), Map.of("enabled", "store.receipts.enabled.noSystem"));

        assertThat(html).doesNotContain("id=\"receipts-form\"").doesNotContain("receipts-errors').focus()");
    }

    private static String render(IntegrationStatus systemStatus) {
        return render(systemStatus, Map.of());
    }

    private static String render(IntegrationStatus systemStatus, Map<String, String> receiptsErrors) {
        Context context = context(systemStatus, receiptsErrors);
        return EnglishFragmentTemplateEngine.create().process("store-receipts", context);
    }

    private static String renderFragment(IntegrationStatus systemStatus) {
        Context context = context(systemStatus, Map.of());
        TemplateSpec spec = new TemplateSpec("store-receipts", Set.of("receiptsForm"), (String) null, null);
        return EnglishFragmentTemplateEngine.create().process(spec, context);
    }

    private static Context context(IntegrationStatus systemStatus, Map<String, String> receiptsErrors) {
        ReceiptSettingsForm form = new ReceiptSettingsForm();
        form.setEnabled(false);
        Context context = new Context();
        context.setVariable("systemStatus", systemStatus);
        context.setVariable("systemHref", "/dashboard/store/receipts/system");
        context.setVariable("disconnectHref", "/dashboard/store/receipts/system/disconnect");
        context.setVariable("receiptsForm", form);
        context.setVariable("receiptsErrors", receiptsErrors);
        context.setVariable("receiptsAction", "/dashboard/store/receipts");
        context.setVariable("savedMessage", null);
        return context;
    }
}
