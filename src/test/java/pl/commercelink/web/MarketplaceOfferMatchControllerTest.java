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
import pl.commercelink.marketplace.links.OfferMatchError;
import pl.commercelink.marketplace.links.OfferMatchProgress;
import pl.commercelink.marketplace.links.OfferMatchResult;
import pl.commercelink.marketplace.links.OfferMatchResult.Row;
import pl.commercelink.marketplace.links.OfferMatchResultStore;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.OfferMatchingRunView;
import pl.commercelink.web.settings.OfferMatchingView;
import pl.commercelink.web.settings.SettingsFlash;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.commercelink.web.MarketplacesFixture.PL;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceOfferMatchControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String EMPIK = "Empik";
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private MarketplaceConnections marketplaces;
    @Mock
    private MarketplaceOfferMatchPublisher publisher;
    @Mock
    private OfferMatchResultStore resultStore;
    @Mock
    private StoreActivity storeActivity;
    @Mock
    private MarketplaceProviderDescriptor empik;
    @Mock
    private MarketplaceProviderDescriptor otherMarketplace;

    private MarketplaceOfferMatchController controller;

    @BeforeEach
    void setUp() {
        controller = new MarketplaceOfferMatchController(storesRepository, marketplaces, publisher, resultStore,
                storeActivity, MarketplacesFixture.polishMessages(), Clock.fixed(NOW, ZoneOffset.UTC));
        when(storeActivity.isActive(org.mockito.ArgumentMatchers.any(Store.class))).thenReturn(true);
        givenStoreConnectedTo(STORE_ID, EMPIK);
        givenStoreConnectedTo("store-2", EMPIK);
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
    void downloadsEveryOfferOfAGroupAsCsvWithActiveOffersFirst() throws Exception {
        // given
        List<Row> linked = new java.util.ArrayList<>();
        linked.add(new Row("sku-old", "Pled; bawełna", List.of("5901234123460"), null, false));
        for (int i = 0; i < 150; i++) {
            linked.add(new Row("sku-" + i, "Oferta " + i, List.of("59012341" + String.format("%05d", i), "5901"), null,
                    true));
        }
        when(resultStore.latest(STORE_ID, EMPIK)).thenReturn(Optional.of(new OfferMatchResult(NOW, 151, linked,
                List.of(new Row("sku-r", "Zestaw", List.of(), OfferMatchResult.NO_EAN, true)), 0, 0)));

        // when
        var response = controller.groupCsv(EMPIK, "linked", PL);

        // then
        byte[] body = response.getBody().getByteArray();
        assertThat(body).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        List<String> lines = new String(body, 3, body.length - 3, java.nio.charset.StandardCharsets.UTF_8).lines().toList();
        assertThat(lines).hasSize(152);
        assertThat(lines.get(0)).isEqualTo("\"SKU oferty\";\"Tytuł oferty\";\"EAN\";\"Aktywna\";\"Powód\"");
        assertThat(lines.get(1)).isEqualTo("\"sku-0\";\"Oferta 0\";\"5901234100000, 5901\";\"tak\";\"\"");
        assertThat(lines.get(151)).isEqualTo("\"sku-old\";\"Pled; bawełna\";\"5901234123460\";\"nie\";\"\"");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("offer-matching-Empik-linked.csv");
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("text/csv;charset=UTF-8");
    }

    @Test
    void theCsvOfAGroupToReviewCarriesTheReasonAsThePageSaysIt() throws Exception {
        // given
        authenticateAs("super", "SUPER_ADMIN");
        when(resultStore.latest("store-2", EMPIK)).thenReturn(Optional.of(new OfferMatchResult(NOW, 1, List.of(),
                List.of(new Row("sku-r", "Zestaw", List.of(), OfferMatchResult.NO_EAN, true)), 0, 0)));

        // when
        var response = controller.superAdminGroupCsv("store-2", EMPIK, "toReview", PL);

        // then
        String csv = new String(response.getBody().getByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("\"sku-r\";\"Zestaw\";\"\";\"tak\";\"Oferta nie ma poprawnego EAN-u");
    }

    @Test
    void theCsvSaysWhichEanAnOfferThatLostItsEansKeeps() throws Exception {
        // given
        when(resultStore.latest(STORE_ID, EMPIK)).thenReturn(Optional.of(new OfferMatchResult(NOW, 1, List.of(),
                List.of(new Row("PS-1", "Lampa", List.of("2000000001012"), OfferMatchResult.NO_EAN_KEPT_LINK, true)),
                0, 0)));

        // when
        var response = controller.groupCsv(EMPIK, "toReview", PL);

        // then
        String csv = new String(response.getBody().getByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("\"PS-1\";\"Lampa\";\"2000000001012\";\"tak\";"
                + "\"Oferta nie ma już EAN-u, zamówienia dostają EAN-y z poprzedniego powiązania");
    }

    @Test
    void theCsvIsNotFoundBeforeTheFirstRun() {
        // given
        when(resultStore.latest(STORE_ID, EMPIK)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> controller.groupCsv(EMPIK, "linked", PL))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void theDroppedGroupOfOffersNotInACatalogHasNoCsv() {
        // given
        when(resultStore.latest(STORE_ID, EMPIK)).thenReturn(Optional.of(new OfferMatchResult(NOW, 0, List.of(),
                List.of(), 0, 0)));

        // when / then
        assertThatThrownBy(() -> controller.groupCsv(EMPIK, "notInCatalog", PL))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void showsGroupsOfLatestRun() {
        // given
        OfferMatchResult result = new OfferMatchResult(Instant.parse("2026-11-02T09:15:00Z"), 9,
                List.of(new Row("sku-1", "Mysz", List.of("5901234123457"), null, true),
                        new Row("sku-3", "Monitor", List.of("5901234123459", "5901234123460"), null, true)),
                List.of(new Row("sku-5", "Słuchawki", List.of(), OfferMatchResult.NO_EAN, true)),
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
        assertThat(matching.groups()).extracting(OfferMatchingView.Group::name).containsExactly("linked", "toReview");
        assertThat(matching.groups()).extracting(OfferMatchingView.Group::count).containsExactly(2, 1);
        assertThat(matching.group("toReview").rows()).singleElement()
                .satisfies(row -> assertThat(row.reasonKey()).isEqualTo("store.marketplaces.offer.matching.reason.no-ean"));
    }

    @Test
    void rendersOnlyTheFirstRowsOfALargeGroup() {
        // given
        List<Row> linked = java.util.stream.IntStream.range(0, OfferMatchingView.ROW_LIMIT + 50)
                .mapToObj(index -> new Row("sku-" + index, "Produkt " + index, List.of("59000000" + (10000 + index)), null, true))
                .toList();
        when(resultStore.latest(STORE_ID, EMPIK)).thenReturn(Optional.of(
                new OfferMatchResult(Instant.now(), linked.size(), linked, List.of(), 0, 0)));
        Model model = new ExtendedModelMap();

        // when
        controller.offerMatching(EMPIK, model, PL);

        // then
        OfferMatchingView.Group group = ((OfferMatchingView) model.getAttribute("matching")).group("linked");
        assertThat(group.count()).isEqualTo(OfferMatchingView.ROW_LIMIT + 50);
        assertThat(group.rows()).hasSize(OfferMatchingView.ROW_LIMIT);
        assertThat(group.limited()).isTrue();
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
    void unknownStoreIsNotFound() {
        // given
        authenticateAs(STORE_ID, "SUPER_ADMIN");

        // when / then
        assertNotFound(() -> controller.superAdminOfferMatching("store-3", EMPIK, new ExtendedModelMap(), PL));
        assertNotFound(() -> controller.superAdminOrderOfferMatching("store-3", EMPIK, PL, new RedirectAttributesModelMap()));
        verify(publisher, never()).order(anyString(), anyString());
    }

    @Test
    void marketplaceNotConnectedIsNotFound() {
        // given
        when(otherMarketplace.supportsOfferListing()).thenReturn(true);
        when(marketplaces.descriptor("Ceneo")).thenReturn(otherMarketplace);

        // when / then
        assertNotFound(() -> controller.offerMatching("Ceneo", new ExtendedModelMap(), PL));
        assertNotFound(() -> controller.orderOfferMatching("Ceneo", PL, new RedirectAttributesModelMap()));
        verify(publisher, never()).order(anyString(), anyString());
    }

    @Test
    void marketplaceWithoutInstalledAdapterIsNotFound() {
        // given
        storesRepository.findById(STORE_ID).connectMarketplace("Ceneo", false);

        // when / then
        assertNotFound(() -> controller.offerMatching("Ceneo", new ExtendedModelMap(), PL));
        assertNotFound(() -> controller.orderOfferMatching("Ceneo", PL, new RedirectAttributesModelMap()));
        verify(publisher, never()).order(anyString(), anyString());
    }

    @Test
    void marketplaceWithoutOfferListingIsNotFound() {
        // given
        when(otherMarketplace.supportsOfferListing()).thenReturn(false);
        when(marketplaces.descriptor("Allegro")).thenReturn(otherMarketplace);
        storesRepository.findById(STORE_ID).connectMarketplace("Allegro", false);

        // when / then
        assertNotFound(() -> controller.offerMatching("Allegro", new ExtendedModelMap(), PL));
        assertNotFound(() -> controller.orderOfferMatching("Allegro", PL, new RedirectAttributesModelMap()));
        verify(publisher, never()).order(anyString(), anyString());
    }

    @Test
    void refusesRunForInactiveIntegration() {
        // given
        storesRepository.findById(STORE_ID).getMarketplaceIntegration(EMPIK).setLoggedIn(false);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.orderOfferMatching(EMPIK, PL, redirectAttributes);

        // then
        verify(publisher, never()).order(anyString(), anyString());
        assertThat(view).isEqualTo("redirect:/dashboard/store/marketplaces/Empik/offer-matching");
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey(SettingsFlash.SAVED_MESSAGE);
        assertThat((String) redirectAttributes.getFlashAttributes().get(SettingsFlash.ERROR_MESSAGE))
                .startsWith("Połączenie z Empik nie działa");
    }

    @Test
    void refusesRunForInactiveStore() {
        // given
        authenticateAs(STORE_ID, "SUPER_ADMIN");
        when(storeActivity.isActive(storesRepository.findById("store-2"))).thenReturn(false);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.superAdminOrderOfferMatching("store-2", EMPIK, PL, redirectAttributes);

        // then
        verify(publisher, never()).order(anyString(), anyString());
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-2/marketplaces/Empik/offer-matching");
        assertThat((String) redirectAttributes.getFlashAttributes().get(SettingsFlash.ERROR_MESSAGE))
                .startsWith("Sklep jest nieaktywny");
    }

    @Test
    void pageExplainsWhyARunCannotBeOrderedAndKeepsTheLatestResult() {
        // given
        storesRepository.findById(STORE_ID).getMarketplaceIntegration(EMPIK).setLoggedIn(false);
        when(resultStore.latest(STORE_ID, EMPIK)).thenReturn(Optional.of(new OfferMatchResult(Instant.now(), 0,
                List.of(), List.of(), 0, 0)));
        Model model = new ExtendedModelMap();

        // when
        controller.offerMatching(EMPIK, model, PL);

        // then
        assertThat(model.getAttribute("runBlockedMessage")).asString().startsWith("Połączenie z Empik nie działa");
        assertThat(model.getAttribute("runBlockedHref")).isEqualTo("/dashboard/store/marketplaces/Empik");
        assertThat(model.getAttribute("matching")).isNotNull();
    }

    @Test
    void activeStoreAndConnectionCanOrderARun() {
        // given
        when(resultStore.latest(STORE_ID, EMPIK)).thenReturn(Optional.empty());
        Model model = new ExtendedModelMap();

        // when
        controller.offerMatching(EMPIK, model, PL);

        // then
        assertThat(model.getAttribute("runBlockedMessage")).isNull();
    }

    private static OfferMatchProgress running(Instant updatedAt, Integer total) {
        return new OfferMatchProgress("run-1", Instant.parse("2026-11-02T09:50:00Z"), updatedAt,
                OfferMatchProgress.Status.RUNNING, "400", 400, total, 0, 0, null, null, null, 0, null, null);
    }

    @Test
    void pageShowsProgressOfRunningRun() {
        // given
        when(resultStore.progress(STORE_ID, EMPIK)).thenReturn(Optional.of(running(NOW.minusSeconds(60), 6150)));
        Model model = new ExtendedModelMap();

        // when
        controller.offerMatching(EMPIK, model, PL);

        // then
        OfferMatchingRunView run = (OfferMatchingRunView) model.getAttribute("run");
        assertThat(run.running()).isTrue();
        assertThat(run.failed()).isFalse();
        assertThat(run.processed()).isEqualTo(400);
        assertThat(run.total()).isEqualTo(6150);
        assertThat(run.updatedAt()).isEqualTo("02.11.2026 10:59");
    }

    @Test
    void pageKeepsTheLatestResultDuringARun() {
        // given
        when(resultStore.progress(STORE_ID, EMPIK)).thenReturn(Optional.of(running(NOW, 6150)));
        when(resultStore.latest(STORE_ID, EMPIK)).thenReturn(Optional.of(new OfferMatchResult(NOW.minus(Duration.ofDays(1)),
                0, List.of(), List.of(), 0, 0)));
        Model model = new ExtendedModelMap();

        // when
        controller.offerMatching(EMPIK, model, PL);

        // then
        assertThat(model.getAttribute("matching")).isNotNull();
        assertThat(model.getAttribute("run")).isNotNull();
    }

    @Test
    void pageShowsFailure() {
        // given
        when(resultStore.progress(STORE_ID, EMPIK)).thenReturn(Optional.of(running(NOW, 6150).failed(NOW, OfferMatchError.MARKETPLACE_ERROR, 500)));
        Model model = new ExtendedModelMap();

        // when
        controller.offerMatching(EMPIK, model, PL);

        // then
        OfferMatchingRunView run = (OfferMatchingRunView) model.getAttribute("run");
        assertThat(run.failed()).isTrue();
        assertThat(run.running()).isFalse();
        assertThat(run.errorKey()).isEqualTo("store.marketplaces.offer.matching.error.MARKETPLACE_ERROR");
        assertThat(run.errorStatus()).isEqualTo(500);
        assertThat(run.startedAt()).isEqualTo("02.11.2026 10:50");
    }

    @Test
    void pageTreatsAStaleRunAsAbandoned() {
        // given
        when(resultStore.progress(STORE_ID, EMPIK)).thenReturn(Optional.of(running(NOW.minus(Duration.ofMinutes(31)), 6150)));
        Model model = new ExtendedModelMap();

        // when
        controller.offerMatching(EMPIK, model, PL);

        // then
        assertThat(model.getAttribute("run")).isNull();
    }

    @Test
    void refusesNewRunWhileOneIsRunning() {
        // given
        when(resultStore.progress(STORE_ID, EMPIK)).thenReturn(Optional.of(running(NOW.minus(Duration.ofMinutes(29)), 6150)));
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.orderOfferMatching(EMPIK, PL, redirectAttributes);

        // then
        verify(publisher, never()).order(anyString(), anyString());
        assertThat(view).isEqualTo("redirect:/dashboard/store/marketplaces/Empik/offer-matching");
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey(SettingsFlash.SAVED_MESSAGE);
        assertThat((String) redirectAttributes.getFlashAttributes().get(SettingsFlash.ERROR_MESSAGE))
                .startsWith("Dopasowanie ofert z Empik już trwa");
    }

    @Test
    void allowsNewRunAfterStaleProgress() {
        // given
        when(resultStore.progress(STORE_ID, EMPIK)).thenReturn(Optional.of(running(NOW.minus(Duration.ofMinutes(31)), 6150)));

        // when
        controller.orderOfferMatching(EMPIK, PL, new RedirectAttributesModelMap());

        // then
        verify(publisher).order(STORE_ID, EMPIK);
    }

    @Test
    void allowsNewRunAfterFailure() {
        // given
        when(resultStore.progress(STORE_ID, EMPIK)).thenReturn(Optional.of(running(NOW, 6150).failed(NOW, OfferMatchError.INTERNAL)));

        // when
        controller.orderOfferMatching(EMPIK, PL, new RedirectAttributesModelMap());

        // then
        verify(publisher).order(STORE_ID, EMPIK);
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
