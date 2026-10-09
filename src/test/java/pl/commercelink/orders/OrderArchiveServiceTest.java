package pl.commercelink.orders;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.orders.OrderArchivePage;
import pl.commercelink.web.orders.OrderArchiveQuery;
import pl.commercelink.web.warehousedocuments.TestMessages;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderArchiveServiceTest {

    private static final String STORE = "s1";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);
    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private OrdersRepository orders;
    @Mock
    private StoresRepository stores;

    private OrderArchiveService service;
    private final Map<String, Order> byId = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        service = new OrderArchiveService(orders, stores, TestMessages.polish());
        lenient().when(stores.findById(STORE)).thenReturn(new Store());
        lenient().when(orders.findByIds(eq(STORE), anyList())).thenAnswer(inv -> {
            List<String> ids = inv.getArgument(1);
            return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));
        });
    }

    /** n closed orders, newest first, order i placed i hours before 9 Oct 2026 noon; every third has surname Kowalski. */
    private List<ArchiveIndexEntry> history(int n) {
        List<ArchiveIndexEntry> entries = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Order order = new Order(STORE);
            order.setOrderId(String.format("%08x-0000-4000-8000-%012d", i + 0x10000000, i));
            order.setStatus(OrderStatus.Completed);
            LocalDateTime at = LocalDateTime.of(2026, 10, 9, 12, 0).minusHours(i);
            order.setOrderedAt(at);
            BillingDetails billing = new BillingDetails();
            billing.setSurname(i % 3 == 0 ? "Kowalski" : "Nowak");
            billing.setEmail("klient" + i + "@example.com");
            order.setBillingDetails(billing);
            byId.put(order.getOrderId(), order);
            entries.add(new ArchiveIndexEntry(order.getOrderId(), at.toString(), OrderStatus.Completed, billing.getEmail()));
        }
        return entries;
    }

    private void indexReturns(List<ArchiveIndexEntry> entries) {
        when(orders.archiveIndex(eq(STORE), any())).thenAnswer(inv -> {
            ArchiveIndexRequest request = inv.getArgument(1);
            List<ArchiveIndexEntry> ordered = new ArrayList<>(entries);
            if (!request.newestFirst()) {
                java.util.Collections.reverse(ordered);
            }
            int start = 0;
            if (request.start() != null) {
                for (int i = 0; i < ordered.size(); i++) {
                    if (ordered.get(i).cursor().equals(request.start())) {
                        start = i + 1;
                    }
                }
            }
            Iterator<ArchiveIndexEntry> it = ordered.subList(start, ordered.size()).iterator();
            return it;
        });
    }

    private static OrderArchiveQuery query(String... pairs) {
        LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.add(pairs[i], pairs[i + 1]);
        }
        return OrderArchiveQuery.parse(map);
    }

    @Test
    void browsingShowsTheNewest25WithANextLink() {
        // given
        indexReturns(history(30));

        // when
        OrderArchivePage page = service.page(STORE, query(), TODAY, PL);

        // then
        assertThat(page.rows()).hasSize(25);
        assertThat(page.pagination().previousHref()).isNull();
        assertThat(page.pagination().nextHref()).contains("after=").contains("offset=25");
        assertThat(page.emptyState()).isNull();
        assertThat(page.scope()).isNull();
    }

    @Test
    void lastPageHasNoNextAndNumbersFollowTheOffset() {
        // given
        List<ArchiveIndexEntry> entries = history(30);
        indexReturns(entries);

        // when
        OrderArchivePage page = service.page(STORE, query().after(entries.get(24).cursor(), 25), TODAY, PL);

        // then
        assertThat(page.rows()).hasSize(5);
        assertThat(page.pagination().nextHref()).isNull();
        assertThat(page.pagination().previousHref()).contains("before=");
        assertThat(page.resultsLine()).isEqualTo("Zamówienia: 26–30");
    }

    @Test
    void previousFromTheSecondPageLandsOnTheFirst() {
        // given
        List<ArchiveIndexEntry> entries = history(30);
        indexReturns(entries);

        // when
        OrderArchivePage page = service.page(STORE, query().before(entries.get(25).cursor(), 25), TODAY, PL);

        // then
        assertThat(page.rows()).hasSize(25);
        assertThat(page.rows().get(0).orderId()).isEqualTo(entries.get(0).orderId());
        assertThat(page.pagination().previousHref()).isNull();
    }

    @Test
    void upperCaseShortNumberFindsTheOrder() {
        // given
        Order closed = new Order(STORE);
        closed.setOrderId("8c1d02aa-0000-4000-8000-000000000001");
        closed.setStatus(OrderStatus.Completed);
        when(orders.findByShortId(STORE, "8c1d02")).thenReturn(List.of(closed));

        // when
        OrderArchivePage page = service.page(STORE, query("q", "8C1D02"), TODAY, PL);

        // then
        assertThat(page.rows()).extracting(r -> r.orderId()).containsExactly(closed.getOrderId());
        verify(orders, never()).archiveIndex(any(), any());
    }

    @Test
    void numberOfAnOpenOrderSaysItIsOpenAndLinksToIt() {
        // given
        Order open = new Order(STORE);
        open.setOrderId("8c1d02aa-0000-4000-8000-000000000001");
        open.setStatus(OrderStatus.Shipping);
        when(orders.findByShortId(STORE, "8c1d02")).thenReturn(List.of(open));

        // when
        OrderArchivePage page = service.page(STORE, query("q", "8c1d02"), TODAY, PL);

        // then
        assertThat(page.rows()).isEmpty();
        assertThat(page.openNotice().actionHref()).isEqualTo("/dashboard/orders/" + open.getOrderId());
        assertThat(page.emptyState()).isNull();
    }

    @Test
    void numberWithoutAnyMatchFallsBackToTextSearch() {
        // given
        when(orders.findByShortId(STORE, "1234")).thenReturn(List.of());
        indexReturns(history(3));

        // when
        OrderArchivePage page = service.page(STORE, query("q", "1234"), TODAY, PL);

        // then
        verify(orders).archiveIndex(eq(STORE), any());
        assertThat(page.emptyState().text()).contains("„1234”");
    }

    @Test
    void emailSearchIgnoresCaseAndReadsTheWholeHistoryWithoutABatchLimit() {
        // given
        indexReturns(history(800));

        // when
        OrderArchivePage page = service.page(STORE, query("q", "KLIENT799@example.com"), TODAY, PL);

        // then
        assertThat(page.rows()).hasSize(1);
        assertThat(page.scope()).isNull();
    }

    @Test
    void textSearchStopsAt25MatchesWithANextLink() {
        // given
        indexReturns(history(300));

        // when
        OrderArchivePage page = service.page(STORE, query("q", "kowalski"), TODAY, PL);

        // then
        assertThat(page.rows()).hasSize(25);
        assertThat(page.pagination().nextHref()).contains("after=").contains("offset=25");
        assertThat(page.scope()).isNull();
    }

    @Test
    void textSearchStopsAfterTheBatchAndOffersOlderOnes() {
        // given — no one is called Zieliński, 700 orders: the batch of 500 ends first
        List<ArchiveIndexEntry> entries = history(700);
        indexReturns(entries);

        // when
        OrderArchivePage page = service.page(STORE, query("q", "zielinski"), TODAY, PL);

        // then
        assertThat(page.rows()).isEmpty();
        assertThat(page.emptyState()).isNull();
        assertThat(page.scope().text()).isEqualTo("Przeszukano zamówienia złożone od 18.09.2026.");
        assertThat(page.scope().moreHref()).contains("after=" + entries.get(499).cursor().encode());
    }

    @Test
    void continuationStartsRightAfterTheLastScannedEntry() {
        // given
        List<ArchiveIndexEntry> entries = history(700);
        indexReturns(entries);

        // when
        OrderArchivePage page = service.page(STORE, query("q", "kowalski").after(entries.get(499).cursor(), 0), TODAY, PL);

        // then — orders 501–699 every third is Kowalski: 501, 504, …, 699
        assertThat(page.rows()).hasSize(25);
        assertThat(page.rows().get(0).orderId()).isEqualTo(entries.get(501).orderId());
    }

    @Test
    void periodAndStatusGoToTheIndexRequest() {
        // given
        indexReturns(history(1));
        ArgumentCaptor<ArchiveIndexRequest> request = ArgumentCaptor.forClass(ArchiveIndexRequest.class);

        // when
        service.page(STORE, query("status", "Cancelled", "period", "30d"), TODAY, PL);

        // then
        verify(orders).archiveIndex(eq(STORE), request.capture());
        assertThat(request.getValue().statuses()).containsExactly(OrderStatus.Cancelled);
        assertThat(request.getValue().from()).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(request.getValue().to()).isNull();
    }

    @Test
    void emptyArchiveSaysWhereOrdersComeFrom() {
        // given
        indexReturns(List.of());

        // when
        OrderArchivePage page = service.page(STORE, query(), TODAY, PL);

        // then
        assertThat(page.emptyState().text()).startsWith("Archiwum jest puste.");
        assertThat(page.emptyState().actions()).isEmpty();
        assertThat(page.pagination().isNeeded()).isFalse();
    }

    @Test
    void segmentsChipsAndDateMenuDescribeTheState() {
        // given
        indexReturns(history(1));

        // when
        OrderArchivePage page = service.page(STORE, query("status", "Completed", "q", "x", "from", "2026-05-01"), TODAY, PL);

        // then
        assertThat(page.segments()).extracting(OrderArchivePage.SegmentLink::label).containsExactly("Wszystkie", "Zakończone", "Anulowane");
        assertThat(page.segments().get(1).active()).isTrue();
        assertThat(page.chips()).extracting(OrderArchivePage.Chip::label).containsExactly("Szukane: x", "Złożone: od 01.05.2026");
        assertThat(page.dates().value()).isEqualTo("od 01.05.2026");
        assertThat(page.dates().presets()).extracting(OrderArchivePage.Preset::label)
                .containsExactly("Cała historia", "Ostatnie 30 dni", "Ostatnie 90 dni", "Ten rok");
    }
}
