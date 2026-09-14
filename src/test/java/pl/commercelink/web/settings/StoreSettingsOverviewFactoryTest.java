package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.invoicing.api.InvoicingProviderDescriptor;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.payments.api.PaymentProviderDescriptor;
import pl.commercelink.shipping.ShippingProviderFactory;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.Branding;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.PaymentIntegration;
import pl.commercelink.stores.Printer;
import pl.commercelink.stores.RMAConfiguration;
import pl.commercelink.stores.ReportingConfiguration;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.WarehouseConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.commercelink.web.settings.TileStatus.Tone.NEUTRAL;
import static pl.commercelink.web.settings.TileStatus.Tone.OK;
import static pl.commercelink.web.settings.TileStatus.Tone.WARNING;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreSettingsOverviewFactoryTest {

    @Mock
    private InvoicingProviderFactory invoicingProviderFactory;

    @Mock
    private PaymentProviderFactory paymentProviderFactory;

    @Mock
    private ShippingProviderFactory shippingProviderFactory;

    @InjectMocks
    private StoreSettingsOverviewFactory factory;

    private Store emptyStore() {
        Store store = new Store();
        store.setStoreId("store-1");
        return store;
    }

    private List<SettingsTileView> tiles(StoreSettingsOverview overview) {
        return overview.sections().stream().flatMap(section -> section.tiles().stream()).toList();
    }

    private SettingsTileView tile(StoreSettingsOverview overview, String key) {
        return tiles(overview).stream().filter(view -> view.tile().key().equals(key)).findFirst().orElseThrow();
    }

    private TileStatus statusOf(Store store, String key) {
        return tile(factory.build(store, UserRole.ADMIN), key).status();
    }

    private BillingDetails completeBillingDetails() {
        BillingDetails details = new BillingDetails();
        details.setName("Jan Kowalski");
        details.setStreetAndNumber("Prosta 1");
        details.setPostalCode("00-001");
        details.setCity("Warszawa");
        details.setCountry("PL");
        details.setEmail("sklep@example.com");
        return details;
    }

    private Store fullyConfiguredStore() {
        Store store = emptyStore();
        store.setBillingDetails(completeBillingDetails());
        Branding branding = new Branding();
        branding.setLogo("logo.png");
        store.setBranding(branding);
        store.setConfigurationValue(IntegrationType.INVOICING_PROVIDER, "fakturownia");
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, "furgonetka");
        store.setPayments(List.of(new PaymentIntegration("stripe", true)));
        MarketplaceIntegration allegro = new MarketplaceIntegration("allegro");
        allegro.setLoggedIn(true);
        store.setMarketplaces(List.of(allegro));
        FulfilmentConfiguration fulfilment = new FulfilmentConfiguration();
        fulfilment.setEnabledCategories(List.of("Dom", "Biuro"));
        fulfilment.setSupplierConnections(List.of(new StoreSupplierConnection("elko", ConnectionMode.OWN)));
        store.setFulfilmentConfiguration(fulfilment);
        ReportingConfiguration reporting = new ReportingConfiguration();
        reporting.setGoogleAdsEnabled(true);
        store.setReportingConfiguration(reporting);
        WarehouseConfiguration warehouse = new WarehouseConfiguration();
        warehouse.setWarehouseId("WH-1");
        warehouse.setCostCenterId("CC-1");
        warehouse.setPrinters(List.of(new Printer()));
        store.setWarehouseConfiguration(warehouse);
        ShippingConfiguration shipping = new ShippingConfiguration();
        shipping.setAuthorizedCarriers(List.of(new AuthorizedCarrier("1", "dpd", "DPD")));
        store.setShippingConfiguration(shipping);
        RMAConfiguration rma = new RMAConfiguration();
        rma.setCarrier(new AuthorizedCarrier("1", "dpd", "DPD"));
        store.setRmaConfiguration(rma);
        ClientNotificationsConfiguration notifications = new ClientNotificationsConfiguration();
        notifications.setSenderName("Sklep Demo");
        notifications.setSupportedTemplates(Map.of("ORDER_CONFIRMATION", "t1"));
        store.setClientNotificationsConfiguration(notifications);
        return store;
    }

    @Test
    void buildsEverySectionForTheStoreAdminAndLinksTilesToTheirOwnSettingsPages() {
        // when
        StoreSettingsOverview overview = factory.build(emptyStore(), UserRole.ADMIN);

        // then
        assertThat(overview.sections()).hasSize(6);
        assertThat(tiles(overview)).hasSize(14);
        assertThat(tile(overview, "warehouse").href()).isEqualTo("/dashboard/store/warehouse");
        assertThat(tile(overview, "rmaCenters").href()).isEqualTo("/dashboard/store/rma-centers");
    }

    @Test
    void hidesRmaCentersFromTheSuperAdminAndPrefixesLinksWithTheStore() {
        // when
        StoreSettingsOverview overview = factory.build(emptyStore(), UserRole.SUPER_ADMIN);

        // then
        assertThat(tiles(overview)).hasSize(13);
        assertThat(tiles(overview)).noneMatch(view -> view.tile().key().equals("rmaCenters"));
        assertThat(tile(overview, "warehouse").href()).isEqualTo("/dashboard/store/store-1/warehouse");
    }

    @Test
    void describesANewStoreWithoutFailingOnMissingConfiguration() {
        // given
        Store store = emptyStore();
        store.setMarketplaces(null);
        store.setPayments(null);

        // when
        StoreSettingsOverview overview = factory.build(store, UserRole.ADMIN);

        // then
        assertThat(tile(overview, "companyDetails").status())
                .isEqualTo(TileStatus.warning("store.settings.status.companyDetails.incomplete"));
        assertThat(tile(overview, "branding").status()).isEqualTo(TileStatus.neutral("store.settings.status.branding.default"));
        assertThat(tile(overview, "invoicing").status()).isEqualTo(TileStatus.neutral("store.settings.status.notConnected"));
        assertThat(tile(overview, "payments").status()).isEqualTo(TileStatus.neutral("store.settings.status.payments.none"));
        assertThat(tile(overview, "marketplaces").status()).isEqualTo(TileStatus.neutral("store.settings.status.marketplaces.none"));
        assertThat(tile(overview, "categories").status()).isEqualTo(TileStatus.neutral("store.settings.status.categories.none"));
        assertThat(tile(overview, "reporting").status()).isEqualTo(TileStatus.neutral("store.settings.status.reporting.disabled"));
        assertThat(tile(overview, "fulfilment").status()).isEqualTo(TileStatus.neutral("store.settings.status.suppliers.none"));
        assertThat(tile(overview, "warehouse").status()).isEqualTo(TileStatus.neutral("store.settings.status.warehouse.incomplete"));
        assertThat(tile(overview, "shipping").status()).isEqualTo(TileStatus.neutral("store.settings.status.notConnected"));
        assertThat(tile(overview, "rma").status()).isEqualTo(TileStatus.neutral("store.settings.status.rma.noCarrier"));
        assertThat(tile(overview, "notification").status()).isEqualTo(TileStatus.neutral("store.settings.status.notification.none"));
        assertThat(tile(overview, "emailTemplates").status()).isEqualTo(TileStatus.neutral(
                "store.settings.status.emailTemplates.enabled", 0, EmailNotificationType.values().length));
        assertThat(tile(overview, "rmaCenters").status()).isNull();
    }

    @Test
    void leavesTheStoreUntouched() {
        // given
        Store store = emptyStore();

        // when
        factory.build(store, UserRole.ADMIN);

        // then
        assertThat(store.getBranding()).isNull();
        assertThat(store.getWarehouseConfiguration()).isNull();
        assertThat(store.getClientNotificationsConfiguration()).isNull();
        assertThat(store.getCheckoutConfiguration()).isNull();
    }

    @Test
    void treatsCompanyDetailsAsCompleteOnlyWhenTheyPassTheSaveValidation() {
        // given
        Store store = emptyStore();
        store.setBillingDetails(completeBillingDetails());

        // when / then
        assertThat(statusOf(store, "companyDetails")).isEqualTo(TileStatus.ok("store.settings.status.companyDetails.complete"));

        store.getBillingDetails().setCity(" ");
        assertThat(statusOf(store, "companyDetails").tone()).isEqualTo(WARNING);
    }

    @Test
    void namesTheConnectedInvoicingProviderByItsDisplayName() {
        // given
        Store store = emptyStore();
        store.setConfigurationValue(IntegrationType.INVOICING_PROVIDER, "fakturownia");
        InvoicingProviderDescriptor descriptor = mock(InvoicingProviderDescriptor.class);
        when(descriptor.displayName()).thenReturn("Fakturownia");
        when(invoicingProviderFactory.getDescriptor("fakturownia")).thenReturn(descriptor);

        // when / then
        assertThat(statusOf(store, "invoicing")).isEqualTo(TileStatus.ok("store.settings.status.connected", "Fakturownia"));
    }

    @Test
    void fallsBackToTheRawProviderNameAndCountsAuthorizedCarriers() {
        // given
        Store store = emptyStore();
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, "furgonetka");

        // when / then
        assertThat(statusOf(store, "shipping")).isEqualTo(TileStatus.ok("store.settings.status.connected", "furgonetka"));

        ShippingConfiguration shipping = new ShippingConfiguration();
        shipping.setAuthorizedCarriers(List.of(new AuthorizedCarrier("1", "dpd", "DPD"), new AuthorizedCarrier("2", "inpost", "InPost")));
        store.setShippingConfiguration(shipping);
        assertThat(statusOf(store, "shipping"))
                .isEqualTo(TileStatus.ok("store.settings.status.connectedWithCarriers", "furgonetka", 2));
    }

    @Test
    void countsPaymentGatewaysAndNamesTheDefaultOne() {
        // given
        Store store = emptyStore();
        store.setPayments(List.of(new PaymentIntegration("stripe", true), new PaymentIntegration("paynow", false)));
        PaymentProviderDescriptor descriptor = mock(PaymentProviderDescriptor.class);
        when(descriptor.displayName()).thenReturn("Stripe");
        when(paymentProviderFactory.getDescriptor("stripe")).thenReturn(descriptor);

        // when / then
        assertThat(statusOf(store, "payments"))
                .isEqualTo(TileStatus.ok("store.settings.status.payments.activeWithDefault", 2, "Stripe"));

        store.setPayments(List.of(new PaymentIntegration("paynow", false)));
        assertThat(statusOf(store, "payments")).isEqualTo(TileStatus.ok("store.settings.status.payments.active", 1));
    }

    @Test
    void warnsWhenAnyMarketplaceLostItsAuthorisation() {
        // given
        MarketplaceIntegration allegro = new MarketplaceIntegration("allegro");
        allegro.setLoggedIn(true);
        MarketplaceIntegration morele = new MarketplaceIntegration("morele");
        morele.setLoggedIn(false);
        Store store = emptyStore();
        store.setMarketplaces(List.of(allegro, morele));

        // when / then
        assertThat(statusOf(store, "marketplaces"))
                .isEqualTo(TileStatus.warning("store.settings.status.marketplaces.disconnected", 1, 2));

        store.setMarketplaces(List.of(allegro));
        assertThat(statusOf(store, "marketplaces")).isEqualTo(TileStatus.ok("store.settings.status.marketplaces.connected", 1));
    }

    @Test
    void countsEnabledCategoriesAndConnectedSuppliers() {
        // given
        Store store = emptyStore();
        FulfilmentConfiguration fulfilment = new FulfilmentConfiguration();
        fulfilment.setEnabledCategories(List.of("Dom", "Biuro"));
        fulfilment.setSupplierConnections(List.of(
                new StoreSupplierConnection("elko", ConnectionMode.OWN),
                new StoreSupplierConnection("acme", ConnectionMode.GLOBAL)));
        store.setFulfilmentConfiguration(fulfilment);

        // when / then
        assertThat(statusOf(store, "categories")).isEqualTo(TileStatus.ok("store.settings.status.categories.enabled", 2));
        assertThat(statusOf(store, "fulfilment")).isEqualTo(TileStatus.ok("store.settings.status.suppliers.count", 2));
    }

    @Test
    void reportsGoogleAdsOnlyWhenEnabled() {
        // given
        Store store = emptyStore();
        ReportingConfiguration reporting = new ReportingConfiguration();
        store.setReportingConfiguration(reporting);

        // when / then
        assertThat(statusOf(store, "reporting")).isEqualTo(TileStatus.neutral("store.settings.status.reporting.disabled"));
        reporting.setGoogleAdsEnabled(true);
        assertThat(statusOf(store, "reporting")).isEqualTo(TileStatus.ok("store.settings.status.reporting.googleAds"));
    }

    @Test
    void treatsTheWarehouseAsConfiguredOnlyWithBothIdentifiers() {
        // given
        Store store = emptyStore();
        WarehouseConfiguration warehouse = new WarehouseConfiguration();
        warehouse.setWarehouseId("WH-1");
        store.setWarehouseConfiguration(warehouse);

        // when / then
        assertThat(statusOf(store, "warehouse")).isEqualTo(TileStatus.neutral("store.settings.status.warehouse.incomplete"));

        warehouse.setCostCenterId("CC-1");
        assertThat(statusOf(store, "warehouse")).isEqualTo(TileStatus.ok("store.settings.status.warehouse.complete"));

        warehouse.setPrinters(List.of(new Printer()));
        assertThat(statusOf(store, "warehouse"))
                .isEqualTo(TileStatus.ok("store.settings.status.warehouse.completeWithPrinters", 1));
    }

    @Test
    void namesTheRmaCarrierAndFallsBackToItsTechnicalName() {
        // given
        Store store = emptyStore();
        RMAConfiguration rma = new RMAConfiguration();
        rma.setCarrier(new AuthorizedCarrier("1", "dpd", "DPD"));
        store.setRmaConfiguration(rma);

        // when / then
        assertThat(statusOf(store, "rma")).isEqualTo(TileStatus.ok("store.settings.status.rma.carrier", "DPD"));

        rma.setCarrier(new AuthorizedCarrier("1", "dpd", " "));
        assertThat(statusOf(store, "rma")).isEqualTo(TileStatus.ok("store.settings.status.rma.carrier", "dpd"));
    }

    @Test
    void showsTheNotificationSenderAndCountsOnlyKnownEmailTemplates() {
        // given
        Store store = emptyStore();
        ClientNotificationsConfiguration notifications = new ClientNotificationsConfiguration();
        notifications.setSenderName("Sklep Demo");
        notifications.setSupportedTemplates(Map.of(
                "ORDER_CONFIRMATION", "t1", "ORDER_SHIPPING", "t2", "NO_LONGER_EXISTING_TYPE", "t3"));
        store.setClientNotificationsConfiguration(notifications);

        // when / then
        assertThat(statusOf(store, "notification"))
                .isEqualTo(TileStatus.ok("store.settings.status.notification.sender", "Sklep Demo"));
        assertThat(statusOf(store, "emailTemplates")).isEqualTo(TileStatus.neutral(
                "store.settings.status.emailTemplates.enabled", 2, EmailNotificationType.values().length));
    }

    @Test
    void marksEveryConfiguredTileOfAFullyConfiguredStoreAsOk() {
        // when
        StoreSettingsOverview overview = factory.build(fullyConfiguredStore(), UserRole.ADMIN);

        // then
        assertThat(tiles(overview))
                .filteredOn(view -> view.status() != null && !view.tile().key().equals("emailTemplates"))
                .allMatch(view -> view.status().tone() == OK);
    }

    @Test
    void translatesEveryStatusItCanProduceInBothLanguages() {
        // given
        Store warnings = emptyStore();
        MarketplaceIntegration lost = new MarketplaceIntegration("allegro");
        lost.setLoggedIn(false);
        warnings.setMarketplaces(List.of(lost));
        Store withoutDefaultPayment = fullyConfiguredStore();
        withoutDefaultPayment.setPayments(List.of(new PaymentIntegration("paynow", false)));
        withoutDefaultPayment.getWarehouseConfiguration().setPrinters(List.of());
        withoutDefaultPayment.getShippingConfiguration().setAuthorizedCarriers(List.of());

        Set<String> keys = new HashSet<>();
        for (Store store : List.of(emptyStore(), fullyConfiguredStore(), warnings, withoutDefaultPayment)) {
            tiles(factory.build(store, UserRole.ADMIN)).stream()
                    .filter(view -> view.status() != null)
                    .forEach(view -> keys.add(view.status().messageKey()));
        }

        for (String language : List.of("pl", "en")) {
            ResourceBundle messages = ResourceBundle.getBundle("messages", Locale.forLanguageTag(language));

            // when / then
            keys.forEach(key -> assertThat(messages.containsKey(key)).as(language + " " + key).isTrue());
        }
        assertThat(keys).hasSizeGreaterThanOrEqualTo(27);
    }

    @Test
    void mapsTonesToTheStatusPillClasses() {
        // when / then
        assertThat(TileStatus.ok("k").cssClass()).isEqualTo("is-ok");
        assertThat(TileStatus.neutral("k").cssClass()).isEqualTo("is-neutral");
        assertThat(TileStatus.warning("k").cssClass()).isEqualTo("is-warn");
        assertThat(TileStatus.ok("k", 2, "Stripe").argsArray()).containsExactly(2, "Stripe");
        assertThat(TileStatus.neutral("k").tone()).isEqualTo(NEUTRAL);
    }
}
