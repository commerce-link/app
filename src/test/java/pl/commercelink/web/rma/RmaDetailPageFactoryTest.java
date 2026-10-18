package pl.commercelink.web.rma;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.rma.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RmaDetailPageFactoryTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String RMA_ID = "9f4e21b7-1111-2222-3333-444455556666";
    private static final String CLIENT_LINK = "https://app.example/store/store-1/client/rma/" + RMA_ID;

    private RmaDetailPageFactory factory;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        factory = new RmaDetailPageFactory(messages);
    }

    private static RMA rma(RMAStatus status) {
        RMA rma = new RMA("store-1");
        rma.setRmaId(RMA_ID);
        rma.setOrderId("3c0d9a41-aaaa-bbbb-cccc-ddddeeeeffff");
        rma.setStatus(status);
        rma.setEmail("jan@example.com");
        rma.setCreatedAt(LocalDateTime.of(2026, 10, 12, 14, 3));
        ShippingDetails details = new ShippingDetails();
        details.setName("Jan");
        details.setSurname("Kowalski");
        details.setCity("Poznań");
        rma.setShippingDetails(details);
        return rma;
    }

    private static RMA marketplaceRma(RMAStatus status) {
        RMA rma = rma(status);
        rma.setMarketplace("Allegro");
        rma.setExternalReturnId("ret-1");
        rma.setExternalReturnReference("RT-5512-0931");
        return rma;
    }

    private static RMAItem item(String id, RMAItemStatus status, int qty) {
        RMAItem item = new RMAItem();
        item.setRmaId(RMA_ID);
        item.setRmaItemId(id);
        item.setItemId("oi-" + id);
        item.setName("Item " + id);
        item.setQty(qty);
        item.setPrice(100.0);
        item.setStatus(status);
        return item;
    }

    private static OrderItem orderItem() {
        OrderItem orderItem = new OrderItem();
        orderItem.setItemId("oi-9");
        orderItem.setName("Corsair RM850e");
        orderItem.setQty(2);
        orderItem.setPrice(389.0);
        return orderItem;
    }

    private RmaDetailPage page(RMA rma, List<RMAItem> items) {
        return page(rma, items, List.of(), null, null);
    }

    private RmaDetailPage page(RMA rma, List<RMAItem> items, List<OrderItem> remaining, String open, String splitItemId) {
        return factory.page("store-1", rma, items, remaining, null, false, CLIENT_LINK, open, splitItemId, PL);
    }

    static Stream<Arguments> processSteps() {
        return Stream.of(
                Arguments.of(RMAStatus.New, "Approved", "rma.detail.next.approve"),
                Arguments.of(RMAStatus.ItemsReceived, "Processing", "rma.detail.next.process"),
                Arguments.of(RMAStatus.Processing, "Completed", "rma.detail.next.complete"),
                Arguments.of(RMAStatus.Approved, null, null),
                Arguments.of(RMAStatus.WaitingForItems, null, null),
                Arguments.of(RMAStatus.Completed, null, null),
                Arguments.of(RMAStatus.Rejected, null, null),
                Arguments.of(null, "Approved", "rma.detail.next.approve"));
    }

    @ParameterizedTest
    @MethodSource("processSteps")
    void nextStatusFollowsTheProcess(RMAStatus status, String target, String labelKey) {
        // when
        RmaDetailPage.NextStatus next = page(rma(status), List.of()).header().next();

        // then
        if (target == null) {
            assertThat(next).isNull();
        } else {
            assertThat(next.target()).isEqualTo(target);
            assertThat(next.labelKey()).isEqualTo(labelKey);
            assertThat(next.titleKey()).isNotBlank();
            assertThat(next.effectKey()).isNotBlank();
            assertThat(next.icon()).isNotBlank();
        }
    }

    @Test
    void finishingWithUnresolvedItemsCountsThem() {
        // given
        List<RMAItem> items = List.of(item("a", RMAItemStatus.New, 1), item("b", RMAItemStatus.Received, 1),
                item("c", RMAItemStatus.MovedToWarehouse, 1));

        // when
        RmaDetailPage.NextStatus next = page(rma(RMAStatus.Processing), items).header().next();

        // then
        assertThat(next.target()).isEqualTo("Completed");
        assertThat(next.unresolved()).isEqualTo(2);
    }

    @Test
    void aClosedRmaHasNoSelectionNoAddAndNoStatusChange() {
        // given
        RMA rma = rma(RMAStatus.Completed);
        rma.setMedia(new ArrayList<>(List.of("store-1/rma/" + RMA_ID + "/photo.jpg")));

        // when
        RmaDetailPage page = page(rma, List.of(item("a", RMAItemStatus.MovedToWarehouse, 1)), List.of(orderItem()), "status", null);

        // then
        assertThat(page.closed()).isTrue();
        assertThat(page.items().selectable()).isFalse();
        assertThat(page.items().canAdd()).isFalse();
        assertThat(page.header().canChangeStatus()).isFalse();
        assertThat(page.header().canReject()).isFalse();
        assertThat(page.header().next()).isNull();
        assertThat(page.media().editable()).isFalse();
        assertThat(page.address().dialogPage()).isNull();
        assertThat(page.openDialog()).isNull();
        assertThat(page.header().clientLink()).isEqualTo(CLIENT_LINK);
    }

    @Test
    void rejectionIsBlockedAfterARefund() {
        // given
        RMA rma = marketplaceRma(RMAStatus.ItemsReceived);
        rma.addActionEvent(RMA.EVENT_REFUND_REQUESTED);

        // when
        RmaDetailPage.Header header = page(rma, List.of()).header();

        // then
        assertThat(header.canReject()).isFalse();
        assertThat(header.rejectBlockedKey()).isEqualTo("rma.detail.reject.blocked");
        assertThat(header.canChangeStatus()).isTrue();
    }

    @Test
    void statusOptionsLeaveOutRejectedAndLockItemsReceivedWhileProcessing() {
        // when
        List<RmaDetailPage.StatusOption> options = page(rma(RMAStatus.Processing), List.of()).header().statusOptions();

        // then
        assertThat(options).extracting(RmaDetailPage.StatusOption::value)
                .containsExactly("New", "Approved", "WaitingForItems", "ItemsReceived", "Processing", "Completed");
        assertThat(options).filteredOn(RmaDetailPage.StatusOption::disabled)
                .extracting(RmaDetailPage.StatusOption::value).containsExactly("ItemsReceived");
        assertThat(options).filteredOn(RmaDetailPage.StatusOption::current)
                .extracting(RmaDetailPage.StatusOption::value).containsExactly("Processing");
        assertThat(options.get(0).label()).isEqualTo("Nowy");
        assertThat(options).allSatisfy(option -> assertThat(option.effectKey()).startsWith("rma.detail.status.effect."));
    }

    @Test
    void resendIsOfferedOnAClosedMarketplaceRmaWithDecisions() {
        // given
        RMA rma = marketplaceRma(RMAStatus.Completed);
        rma.addMarketplaceDecision(new MarketplaceDecision("ReturnAccepted", "5b1d2c3a-0000-0000-0000-00000000e07a", "{}",
                LocalDateTime.of(2026, 10, 16, 11, 42)));

        // when
        RmaDetailPage page = page(rma, List.of());

        // then
        assertThat(page.header().resendHref()).isEqualTo("/dashboard/rma/" + RMA_ID + "/resend-marketplace-decision");
        assertThat(page.marketplaceCard().resendHref()).isEqualTo(page.header().resendHref());
        assertThat(page.marketplaceCard().decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.typeLabel()).isEqualTo("Zwrot pieniędzy");
            assertThat(decision.recordedAt()).isEqualTo("16.10.2026 11:42");
            assertThat(decision.commandShort()).isEqualTo("5b1d…e07a");
            assertThat(decision.commandId()).isEqualTo("5b1d2c3a-0000-0000-0000-00000000e07a");
        });
        assertThat(page.marketplaceCard().name()).isEqualTo("Allegro");
        assertThat(page.marketplaceCard().reference()).isEqualTo("RT-5512-0931");
    }

    @Test
    void aMarketplaceRmaWithoutDecisionsOffersNoResend() {
        // when
        RmaDetailPage page = page(marketplaceRma(RMAStatus.New), List.of());

        // then
        assertThat(page.header().resendHref()).isNull();
        assertThat(page.marketplaceCard().decisions()).isEmpty();
        assertThat(page.header().marketplaceLine().text()).isEqualTo("Allegro: zwrot RT-5512-0931");
        assertThat(page.header().source()).isEqualTo("Allegro");
        assertThat(page.settings().emailRequired()).isFalse();
    }

    @Test
    void historyIsNewestFirstWithLocalizedTitlesAndTheCreationLast() {
        // given
        RMA rma = rma(RMAStatus.ItemsReceived);
        rma.addEvent(new Event(EventType.email, "RMA_ITEMS_RECEIVED", LocalDateTime.of(2026, 10, 14, 9, 0)));
        rma.addEvent(new Event(EventType.action, "FOO", LocalDateTime.of(2026, 10, 15, 10, 30)));

        // when
        RmaDetailPage.History history = page(rma, List.of()).history();

        // then
        assertThat(history.visible()).isEqualTo(4);
        assertThat(history.events()).extracting(RmaDetailPage.Event::title)
                .containsExactly("Zdarzenie: FOO", "Wysłano e-mail: przedmioty dotarły", "Zgłoszono RMA");
        assertThat(history.events()).extracting(RmaDetailPage.Event::at)
                .containsExactly("15.10.2026 10:30", "14.10.2026 09:00", "12.10.2026 14:03");
    }

    @Test
    void mediaKindsFollowTheExtension() {
        // given
        RMA rma = rma(RMAStatus.New);
        String prefix = "store-1/rma/" + RMA_ID + "/";
        rma.setMedia(new ArrayList<>(List.of(prefix + "a.JPG", prefix + "b.png", prefix + "c.mov", prefix + "d.pdf",
                prefix + "e.txt", prefix + "f g.jpeg")));

        // when
        RmaDetailPage.Media media = page(rma, List.of()).media();

        // then
        assertThat(media.editable()).isTrue();
        assertThat(media.tiles()).extracting(RmaDetailPage.Tile::kind)
                .containsExactly("image", "image", "video", "pdf", "file", "image");
        assertThat(media.tiles().get(0).fileName()).isEqualTo("a.JPG");
        assertThat(media.tiles().get(0).key()).isEqualTo(prefix + "a.JPG");
        assertThat(media.tiles().get(0).href()).isEqualTo("/rma/" + RMA_ID + "/media/a.JPG");
        assertThat(media.tiles().get(5).href()).isEqualTo("/rma/" + RMA_ID + "/media/f%20g.jpeg");
        assertThat(page(rma, List.of()).current().media()).containsExactlyElementsOf(rma.getMedia());
    }

    @Test
    void openIsAcceptedOnlyForKnownDialogs() {
        // given
        RMA rma = rma(RMAStatus.ItemsReceived);
        rma.setShipments(new ArrayList<>(List.of(new Shipment(), new Shipment())));
        List<RMAItem> items = List.of(item("single", RMAItemStatus.Received, 1), item("pair", RMAItemStatus.Received, 2));

        // when
        RmaDetailPage evil = page(rma, items, List.of(), "evil", null);
        RmaDetailPage outOfRange = page(rma, items, List.of(), "shipment-99", null);
        RmaDetailPage zero = page(rma, items, List.of(), "shipment-0", null);
        RmaDetailPage second = page(rma, items, List.of(), "shipment-2", null);
        RmaDetailPage status = page(rma, items, List.of(), "status", null);
        RmaDetailPage splitSingle = page(rma, items, List.of(), "split", "single");
        RmaDetailPage splitUnknown = page(rma, items, List.of(), "split", "nope");
        RmaDetailPage splitPair = page(rma, items, List.of(), "split", "pair");

        // then
        assertThat(evil.openDialog()).isNull();
        assertThat(outOfRange.openDialog()).isNull();
        assertThat(zero.openDialog()).isNull();
        assertThat(second.openDialog()).isEqualTo("shipment-2");
        assertThat(status.openDialog()).isEqualTo("status");
        assertThat(splitSingle.openDialog()).isNull();
        assertThat(splitSingle.split()).isNull();
        assertThat(splitUnknown.openDialog()).isNull();
        assertThat(splitPair.openDialog()).isEqualTo("split");
        assertThat(splitPair.split()).isEqualTo(new RmaDetailPage.SplitTarget("pair", "Item pair", 2,
                "/dashboard/rma/" + RMA_ID + "/items/pair/split"));
    }

    @Test
    void itemsShowStatusResolutionAndValues() {
        // given
        RMAItem item = item("7c1e5a20-0000-0000-0000-000000000000", RMAItemStatus.Received, 2);
        item.setDesiredResolution(RMAResolutionType.Return);
        item.setEan(" ");
        item.setReason("Nie działa");

        // when
        RmaDetailPage page = page(rma(RMAStatus.ItemsReceived), List.of(item), List.of(orderItem()), null, null);

        // then
        RmaDetailPage.Row row = page.items().rows().get(0);
        assertThat(row.index()).isZero();
        assertThat(row.shortId()).isEqualTo("7c1e5a20");
        assertThat(row.href()).isEqualTo("/dashboard/rma/" + RMA_ID + "/items/7c1e5a20-0000-0000-0000-000000000000");
        assertThat(row.ean()).isNull();
        assertThat(row.status()).isEqualTo("Received");
        assertThat(row.statusLabel()).isEqualTo("Otrzymany");
        assertThat(row.statusTone()).isEqualTo("is-warn");
        assertThat(row.desired()).isEqualTo("Zwrot");
        assertThat(row.actual()).isNull();
        assertThat(row.unitPrice()).isEqualTo("100,00 PLN");
        assertThat(row.total()).isEqualTo("200,00 PLN");
        assertThat(row.totalValue()).isEqualTo(200.0);
        assertThat(page.header().itemCount()).isEqualTo("1 pozycja");
        assertThat(page.header().total()).isEqualTo("200,00 PLN");
        assertThat(page.header().clientName()).isEqualTo("Jan Kowalski");
        assertThat(page.header().orderHref()).isEqualTo("/dashboard/orders/3c0d9a41-aaaa-bbbb-cccc-ddddeeeeffff");
        assertThat(page.header().createdAt()).isEqualTo("12.10.2026 14:03");
        assertThat(page.items().canAdd()).isTrue();
        assertThat(page.items().selectable()).isTrue();
        assertThat(page.add().options()).singleElement()
                .isEqualTo(new RmaDetailPage.AddOption("oi-9", "Corsair RM850e (do 2 szt.)", 2, 389.0));
    }

    @Test
    void settingsAndCurrentFieldsCarryTheStoredValues() {
        // given
        RMA rma = rma(RMAStatus.Approved);
        rma.setShippingInsurance(4247.0);
        rma.setEmailNotificationsEnabled(true);

        // when
        RmaDetailPage page = page(rma, List.of());

        // then
        assertThat(page.settings()).isEqualTo(new RmaDetailPage.Settings("jan@example.com", true, true, "4247.00",
                "4 247,00 PLN"));
        assertThat(page.current()).isEqualTo(new RmaDetailPage.CurrentFields("Approved", "jan@example.com", null, true,
                "4247.00", List.of()));
    }

    @Test
    void theInsuranceFieldShowsWholeGroszeWhileTheCarriedCopyStaysExact() {
        // given: a sum of increases kept as a double (199.99 + 49.99)
        RMA rma = rma(RMAStatus.Approved);
        rma.setShippingInsurance(199.99 + 49.99);

        // when
        RmaDetailPage page = page(rma, List.of());

        // then: step=0.01 refuses "249.98000000000002", so the visible field is rounded; the other forms carry the
        // stored value so that saving them does not change it
        assertThat(page.settings().insurance()).isEqualTo("249.98");
        assertThat(page.current().insurance()).isEqualTo("249.98000000000002");
    }

    @Test
    void aBareRmaBuildsAPageWithoutFailing() {
        // given
        RMA rma = new RMA();
        rma.setRmaId(RMA_ID);
        rma.setShipments(null);
        rma.setMedia(null);

        // when
        RmaDetailPage page = factory.page("store-1", rma, List.of(), List.of(), null, false, CLIENT_LINK, "shipment-1", null, PL);

        // then
        assertThat(page.header().statusLabel()).isEqualTo("Nowy");
        assertThat(page.header().clientName()).isNull();
        assertThat(page.header().orderId()).isNull();
        assertThat(page.header().orderHref()).isNull();
        assertThat(page.header().createdAt()).isNull();
        assertThat(page.header().marketplaceLine()).isNull();
        assertThat(page.marketplaceCard()).isNull();
        assertThat(page.items().rows()).isEmpty();
        assertThat(page.media().tiles()).isEmpty();
        assertThat(page.history().events()).isEmpty();
        assertThat(page.address().missing()).isTrue();
        assertThat(page.address().lines()).isEmpty();
        assertThat(page.address().dialogPage()).isNotNull();
        assertThat(page.current().status()).isEqualTo("New");
        assertThat(page.current().media()).isEmpty();
        assertThat(page.settings().email()).isNull();
        assertThat(page.openDialog()).isNull();
    }
}
