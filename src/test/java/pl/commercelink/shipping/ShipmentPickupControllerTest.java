package pl.commercelink.shipping;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.shipping.api.PickupWindow;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.shipping.api.ShippingProviderDescriptor;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentPickupControllerTest {

    private static final String STORE_ID = "store-1";
    private static final Locale POLISH = Locale.forLanguageTag("pl");
    private static final String ORDER_1 = "7a3f2c1e-58d4-4c2b-9e61-0b7d3a9f4e12";
    private static final String ORDER_2 = "91c4e0b2-1111-2222-3333-444455556666";
    private static final String BACK = "/dashboard/orders/" + ORDER_1;
    private static final PickupWindow WINDOW =
            new PickupWindow(LocalDate.of(2026, 10, 8), LocalTime.of(9, 0), LocalTime.of(17, 0), "h-1");
    private static final String WINDOW_VALUE = "2026-10-08|09:00|17:00|h-1";

    @Mock private ShipmentPickupService pickupService;
    @Mock private StoresRepository storesRepository;
    @Mock private ShippingProviderFactory providerFactory;
    @Mock private ShippingProviderDescriptor furgonetka;
    @Mock private Store store;

    private ShipmentPickupController controller;
    private MockedStatic<CustomSecurityContext> security;

    @BeforeEach
    void setUp() {
        security = mockStatic(CustomSecurityContext.class);
        security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(store.getStoreId()).thenReturn(STORE_ID);
        when(store.getPickUpAddresses()).thenReturn(List.of(address("addr-1", "Magazyn Główny"), address("addr-2", "Biuro")));
        when(providerFactory.getDescriptor("furgonetka")).thenReturn(furgonetka);
        when(furgonetka.displayName()).thenReturn("Furgonetka");
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        when(pickupService.pageWindows(any(), any(), any(), anyList(), anyInt()))
                .thenReturn(new ShipmentPickupService.PageWindows(List.of(), Map.of()));
        controller = new ShipmentPickupController(pickupService, storesRepository, providerFactory, messages);
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    private static ShippingDetails address(String id, String name) {
        ShippingDetails details = new ShippingDetails();
        details.setId(id);
        details.setCompanyName(name);
        details.setStreetAndNumber("Magazynowa 1");
        details.setPostalCode("00-001");
        details.setCity("Warszawa");
        return details;
    }

    private static PickupCandidate entry(String externalId, ShipmentOwnerType ownerType, String ownerId, String carrier,
                                         String addressId) {
        return new PickupCandidate(ownerType, ownerId, externalId, "TRK-" + externalId, "furgonetka", carrier,
                addressId);
    }

    private static PickupGroup group(String carrier, String addressId, PickupCandidate... entries) {
        return new PickupGroup(PickupGroup.key("furgonetka", carrier, addressId), "furgonetka", carrier, addressId,
                List.of(entries));
    }

    private static final PickupGroup DHL = group("dhl", "addr-1",
            entry("9", ShipmentOwnerType.ORDER, ORDER_2, "dhl", "addr-1"));
    private static final PickupGroup DPD = group("dpd", "addr-2",
            entry("1", ShipmentOwnerType.ORDER, ORDER_1, "dpd", "addr-2"),
            entry("2", ShipmentOwnerType.RMA, ORDER_2, "dpd", "addr-2"));

    private ShipmentPickupPage open(String group, String back) {
        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.page(group, back, model, POLISH);
        assertThat(view).isEqualTo("shipping-pickup");
        return (ShipmentPickupPage) model.get("pickupPage");
    }

    @Test
    void anAllegroOwnNetworkCarrierReadsOneByAllegroInTheGroupLabel() {
        // given
        PickupGroup one = group("ALLEGRO_ONE_KURIER", "addr-1",
                entry("7", ShipmentOwnerType.ORDER, ORDER_1, "ALLEGRO_ONE_KURIER", "addr-1"));
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(one));
        when(pickupService.pageWindows(any(), any(), any(), any(), eq(4))).thenReturn(new ShipmentPickupService.PageWindows(List.of(WINDOW), Map.of()));

        // when
        ShipmentPickupPage page = open(one.key(), BACK);

        // then
        assertThat(page.groups()).extracting(ShipmentPickupPage.GroupOption::label)
                .allMatch(label -> label.startsWith("One by Allegro · ") && !label.contains("ALLEGRO_ONE_KURIER"));
    }

    @Test
    void theRequestedGroupIsShownWithItsPackagesAndTheWindowsOfTheNextFourDays() {
        // given
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DHL, DPD));
        when(pickupService.pageWindows(eq(store), eq("furgonetka"), any(), eq(List.of("1", "2")), eq(4))).thenReturn(new ShipmentPickupService.PageWindows(List.of(WINDOW), Map.of()));

        // when
        ShipmentPickupPage page = open(DPD.key(), BACK);

        // then
        assertThat(page.selectedKey()).isEqualTo(DPD.key());
        assertThat(page.groups()).extracting(ShipmentPickupPage.GroupOption::label)
                .containsExactly("dhl · Furgonetka · Magazyn Główny · paczek: 1", "dpd · Furgonetka · Biuro · paczek: 2");
        assertThat(page.groups()).extracting(ShipmentPickupPage.GroupOption::selected).containsExactly(false, true);
        assertThat(page.packages()).containsExactly(
                new ShipmentPickupPage.PackageRow("1", "7a3f2c1e · TRK-1", "to zamówienie", null),
                new ShipmentPickupPage.PackageRow("2", "91c4e0b2 · TRK-2", null, null));
        assertThat(page.address()).isEqualTo("Biuro, Magazynowa 1, 00-001 Warszawa");
        assertThat(page.windows()).containsExactly(new ShipmentPickupPage.WindowOption(WINDOW_VALUE, "czw. 8 paź, 9:00–17:00"));
        assertThat(page.windowsError()).isNull();
        assertThat(page.back()).isEqualTo(BACK);
    }

    @Test
    void withoutARequestedGroupTheFirstOneIsShown() {
        // given
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DHL, DPD));

        // when
        ShipmentPickupPage page = open("furgonetka|ups|addr-9", null);

        // then
        assertThat(page.selectedKey()).isEqualTo(DHL.key());
        verify(pickupService).pageWindows(store, "furgonetka", "addr-1", List.of("9"), 4);
    }

    @Test
    void theRmaThePageWasOpenedFromIsMarkedAsSuch() {
        // given
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DPD));

        // when
        ShipmentPickupPage page = open(DPD.key(), "/dashboard/rma/" + ORDER_2);

        // then
        assertThat(page.packages()).extracting(ShipmentPickupPage.PackageRow::marker).containsExactly(null, "to zgłoszenie");
    }

    @Test
    void withoutWaitingPackagesNothingIsAskedOfTheProvider() {
        // given
        when(pickupService.groups(STORE_ID)).thenReturn(List.of());

        // when
        ShipmentPickupPage page = open(null, BACK);

        // then
        assertThat(page.hasGroups()).isFalse();
        verify(pickupService, never()).pageWindows(any(), any(), any(), anyList(), anyInt());
    }

    @Test
    void aProviderErrorReadingTheWindowsIsShownInsteadOfThem() {
        // given
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DPD));
        when(pickupService.pageWindows(any(), any(), any(), anyList(), anyInt())).thenThrow(new ShippingException("Brak usługi odbioru"));

        // when
        ShipmentPickupPage page = open(DPD.key(), BACK);

        // then
        assertThat(page.windowsError()).isEqualTo("Brak usługi odbioru");
        assertThat(page.canOrder()).isFalse();
    }

    @Test
    void aDisconnectedIntegrationIsNamedWhenTheWindowsAreRead() {
        // given
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DPD));
        when(pickupService.pageWindows(any(), any(), any(), anyList(), anyInt())).thenThrow(new ShippingUnavailableException(STORE_ID));

        // when
        ShipmentPickupPage page = open(DPD.key(), BACK);

        // then
        assertThat(page.windowsError()).startsWith("Sklep nie ma podłączonego konta kuriera");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"https://evil.example/x", "//evil.example/x", "/dashboard//evil.example", "/dashboardx",
            "/dashboard/../login", "/dashboard/\\evil.example", "/dashboard/orders\r\nLocation: x", "dashboard/orders"})
    void aBackAddressOutsideTheDashboardIsReplaced(String back) {
        // given
        when(pickupService.groups(STORE_ID)).thenReturn(List.of());

        // when
        ShipmentPickupPage page = open(null, back);

        // then
        assertThat(page.back()).isEqualTo("/dashboard/orders");
    }

    @Test
    void aBackAddressOfTheDashboardIsKept() {
        // given
        when(pickupService.groups(STORE_ID)).thenReturn(List.of());

        // when
        ShipmentPickupPage page = open(null, "/dashboard/rma/abc?tab=shipments");

        // then
        assertThat(page.back()).isEqualTo("/dashboard/rma/abc?tab=shipments");
    }

    private static final String LIST = "/dashboard/orders?status=Shipping&q=Jan+Kowalski";

    @Test
    void theOrdersListWithItsQueryIsKeptAsTheWayBack() {
        // given: "Zamów odbiór" on the orders list sends the list as it is narrowed now
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DPD));

        // when
        ShipmentPickupPage page = open(null, LIST);

        // then: the first group, no package marked as the page's own
        assertThat(page.back()).isEqualTo(LIST);
        assertThat(page.selectedKey()).isEqualTo(DPD.key());
        assertThat(page.packages()).extracting(ShipmentPickupPage.PackageRow::marker).containsOnlyNulls();
    }

    @Test
    void orderingFromTheListReturnsToTheSameListWithThePlainMessage() {
        // given: order 1's package is left out, which an order page would be told about
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DPD));
        when(pickupService.order(any(), any(), any(), anyList(), any())).thenReturn(PickupStart.started());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.order(DPD.key(), List.of("2"), WINDOW_VALUE, LIST, redirect, POLISH);

        // then
        assertThat(view).isEqualTo("redirect:" + LIST);
        assertThat(redirect.getFlashAttributes().get("successMessage"))
                .isEqualTo("Zamawiamy odbiór. Termin pojawi się przy przesyłkach za kilka sekund.");
    }

    @Test
    void aFormErrorKeepsTheListQueryInsideTheWayBack() {
        // given
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DPD));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.order(DPD.key(), null, WINDOW_VALUE, LIST, redirect, POLISH);

        // then: the list's "&" and "=" stay in the back parameter instead of becoming the page's own parameters
        assertThat(view).isEqualTo("redirect:/dashboard/shipping/pickups/new?group=furgonetka%7Cdpd%7Caddr-2"
                + "&back=%2Fdashboard%2Forders%3Fstatus%3DShipping%26q%3DJan%2BKowalski");
    }

    @Test
    void ordersThePickupForTheChosenPackagesOfTheGroup() {
        // given
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DHL, DPD));
        when(pickupService.order(eq(store), eq("furgonetka"), any(), anyList(), eq(WINDOW))).thenReturn(PickupStart.started());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String back = "/dashboard/rma/" + ORDER_2;

        // when
        String view = controller.order(DPD.key(), List.of("2", "9"), WINDOW_VALUE, back, redirect, POLISH);

        // then
        assertThat(view).isEqualTo("redirect:" + back);
        verify(pickupService).order(eq(store), eq("furgonetka"), any(),
                eq(List.of(new PickupTarget(ShipmentOwnerType.RMA, ORDER_2, "2", "TRK-2"))), eq(WINDOW));
        assertThat(redirect.getFlashAttributes().get("successMessage"))
                .isEqualTo("Zamawiamy odbiór. Termin pojawi się przy przesyłkach za kilka sekund.");
    }

    @Test
    void theOrderWhosePackageWasLeftOutIsToldItsPackageIsNotInThePickup() {
        // given: the operator came from order 1 and unticked its package (refused by the carrier, or deselected)
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DPD));
        when(pickupService.order(any(), any(), any(), anyList(), any())).thenReturn(PickupStart.started());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.order(DPD.key(), List.of("2"), WINDOW_VALUE, BACK, redirect, POLISH);

        // then
        assertThat(view).isEqualTo("redirect:" + BACK);
        assertThat(redirect.getFlashAttributes().get("successMessage"))
                .isEqualTo("Zamawiamy odbiór pozostałych paczek. Paczki tego zamówienia w nim nie ma.");
    }

    @Test
    void theRmaWhosePackageWasLeftOutIsToldItsPackageIsNotInThePickup() {
        // given: an order and an RMA may share an id-shaped path; only the RMA's own package counts
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DPD));
        when(pickupService.order(any(), any(), any(), anyList(), any())).thenReturn(PickupStart.started());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        controller.order(DPD.key(), List.of("1"), WINDOW_VALUE, "/dashboard/rma/" + ORDER_2, redirect, POLISH);

        // then
        assertThat(redirect.getFlashAttributes().get("successMessage"))
                .isEqualTo("Zamawiamy odbiór pozostałych paczek. Paczki tego zgłoszenia w nim nie ma.");
    }

    @Test
    void aPageWithoutAPackageInTheGroupGetsThePlainMessage() {
        // given: the order list, and an order whose packages wait in another group
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DHL, DPD));
        when(pickupService.order(any(), any(), any(), anyList(), any())).thenReturn(PickupStart.started());
        RedirectAttributesModelMap fromList = new RedirectAttributesModelMap();
        RedirectAttributesModelMap fromOtherOrder = new RedirectAttributesModelMap();

        // when
        controller.order(DPD.key(), List.of("1"), WINDOW_VALUE, "/dashboard/orders", fromList, POLISH);
        controller.order(DPD.key(), List.of("1"), WINDOW_VALUE, "/dashboard/orders/" + ORDER_2, fromOtherOrder, POLISH);

        // then
        assertThat(fromList.getFlashAttributes().get("successMessage"))
                .isEqualTo("Zamawiamy odbiór. Termin pojawi się przy przesyłkach za kilka sekund.");
        assertThat(fromOtherOrder.getFlashAttributes().get("successMessage"))
                .isEqualTo("Zamawiamy odbiór. Termin pojawi się przy przesyłkach za kilka sekund.");
    }

    @Test
    void withNoPackageTickedThePageAsksForOneAndNothingIsOrdered() {
        // given
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DPD));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.order(DPD.key(), null, WINDOW_VALUE, BACK, redirect, POLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/shipping/pickups/new?group=furgonetka%7Cdpd%7Caddr-2&back="
                + "%2Fdashboard%2Forders%2F" + ORDER_1);
        assertThat(redirect.getFlashAttributes().get("pickupError")).isEqualTo("Zaznacz co najmniej jedną paczkę.");
        verify(pickupService, never()).order(any(), any(), any(), any(), any());
    }

    @Test
    void withoutAWindowThePageAsksForOne() {
        // given
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DPD));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        controller.order(DPD.key(), List.of("1"), "not-a-window", BACK, redirect, POLISH);

        // then
        assertThat(redirect.getFlashAttributes().get("pickupError")).isEqualTo("Wybierz termin odbioru.");
        verify(pickupService, never()).order(any(), any(), any(), any(), any());
    }

    @Test
    void aFormErrorIsShownOnThePage() {
        // given
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DPD));
        ExtendedModelMap model = new ExtendedModelMap();
        model.addAttribute("pickupError", "Zaznacz co najmniej jedną paczkę.");

        // when
        controller.page(DPD.key(), BACK, model, POLISH);

        // then
        assertThat(((ShipmentPickupPage) model.get("pickupPage")).formError()).isEqualTo("Zaznacz co najmniej jedną paczkę.");
    }

    @Test
    void packagesOutsideTheGroupAreIgnored() {
        // given
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DHL, DPD));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.order(DPD.key(), List.of("9"), WINDOW_VALUE, BACK, redirect, POLISH);

        // then
        assertThat(view).isEqualTo("redirect:" + BACK);
        verify(pickupService, never()).order(any(), any(), any(), any(), any());
        assertThat(redirect.getFlashAttributes().get("errorMessage"))
                .isEqualTo("Żadna z wybranych paczek nie czeka już na odbiór.");
    }

    @Test
    void theProvidersRefusalIsShownOnReturn() {
        // given
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DPD));
        when(pickupService.order(any(), any(), any(), anyList(), any())).thenReturn(PickupStart.refused("cmd-1", "Brak okna"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        controller.order(DPD.key(), List.of("1"), WINDOW_VALUE, BACK, redirect, POLISH);

        // then
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("Brak okna");
    }

    @Test
    void aDisconnectedIntegrationOrdersNothing() {
        // given
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DPD));
        when(pickupService.order(any(), any(), any(), anyList(), any())).thenThrow(new ShippingUnavailableException(STORE_ID));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.order(DPD.key(), List.of("1"), WINDOW_VALUE, BACK, redirect, POLISH);

        // then
        assertThat(view).isEqualTo("redirect:" + BACK);
        assertThat(redirect.getFlashAttributes().get("errorMessage"))
                .isEqualTo("Integracja wysyłki jest odłączona — odbiór nie został zamówiony.");
    }

    @Test
    void theOrderGoesBackOnlyWithinTheDashboard() {
        // given
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DPD));
        when(pickupService.order(any(), any(), any(), anyList(), any())).thenReturn(PickupStart.started());

        // when
        String view = controller.order(DPD.key(), List.of("1"), WINDOW_VALUE, "https://evil.example",
                new RedirectAttributesModelMap(), POLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders");
    }

    @Test
    void aPackageTheCarrierRefusesIsNamedAndLeftOutWhileTheOthersKeepTheirWindows() {
        // given: package 2 was booked in the provider's panel, so the carrier gives no windows for it
        when(pickupService.groups(STORE_ID)).thenReturn(List.of(DPD));
        when(pickupService.pageWindows(eq(store), eq("furgonetka"), any(), eq(List.of("1", "2")), eq(4))).thenReturn(
                new ShipmentPickupService.PageWindows(List.of(WINDOW), Map.of("2", "Przesyłka została już zamówiona")));

        // when
        ShipmentPickupPage page = open(DPD.key(), BACK);

        // then
        assertThat(page.canOrder()).isTrue();
        assertThat(page.packages()).extracting(ShipmentPickupPage.PackageRow::orderable).containsExactly(true, false);
        assertThat(page.packages().get(1).refusal())
                .isEqualTo("Przewoźnik nie poda terminu odbioru tej paczki: Przesyłka została już zamówiona. "
                        + "Zniknie z listy, gdy przewoźnik ją dostarczy.");
    }
}
