package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import pl.commercelink.marketplace.MarketplaceExportRun;
import pl.commercelink.marketplace.MarketplaceExportRunDocument;
import pl.commercelink.marketplace.MarketplaceExportRunFile;
import pl.commercelink.marketplace.MarketplaceExportSkipReason;
import pl.commercelink.marketplace.MarketplaceExportRunService;
import pl.commercelink.marketplace.MarketplaceOfferSnapshot;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.starter.util.ConversionUtil;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceExportHistoryControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String MARKETPLACE = "allegro";
    private static final String CATALOG_ID = "catalog-1";
    private static final String RUN_ID = "2026-08-13_01-31-05";

    @Mock
    private MarketplaceExportRunService marketplaceExportRunService;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private MarketplaceExportHistoryController controller;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        authenticateAs(STORE_ID, "ADMIN");
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenReturn("Produkt bez ceny w cenniku");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rendersRunDetailsForTheStoreOfTheLoggedInAdmin() {
        // given
        givenRun(STORE_ID, runFile());
        Model model = new ExtendedModelMap();

        // when
        String view = controller.exportRun(MARKETPLACE, CATALOG_ID, RUN_ID, model);

        // then
        assertThat(view).isEqualTo("store-marketplace-export-run");
        MarketplaceExportRunDocument run = (MarketplaceExportRunDocument) model.getAttribute("run");
        assertThat(run.offers()).hasSize(1);
        assertThat(run.excluded()).hasSize(1);
        assertThat(model.getAttribute("raw")).asString().contains("pim-A");
        assertThat(model.getAttribute("rawTooLarge")).isEqualTo(false);
    }

    @Test
    void localizesEveryExclusionReasonPresentInTheDocument() {
        // given
        givenRun(STORE_ID, runFile());
        Model model = new ExtendedModelMap();

        // when
        controller.exportRun(MARKETPLACE, CATALOG_ID, RUN_ID, model);

        // then
        @SuppressWarnings("unchecked")
        Map<String, String> reasonLabels = (Map<String, String>) model.getAttribute("reasonLabels");
        assertThat(reasonLabels).containsEntry(
                MarketplaceExportSkipReason.PRODUCT_NOT_IN_PRICELIST.name(), "Produkt bez ceny w cenniku");
    }

    @Test
    void rendersErrorViewWhenRunDoesNotExist() {
        // given
        when(marketplaceExportRunService.findRun(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        Model model = new ExtendedModelMap();

        // when
        String view = controller.exportRun(MARKETPLACE, CATALOG_ID, RUN_ID, model);

        // then
        assertThat(view).isEqualTo("error");
    }

    @Test
    void rawFileEndpointReturnsInlineJson() {
        // given
        givenRun(STORE_ID, runFile());

        // when
        ResponseEntity<?> response = controller.exportRunFile(MARKETPLACE, CATALOG_ID, RUN_ID);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("inline; filename=\"" + RUN_ID + ".json\"");
    }

    @Test
    void rawFileEndpointReturnsNotFoundWhenRunIsMissing() {
        // given
        when(marketplaceExportRunService.findRun(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        // when
        ResponseEntity<?> response = controller.exportRunFile(MARKETPLACE, CATALOG_ID, RUN_ID);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void superAdminPathReadsTheStoreFromThePathInsteadOfTheSession() {
        // given
        authenticateAs(STORE_ID, "SUPER_ADMIN");
        givenRun("store-2", runFile());
        Model model = new ExtendedModelMap();

        // when
        String view = controller.superAdminExportRun("store-2", MARKETPLACE, CATALOG_ID, RUN_ID, model);

        // then
        assertThat(view).isEqualTo("store-marketplace-export-run");
        assertThat(model.getAttribute("storeId")).isEqualTo("store-2");
        assertThat(model.getAttribute("isSuperAdmin")).isEqualTo(true);
    }

    private void givenRun(String storeId, MarketplaceExportRunFile runFile) {
        when(marketplaceExportRunService.findRun(storeId, MARKETPLACE, CATALOG_ID, RUN_ID))
                .thenReturn(Optional.of(runFile));
    }

    private MarketplaceExportRunFile runFile() {
        MarketplaceExportRun run = new MarketplaceExportRun(STORE_ID, MARKETPLACE, CATALOG_ID, "pricelist-1");
        run.providerCalled(true);
        run.offers(List.of(MarketplaceOfferSnapshot.published("pim-A", 3503L, 7L, null)));
        run.excludeProduct(category(), product(), MarketplaceExportSkipReason.PRODUCT_NOT_IN_PRICELIST, null);

        MarketplaceExportRunDocument document = run.toDocument(RUN_ID, Instant.parse("2026-08-13T01:31:05Z"));
        byte[] raw = ConversionUtil.toJson(document).getBytes(StandardCharsets.UTF_8);
        return new MarketplaceExportRunFile(document, raw);
    }

    private CategoryDefinition category() {
        CategoryDefinition category = new CategoryDefinition();
        category.setCategoryId(CATALOG_ID);
        category.setName("Laptopy");
        return category;
    }

    private Product product() {
        return new Product(CATALOG_ID, "pim-B", "EAN-B", "MFN-B", "Brand", "Label", "Name-B", "default");
    }

    private void authenticateAs(String storeId, String role) {
        CustomUser user = new CustomUser(null, null, Map.of("storeId", storeId, "role", role));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)))
        );
    }
}
