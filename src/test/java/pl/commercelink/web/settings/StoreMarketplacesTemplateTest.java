package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.web.dtos.MarketplaceSettingsForm;
import pl.commercelink.web.settings.MarketplaceView.State;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoreMarketplacesTemplateTest {

    private static final String PATH = "/dashboard/store/marketplaces";

    private static MarketplaceView view(String name, String displayName, State state, String returns, List<String> catalogs,
                                        boolean accountPage) {
        String base = PATH + "/" + name;
        return new MarketplaceView(name, displayName, state, "Zamówienia: Co 30 min · ostatnio pobrane 18.09.2026 14:05",
                returns, catalogs, base, accountPage ? base + "/authorize" : null, base + "/disconnect",
                PATH + "/exports/" + name);
    }

    private static String list(List<MarketplaceView> marketplaces, boolean canAdd) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", SettingsPage.forRequest(UserRole.ADMIN, PATH));
        variables.put("navigation", null);
        variables.put("marketplaces", marketplaces);
        variables.put("disconnectMessages", Map.of("Allegro", "Wystawione oferty zostaną na Allegro."));
        variables.put("marketplacesInstalled", true);
        variables.put("newMarketplaceHref", canAdd ? PATH + "/new" : null);
        return SettingsTemplateRenderer.render("store-marketplaces", variables);
    }

    /** The table with a "⋮" menu and the modal with its own script are gone; the page is one list without a form. */
    @Test
    void theListShowsWhatEachMarketplaceDoesWithEditAndDisconnectInTheRow() {
        // when
        String html = list(List.of(view("Allegro", "Allegro", State.ACTIVE, "Zwroty: Domyślny — co 60 min",
                List.of("Główny", "Outlet"), true)), true);

        // then
        assertThat(html).contains("Zamówienia: Co 30 min · ostatnio pobrane 18.09.2026 14:05")
                .contains("Zwroty: Domyślny — co 60 min")
                .contains("Oferty z katalogów: Główny, Outlet")
                .contains("href=\"/dashboard/store/marketplaces/exports/Allegro\"");
        assertThat(html).contains("aria-label=\"Edytuj: Allegro\"").contains("href=\"/dashboard/store/marketplaces/Allegro\"");
        assertThat(html).contains("data-cl-confirm-message=\"Wystawione oferty zostaną na Allegro.\"")
                .contains("href=\"/dashboard/store/marketplaces/Allegro/disconnect\"");
        assertThat(html).contains("href=\"/dashboard/store/marketplaces/new\"").contains(">Dodaj marketplace<");
        assertThat(html).doesNotContain("<table").doesNotContain("marketplaceModal").doesNotContain("dropdown")
                .doesNotContain("data-cl-async").doesNotContain("alert(").doesNotContain("??");
        assertThat(html).contains("/js/confirm-dialog.js");
    }

    @Test
    void anAccountToConnectIsOfferedInPlaceOfTheStatusAndSaysWhatStopped() {
        // when
        String html = list(List.of(view("Allegro", "Allegro", State.NOT_AUTHORIZED, null, List.of("Główny"), true)), true);

        // then
        assertThat(html).contains(">Konto niepołączone<")
                .contains("Zamówienia i zwroty nie są pobierane")
                .contains("aria-label=\"Połącz konto: Allegro\"")
                .contains("href=\"/dashboard/store/marketplaces/Allegro/authorize\"");
    }

    @Test
    void anExpiredKeysOnlyMarketplaceIsCompletedOnItsPage() {
        // when
        String html = list(List.of(view("CsCart", "CS-Cart Multi-Vendor", State.EXPIRED, null, List.of("Główny"), false)), false);

        // then
        assertThat(html).contains(">Połączenie wygasło<").contains("Sprawdź je i zapisz ponownie.").contains(">Uzupełnij<");
        assertThat(html).doesNotContain("/authorize").doesNotContain(">Dodaj marketplace<");
    }

    /** Without a catalog sending offers the row says nothing about catalogs (decision 2026-09-18: no warning). */
    @Test
    void aMarketplaceNoCatalogSendsOffersToHasNoCatalogLine() {
        // when
        String html = list(List.of(view("Allegro", "Allegro", State.ACTIVE, null, List.of(), true)), true);

        // then
        assertThat(html).doesNotContain("Oferty z katalogów").doesNotContain("Żaden katalog").doesNotContain("Historia eksportu");
    }

    @Test
    void aMarketplaceWithoutItsAdapterCanOnlyBeDisconnected() {
        // when
        String html = list(List.of(view("Ceneo", "Ceneo", State.MISSING, null, List.of(), false)), false);

        // then
        assertThat(html).contains(">Niedostępny<").contains("odłącz go")
                .contains("<p class=\"cl-list-desc is-warn\">Integracji tego marketplace");
        assertThat(html).doesNotContain("href=\"/dashboard/store/marketplaces/Ceneo\"").contains("/Ceneo/disconnect");
    }

    @Test
    void anEmptyListSaysSo() {
        // when
        String html = list(List.of(), true);

        // then
        assertThat(html).contains("Nie podłączono jeszcze żadnego marketplace");
    }

    @Test
    void theNewPageShowsEachMarketplacesAccessDetailsAndSchedulesAsVariants() {
        // given
        Map<String, Object> variables = subpage(List.of(allegro(), csCart()), false);

        // when
        String html = SettingsTemplateRenderer.render("store-marketplace", variables);

        // then
        assertThat(html).contains("data-cl-variant-select=\"marketplace\"").contains(">Wybierz marketplace<");
        assertThat(html).contains("name=\"schedules[Allegro.orders]\"").contains("name=\"schedules[Allegro.returns]\"")
                .contains("name=\"schedules[CsCart.orders]\"").doesNotContain("schedules[CsCart.returns]");
        // adapter asterisks and placeholder copies of the label are not shown
        assertThat(html).contains(">Adres sklepu<").doesNotContain("Adres sklepu *").doesNotContain("Przykład: Client ID")
                .contains("Przykład: https://sklep.example.com");
        assertThat(html).contains("Po zapisie połączysz konto sprzedawcy na stronie Allegro.");
        assertThat(html).contains("data-cl-schedule-auto").contains("data-cl-async").contains("/js/async-form.js")
                .contains("/js/variant-fields.js").contains(">Zapisz marketplace<").doesNotContain("??");
    }

    @Test
    void theEditPageHasNoChoiceAndKeepsTheStoredSchedule() {
        // given
        Map<String, Object> variables = subpage(List.of(csCart()), true);
        MarketplaceSettingsForm form = (MarketplaceSettingsForm) variables.get("form");
        form.setProviderName("CsCart");
        form.getSchedules().put("CsCart.orders", "0/30 * * * ? *");

        // when
        String html = SettingsTemplateRenderer.render("store-marketplace", variables);

        // then
        assertThat(html).doesNotContain("data-cl-variant-select").doesNotContain("name=\"providerName\"");
        assertThat(html).contains("value=\"0/30 * * * ? *\"");
    }

    @Test
    void theAuthorizationPageShowsTheCodeTheLinkAndAButtonThatWorksWithoutJavaScript() {
        // given
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", null);
        variables.put("navigation", null);
        variables.put("displayName", "Allegro");
        variables.put("pending", pending());
        variables.put("expiresAtText", "15:40");
        variables.put("connected", false);
        variables.put("startAction", PATH + "/Allegro/authorize");
        variables.put("checkAction", PATH + "/Allegro/authorize/check");
        variables.put("statusAction", PATH + "/Allegro/authorize/status");
        variables.put("marketplacesHref", PATH);
        variables.put("backLabel", "Marketplaces");
        variables.put("pageTitle", "Połącz konto Allegro");

        // when
        String html = SettingsTemplateRenderer.render("store-marketplace-authorize", variables);

        // then
        assertThat(html).contains("<code class=\"cl-code\">ABCD-1234</code>")
                .contains("href=\"https://allegro.example/skojarz?code=ABCD-1234\"").contains("target=\"_blank\"");
        assertThat(html).contains("action=\"/dashboard/store/marketplaces/Allegro/authorize/check\"")
                .contains("data-status-url=\"/dashboard/store/marketplaces/Allegro/authorize/status\"")
                .contains(">Sprawdź połączenie<").contains("Kod jest ważny do 15:40.");
        assertThat(html).contains("/js/device-authorization.js").doesNotContain("??");
    }

    private static Map<String, Object> subpage(List<MarketplaceProviderDescriptor> providers, boolean editing) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", null);
        variables.put("navigation", null);
        variables.put("form", new MarketplaceSettingsForm());
        variables.put("errors", Map.of());
        variables.put("errorLabels", Map.of());
        variables.put("failure", null);
        variables.put("providers", providers);
        variables.put("editing", editing);
        variables.put("providerKnown", false);
        variables.put("storedSecretIds", Set.of());
        variables.put("authorizedOnMarketplace", Set.of("Allegro"));
        variables.put("scheduleMinIntervalMinutes", 15);
        variables.put("ordersDefaultText", "Domyślny — co 10 min");
        variables.put("returnsDefaultText", "Domyślny — co 60 min");
        variables.put("formAction", PATH + "/new");
        variables.put("marketplacesHref", PATH);
        variables.put("backLabel", "Marketplaces");
        variables.put("pageTitle", "Nowy marketplace");
        return variables;
    }

    private static MarketplaceProviderDescriptor allegro() {
        MarketplaceProviderDescriptor descriptor = mock(MarketplaceProviderDescriptor.class);
        when(descriptor.name()).thenReturn("Allegro");
        when(descriptor.displayName()).thenReturn("Allegro");
        when(descriptor.supportsReturns()).thenReturn(true);
        when(descriptor.configurationFields()).thenReturn(List.of(
                new ProviderField("clientId", "Client ID", FieldType.TEXT, true, "Client ID"),
                new ProviderField("clientSecret", "Client Secret", FieldType.PASSWORD, true, "******")));
        return descriptor;
    }

    private static MarketplaceProviderDescriptor csCart() {
        MarketplaceProviderDescriptor descriptor = mock(MarketplaceProviderDescriptor.class);
        when(descriptor.name()).thenReturn("CsCart");
        when(descriptor.displayName()).thenReturn("CS-Cart Multi-Vendor");
        when(descriptor.configurationFields()).thenReturn(List.of(
                new ProviderField("apiUrl", "Adres sklepu *", FieldType.URL, true, "https://sklep.example.com")));
        return descriptor;
    }

    /** MarketplaceAuthorization.Pending is package-private in web; the template reads it only through these accessors. */
    private static Object pending() {
        return new PendingStub();
    }

    public static final class PendingStub {
        public String userCode() {
            return "ABCD-1234";
        }

        public String openUri() {
            return "https://allegro.example/skojarz?code=ABCD-1234";
        }

        public long intervalSeconds() {
            return 5;
        }

        public Instant expiresAt() {
            return Instant.parse("2026-09-18T13:40:00Z");
        }
    }
}
