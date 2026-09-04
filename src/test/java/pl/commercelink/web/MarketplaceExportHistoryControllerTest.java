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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import pl.commercelink.marketplace.MarketplaceExportRunFile;
import pl.commercelink.marketplace.MarketplaceExportRunService;
import pl.commercelink.marketplace.MarketplaceOfferSnapshot;
import pl.commercelink.starter.security.model.CustomUser;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceExportHistoryControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String MARKETPLACE = "allegro";
    private static final String CATALOG_ID = "catalog-1";
    private static final String RUN_ID = "8213415334_2026-08-13_01-31-05";
    private static final String LEGACY_RUN_ID = "2026-08-13_01-31-05";

    @Mock
    private MarketplaceExportRunService marketplaceExportRunService;

    @InjectMocks
    private MarketplaceExportHistoryController controller;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        authenticateAs(STORE_ID, "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rendersRunRowsForTheStoreOfTheLoggedInAdmin() {
        // given
        givenRun(STORE_ID, runFile(false));
        Model model = new ExtendedModelMap();

        // when
        String view = controller.exportRun(MARKETPLACE, CATALOG_ID, RUN_ID, model);

        // then
        assertThat(view).isEqualTo("store-marketplace-export-run");
        assertThat(model.getAttribute("runId")).isEqualTo(RUN_ID);
        assertThat(model.getAttribute("runTimestamp")).isEqualTo("2026-08-13 01:31:05");
        assertThat(model.getAttribute("failed")).isEqualTo(false);
        assertThat(model.getAttribute("marketplace")).isEqualTo(MARKETPLACE);
        assertThat(model.getAttribute("catalogId")).isEqualTo(CATALOG_ID);
        assertThat(model.getAttribute("storeId")).isEqualTo(STORE_ID);
        assertThat(model.getAttribute("rawTooLarge")).isEqualTo(false);
        assertThat(model.getAttribute("raw")).asString().contains("pim-A");

        @SuppressWarnings("unchecked")
        List<MarketplaceOfferSnapshot> rows = (List<MarketplaceOfferSnapshot>) model.getAttribute("rows");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).pimId()).isEqualTo("pim-A");
    }

    @Test
    void marksTheRunAsFailedWhenTheStoredFileSaysSo() {
        // given
        givenRun(STORE_ID, runFile(true));
        Model model = new ExtendedModelMap();

        // when
        controller.exportRun(MARKETPLACE, CATALOG_ID, RUN_ID, model);

        // then
        assertThat(model.getAttribute("failed")).isEqualTo(true);
    }

    @Test
    void keepsALegacyRunIdReadableWithoutACountdownPrefix() {
        // given
        givenRun(STORE_ID, LEGACY_RUN_ID, runFile(false));
        Model model = new ExtendedModelMap();

        // when
        controller.exportRun(MARKETPLACE, CATALOG_ID, LEGACY_RUN_ID, model);

        // then
        assertThat(model.getAttribute("runId")).isEqualTo(LEGACY_RUN_ID);
        assertThat(model.getAttribute("runTimestamp")).isEqualTo("2026-08-13 01:31:05");
    }

    @Test
    void fallsBackToTheRawRunIdWhenItCarriesNoReadableTimestamp() {
        // given
        givenRun(STORE_ID, "no-timestamp", runFile(false));
        Model model = new ExtendedModelMap();

        // when
        controller.exportRun(MARKETPLACE, CATALOG_ID, "no-timestamp", model);

        // then
        assertThat(model.getAttribute("runTimestamp")).isEqualTo("no-timestamp");
    }

    @Test
    void resolvesACountdownRunIdInTheUrl() throws Exception {
        // given
        givenAnyRun(runFile(false));

        // when / then
        perform(RUN_ID)
                .andExpect(status().isOk())
                .andExpect(view().name("store-marketplace-export-run"))
                .andExpect(model().attribute("runId", RUN_ID))
                .andExpect(model().attribute("runTimestamp", "2026-08-13 01:31:05"));
    }

    @Test
    void resolvesALegacyRunIdInTheUrl() throws Exception {
        // given
        givenAnyRun(runFile(false));

        // when / then
        perform(LEGACY_RUN_ID)
                .andExpect(status().isOk())
                .andExpect(view().name("store-marketplace-export-run"))
                .andExpect(model().attribute("runId", LEGACY_RUN_ID));
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
    void rawFileEndpointReturnsInlineCsv() {
        // given
        givenRun(STORE_ID, runFile(false));

        // when
        ResponseEntity<?> response = controller.exportRunFile(MARKETPLACE, CATALOG_ID, RUN_ID);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("text/csv");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("inline; filename=\"" + RUN_ID + ".csv\"");
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
        givenRun("store-2", runFile(false));
        Model model = new ExtendedModelMap();

        // when
        String view = controller.superAdminExportRun("store-2", MARKETPLACE, CATALOG_ID, RUN_ID, model);

        // then
        assertThat(view).isEqualTo("store-marketplace-export-run");
        assertThat(model.getAttribute("storeId")).isEqualTo("store-2");
        assertThat(model.getAttribute("isSuperAdmin")).isEqualTo(true);
    }

    private ResultActions perform(String runId) throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        return mockMvc.perform(get("/dashboard/store/marketplaces/exports/{marketplace}/{catalogId}/{runId}",
                MARKETPLACE, CATALOG_ID, runId));
    }

    private void givenRun(String storeId, MarketplaceExportRunFile runFile) {
        givenRun(storeId, RUN_ID, runFile);
    }

    private void givenRun(String storeId, String runId, MarketplaceExportRunFile runFile) {
        when(marketplaceExportRunService.findRun(storeId, MARKETPLACE, CATALOG_ID, runId))
                .thenReturn(Optional.of(runFile));
    }

    private void givenAnyRun(MarketplaceExportRunFile runFile) {
        when(marketplaceExportRunService.findRun(eq(STORE_ID), eq(MARKETPLACE), eq(CATALOG_ID), anyString()))
                .thenReturn(Optional.of(runFile));
    }

    private MarketplaceExportRunFile runFile(boolean failed) {
        List<MarketplaceOfferSnapshot> rows =
                List.of(MarketplaceOfferSnapshot.published("pim-A", 3503L, 7L));
        byte[] raw = "pimId;price;quantity;removalAttempts;outcome;reasonCode;message\npim-A;3503;7;0;PUBLISHED;;\n"
                .getBytes(StandardCharsets.UTF_8);
        return new MarketplaceExportRunFile(RUN_ID, failed, rows, raw);
    }

    private void authenticateAs(String storeId, String role) {
        CustomUser user = new CustomUser(null, null, Map.of("storeId", storeId, "role", role));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)))
        );
    }
}
