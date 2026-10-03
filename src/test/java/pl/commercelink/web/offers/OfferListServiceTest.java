package pl.commercelink.web.offers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.baskets.*;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfferListServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 12, 0);
    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String TEMPLATE_ID = "b0a7d3e5-1111-2222-3333-444455556666";

    @Mock
    private BasketsRepository baskets;
    @Mock
    private StoresRepository stores;
    private OfferListService service;

    @BeforeEach
    void setup() {
        when(stores.findById("store-1")).thenReturn(mock(Store.class));
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        service = new OfferListService(baskets, stores, messages, "https://app.example");
        when(baskets.findForList(anyString(), any(), anyInt(), anyInt())).thenReturn(new OfferListResult(List.of(), 0, 1));
    }

    private static OfferListQuery query(String... pairs) {
        LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.add(pairs[i], pairs[i + 1]);
        return OfferListQuery.parse(map);
    }

    private static Basket template() {
        Basket basket = new Basket();
        basket.setStoreId("store-1");
        basket.setBasketId(TEMPLATE_ID);
        basket.setType(BasketType.OfferTemplate);
        basket.setName("Szablon CAD");
        basket.setCreatedAt(LocalDateTime.of(2026, 8, 28, 10, 0));
        return basket;
    }

    @Test
    void entryPageAsksForNewestOffersWithoutFilters() {
        // when
        OfferListPage page = service.page("store-1", query(), NOW, PL);

        // then
        verify(baskets).findForList("store-1", new OfferListCriteria(BasketType.Offer, null, Set.of(), null, null, NOW), 1, 25);
        assertThat(page.segments()).extracting(OfferListPage.SegmentLink::label).containsExactly("Oferty", "Szablony", "Koszyki ze sklepu");
        assertThat(page.segments().get(0).active()).isTrue();
        assertThat(page.validitySummary()).isEqualTo("wszystkie");
        assertThat(page.dates().value()).isEqualTo("wszystkie");
        assertThat(page.chips()).isEmpty();
        assertThat(page.resultsLine()).isEqualTo("Oferty: 0");
        assertThat(page.emptyState().text()).isEqualTo("Nie masz jeszcze ofert. Utwórz pierwszą przyciskiem „Nowa oferta”.");
        assertThat(page.emptyState().actionHref()).isNull();
    }

    @Test
    void filtersBecomeCriteriaChipsAndSummaries() {
        // when
        OfferListPage page = service.page("store-1",
                query("q", "CAD", "validity", "expiring", "from", "2026-09-01", "to", "2026-09-30"), NOW, PL);

        // then
        verify(baskets).findForList(eq("store-1"), eq(new OfferListCriteria(BasketType.Offer, "CAD", Set.of(OfferValidity.EXPIRING),
                java.time.LocalDate.of(2026, 9, 1), java.time.LocalDate.of(2026, 9, 30), NOW)), eq(1), eq(25));
        assertThat(page.validitySummary()).isEqualTo("Wygasają w 7 dni");
        assertThat(page.dates().value()).isEqualTo("01.09 – 30.09");
        assertThat(page.chips()).extracting(OfferListPage.Chip::label)
                .containsExactly("Szukane: CAD", "Ważność: Wygasają w 7 dni", "Utworzono: 01.09 – 30.09");
        assertThat(page.chips().get(0).clearHref()).isEqualTo("/dashboard/offers?validity=expiring&from=2026-09-01&to=2026-09-30");
        assertThat(page.activeFilterCount()).isEqualTo(3);
        assertThat(page.emptyState().text()).isEqualTo("Brak wyników dla wybranych filtrów.");
        assertThat(page.emptyState().actionHref()).isEqualTo("/dashboard/offers");
    }

    @Test
    void twoValidityValuesAreSummarisedAsACount() {
        // when / then
        assertThat(service.page("store-1", query("validity", "active", "validity", "expired"), NOW, PL).validitySummary())
                .isEqualTo("wybrane: 2");
    }

    @Test
    void fullIdFromAnotherSegmentSwitchesSegment() {
        // given
        when(baskets.findById("store-1", TEMPLATE_ID)).thenReturn(Optional.of(template()));

        // when
        OfferListPage page = service.page("store-1", query("q", TEMPLATE_ID, "validity", "expired"), NOW, PL);

        // then
        assertThat(page.query().segment()).isEqualTo(OfferSegment.TEMPLATES);
        assertThat(page.templates()).extracting(OfferListPage.TemplateRow::name).containsExactly("Szablon CAD");
        assertThat(page.resultsLine()).isEqualTo("Szablony: 1");
        verify(baskets, never()).findForList(anyString(), any(), anyInt(), anyInt());
    }

    @Test
    void pastedUpperCaseFullIdIsLookedUpAsStored() {
        // given
        when(baskets.findById("store-1", TEMPLATE_ID)).thenReturn(Optional.of(template()));

        // when
        OfferListPage page = service.page("store-1", query("q", TEMPLATE_ID.toUpperCase()), NOW, PL);

        // then
        assertThat(page.templates()).extracting(OfferListPage.TemplateRow::name).containsExactly("Szablon CAD");
    }

    @Test
    void fullIdOfAnotherStoreFallsBackToTextSearch() {
        // given
        when(baskets.findById("store-1", TEMPLATE_ID)).thenReturn(Optional.empty());

        // when
        service.page("store-1", query("q", TEMPLATE_ID), NOW, PL);

        // then
        verify(baskets).findForList(eq("store-1"), argThat(c -> TEMPLATE_ID.equals(c.text())), eq(1), eq(25));
    }

    @Test
    void paginationUsesTheTotalAndKeepsTheQuery() {
        // given
        when(baskets.findForList(anyString(), any(), anyInt(), anyInt())).thenReturn(new OfferListResult(List.of(), 143, 2));

        // when
        OfferListPage page = service.page("store-1", query("q", "CAD", "page", "2"), NOW, PL);

        // then
        assertThat(page.pagination().totalItems()).isEqualTo(143);
        assertThat(page.pagination().page()).isEqualTo(2);
        assertThat(page.pagination().previousHref()).isEqualTo("/dashboard/offers?q=CAD");
        assertThat(page.pagination().nextHref()).isEqualTo("/dashboard/offers?q=CAD&page=3");
    }

    @Test
    void templatesAndBasketsHaveTheirOwnEmptyTexts() {
        // when / then
        assertThat(service.page("store-1", query("segment", "templates"), NOW, PL).emptyState().text())
                .isEqualTo("Nie masz szablonów. Zapisz ofertę jako szablon na jej stronie.");
        assertThat(service.page("store-1", query("segment", "baskets"), NOW, PL).emptyState().text())
                .isEqualTo("Brak koszyków z ostatnich 14 dni.");
    }
}
