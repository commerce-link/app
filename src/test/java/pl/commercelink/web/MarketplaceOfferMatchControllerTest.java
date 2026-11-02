package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.marketplace.links.MarketplaceOfferMatchPublisher;
import pl.commercelink.marketplace.links.OfferMatchResult;
import pl.commercelink.marketplace.links.OfferMatchResult.Row;
import pl.commercelink.marketplace.links.OfferMatchResultStore;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.OfferMatchingView;
import pl.commercelink.web.settings.SettingsFlash;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.commercelink.web.MarketplacesFixture.PL;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceOfferMatchControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String EMPIK = "Empik";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private MarketplaceConnections marketplaces;
    @Mock
    private MarketplaceOfferMatchPublisher publisher;
    @Mock
    private OfferMatchResultStore resultStore;

    private MarketplaceOfferMatchController controller;

    @BeforeEach
    void setUp() {
        controller = new MarketplaceOfferMatchController(storesRepository, marketplaces, publisher, resultStore,
                MarketplacesFixture.polishMessages());
        givenStoreConnectedTo(STORE_ID, EMPIK);
        givenStoreConnectedTo("store-2", EMPIK);
        MarketplaceProviderDescriptor empik = mock(MarketplaceProviderDescriptor.class);
        when(empik.supportsOfferListing()).thenReturn(true);
        when(marketplaces.descriptor(EMPIK)).thenReturn(empik);
        when(marketplaces.displayName(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        authenticateAs(STORE_ID, "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void showsEmptyStateBeforeFirstRun() {
        // given
        when(resultStore.latest(STORE_ID, EMPIK)).thenReturn(Optional.empty());
        Model model = new ExtendedModelMap();

        // when
        String view = controller.offerMatching(EMPIK, model, PL);

        // then
        assertThat(view).isEqualTo("store-marketplace-offer-matching");
        assertThat(model.getAttribute("matching")).isNull();
        assertThat(model.getAttribute("formAction")).isEqualTo("/dashboard/store/marketplaces/Empik/offer-matching");
        assertThat(model.getAttribute("marketplacesHref")).isEqualTo("/dashboard/store/marketplaces");
        assertThat(model.getAttribute("pageTitle")).isEqualTo("Dopasuj oferty · Empik");
        assertThat(model.getAttribute("displayName")).isEqualTo(EMPIK);
    }

    @Test
    void showsGroupsOfLatestRun() {
        // given
        OfferMatchResult result = new OfferMatchResult(Instant.parse("2026-11-02T09:15:00Z"), 9,
                List.of(new Row("sku-1", "Mysz", List.of("5901234123457"), "pim-1", null)),
                List.of(new Row("sku-2", "Klawiatura", List.of("5901234123458"), "pim-2", OfferMatchResult.ALREADY_LINKED)),
                List.of(new Row("sku-3", "Monitor", List.of("5901234123459", "5901234123460"), null, null),
                        new Row("sku-4", "Kabel", List.of("5901234123459"), null, null)),
                List.of(new Row("sku-5", "Słuchawki", List.of(), null, OfferMatchResult.NO_EAN)),
                2, 3);
        when(resultStore.latest(STORE_ID, EMPIK)).thenReturn(Optional.of(result));
        Model model = new ExtendedModelMap();

        // when
        controller.offerMatching(EMPIK, model, PL);

        // then
        OfferMatchingView matching = (OfferMatchingView) model.getAttribute("matching");
        assertThat(matching.finishedAt()).isEqualTo("02.11.2026 10:15");
        assertThat(matching.total()).isEqualTo(9);
        assertThat(matching.ownOffers()).isEqualTo(2);
        assertThat(matching.alreadyLinked()).isEqualTo(3);
        assertThat(matching.groups()).extracting(OfferMatchingView.Group::name)
                .containsExactly("linked", "notInCatalog", "noPimEntry", "toReview");
        assertThat(matching.groups()).extracting(OfferMatchingView.Group::count).containsExactly(1, 1, 2, 1);
        assertThat(matching.group("noPimEntry").eans()).isEqualTo("5901234123459\n5901234123460");
        assertThat(matching.group("notInCatalog").eans()).isEqualTo("5901234123458");
        assertThat(matching.group("linked").eans()).isNull();
        assertThat(matching.group("toReview").eans()).isNull();
        assertThat(matching.group("toReview").rows()).singleElement()
                .satisfies(row -> assertThat(row.reasonKey()).isEqualTo("store.marketplaces.offer.matching.reason.no-ean"));
        assertThat(matching.group("notInCatalog").rows()).singleElement()
                .satisfies(row -> assertThat(row.linkedBefore()).isTrue());
    }

    @Test
    void rendersOnlyTheFirstRowsOfALargeGroupButCopiesEveryEan() {
        // given
        List<Row> missing = java.util.stream.IntStream.range(0, OfferMatchingView.ROW_LIMIT + 50)
                .mapToObj(index -> new Row("sku-" + index, "Produkt " + index, List.of("59000000" + (10000 + index)), null, null))
                .toList();
        when(resultStore.latest(STORE_ID, EMPIK)).thenReturn(Optional.of(
                new OfferMatchResult(Instant.now(), missing.size(), List.of(), List.of(), missing, List.of(), 0, 0)));
        Model model = new ExtendedModelMap();

        // when
        controller.offerMatching(EMPIK, model, PL);

        // then
        OfferMatchingView.Group group = ((OfferMatchingView) model.getAttribute("matching")).group("noPimEntry");
        assertThat(group.count()).isEqualTo(OfferMatchingView.ROW_LIMIT + 50);
        assertThat(group.rows()).hasSize(OfferMatchingView.ROW_LIMIT);
        assertThat(group.limited()).isTrue();
        assertThat(group.eans().split("\n")).hasSize(OfferMatchingView.ROW_LIMIT + 50);
    }

    @Test
    void orderingARunPublishesStoreAndMarketplace() {
        // given
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.orderOfferMatching(EMPIK, PL, redirectAttributes);

        // then
        verify(publisher).order(STORE_ID, EMPIK);
        assertThat(view).isEqualTo("redirect:/dashboard/store/marketplaces/Empik/offer-matching");
        assertThat((String) redirectAttributes.getFlashAttributes().get(SettingsFlash.SAVED_MESSAGE))
                .startsWith("Dopasowanie ofert z Empik zlecone");
    }

    @Test
    void superAdminSeesStoreFromPath() {
        // given
        authenticateAs(STORE_ID, "SUPER_ADMIN");
        when(resultStore.latest("store-2", EMPIK)).thenReturn(Optional.empty());
        Model model = new ExtendedModelMap();

        // when
        controller.superAdminOfferMatching("store-2", EMPIK, model, PL);
        String redirect = controller.superAdminOrderOfferMatching("store-2", EMPIK, PL, new RedirectAttributesModelMap());

        // then
        verify(resultStore).latest("store-2", EMPIK);
        verify(publisher).order("store-2", EMPIK);
        assertThat(model.getAttribute("formAction")).isEqualTo("/dashboard/store/store-2/marketplaces/Empik/offer-matching");
        assertThat(redirect).isEqualTo("redirect:/dashboard/store/store-2/marketplaces/Empik/offer-matching");
    }

    @Test
    void unknownMarketplaceIsNotFound() {
        // given
        MarketplaceProviderDescriptor allegro = mock(MarketplaceProviderDescriptor.class);
        when(allegro.supportsOfferListing()).thenReturn(false);
        when(marketplaces.descriptor("Allegro")).thenReturn(allegro);
        storesRepository.findById(STORE_ID).connectMarketplace("Allegro", false);

        // when / then
        assertNotFound(() -> controller.offerMatching("Ceneo", new ExtendedModelMap(), PL));
        assertNotFound(() -> controller.orderOfferMatching("Ceneo", PL, new RedirectAttributesModelMap()));
        assertNotFound(() -> controller.orderOfferMatching("Allegro", PL, new RedirectAttributesModelMap()));
        assertNotFound(() -> controller.offerMatching("Allegro", new ExtendedModelMap(), PL));
        verify(publisher, never()).order(anyString(), anyString());
    }

    @Test
    void marketplaceTheStoreIsNotConnectedToIsNotFound() {
        // given
        authenticateAs(STORE_ID, "SUPER_ADMIN");

        // when / then
        assertNotFound(() -> controller.superAdminOfferMatching("store-3", EMPIK, new ExtendedModelMap(), PL));
        assertNotFound(() -> controller.superAdminOrderOfferMatching("store-3", EMPIK, PL, new RedirectAttributesModelMap()));
        verify(publisher, never()).order(anyString(), anyString());
    }

    private static void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private void givenStoreConnectedTo(String storeId, String marketplace) {
        Store store = new Store();
        store.setStoreId(storeId);
        store.connectMarketplace(marketplace, false);
        when(storesRepository.findById(storeId)).thenReturn(store);
    }

    private void authenticateAs(String storeId, String role) {
        CustomUser user = new CustomUser(null, null, Map.of("storeId", storeId, "role", role));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
