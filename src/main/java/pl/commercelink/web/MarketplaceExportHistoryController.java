package pl.commercelink.web;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import pl.commercelink.marketplace.MarketplaceExportRunFile;
import pl.commercelink.marketplace.MarketplaceExportRunHeader;
import pl.commercelink.marketplace.MarketplaceExportRunId;
import pl.commercelink.marketplace.MarketplaceExportRunService;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.settings.MarketplaceExportRunView;
import pl.commercelink.web.settings.SettingsPaths;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Controller
public class MarketplaceExportHistoryController {

    private static final String MARKETPLACE_PATH = "/{marketplace:[A-Za-z0-9_.-]+}";

    private static final String RUN_PATH =
            MARKETPLACE_PATH + "/{catalogId:[A-Za-z0-9_-]+}"
                    + "/{runId:(?:\\d{10}_)?\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}}";

    static final int LISTED_RUNS_LIMIT = 25;

    private final MarketplaceExportRunService marketplaceExportRunService;
    private final MarketplaceConnections marketplaces;
    private final ProductCatalogRepository catalogRepository;
    private final MessageSource messageSource;

    MarketplaceExportHistoryController(MarketplaceExportRunService marketplaceExportRunService,
                                       MarketplaceConnections marketplaces, ProductCatalogRepository catalogRepository,
                                       MessageSource messageSource) {
        this.marketplaceExportRunService = marketplaceExportRunService;
        this.marketplaces = marketplaces;
        this.catalogRepository = catalogRepository;
        this.messageSource = messageSource;
    }

    @GetMapping("/dashboard/store/marketplaces/exports" + MARKETPLACE_PATH)
    @PreAuthorize("hasRole('ADMIN')")
    public String exportHistory(@PathVariable String marketplace, Model model, Locale locale) {
        return renderHistory(getStoreId(), marketplace, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/marketplaces/exports" + MARKETPLACE_PATH)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminExportHistory(@PathVariable String storeId,
                                          @PathVariable String marketplace,
                                          Model model, Locale locale) {
        return renderHistory(storeId, marketplace, model, locale);
    }

    @GetMapping("/dashboard/store/marketplaces/exports" + RUN_PATH)
    @PreAuthorize("hasRole('ADMIN')")
    public String exportRun(@PathVariable String marketplace,
                            @PathVariable String catalogId,
                            @PathVariable String runId,
                            Model model, Locale locale) {
        return renderRun(getStoreId(), marketplace, catalogId, runId, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/marketplaces/exports" + RUN_PATH)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminExportRun(@PathVariable String storeId,
                                      @PathVariable String marketplace,
                                      @PathVariable String catalogId,
                                      @PathVariable String runId,
                                      Model model, Locale locale) {
        return renderRun(storeId, marketplace, catalogId, runId, model, locale);
    }

    @GetMapping("/dashboard/store/marketplaces/exports" + RUN_PATH + "/file")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> exportRunFile(@PathVariable String marketplace,
                                           @PathVariable String catalogId,
                                           @PathVariable String runId) {
        return renderRunFile(getStoreId(), marketplace, catalogId, runId);
    }

    @GetMapping("/dashboard/store/{storeId}/marketplaces/exports" + RUN_PATH + "/file")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> superAdminExportRunFile(@PathVariable String storeId,
                                                     @PathVariable String marketplace,
                                                     @PathVariable String catalogId,
                                                     @PathVariable String runId) {
        return renderRunFile(storeId, marketplace, catalogId, runId);
    }

    private String renderHistory(String storeId, String marketplace, Model model, Locale locale) {
        List<MarketplaceExportRunHeader> runs =
                marketplaceExportRunService.findRuns(storeId, marketplace, LISTED_RUNS_LIMIT);

        String displayName = marketplaces.displayName(marketplace);
        model.addAttribute("marketplace", marketplace);
        model.addAttribute("displayName", displayName);
        model.addAttribute("catalogNames", runs.isEmpty() ? Map.of() : catalogNames(storeId));
        model.addAttribute("marketplacesHref", SettingsPaths.store(storeId, "/marketplaces"));
        model.addAttribute("backLabel", messageSource.getMessage("store.marketplaces", null, locale));
        model.addAttribute("pageTitle", messageSource.getMessage("store.marketplaces.exports.history.title",
                new Object[]{displayName}, locale));
        model.addAttribute("storeId", storeId);
        model.addAttribute("exportRuns", runs);
        model.addAttribute("runLimit", LISTED_RUNS_LIMIT);
        model.addAttribute("isSuperAdmin", isSuperAdmin());

        return "store-marketplace-export-history";
    }

    private String renderRun(String storeId, String marketplace, String catalogId, String runId, Model model,
                             Locale locale) {
        Optional<MarketplaceExportRunFile> runFile =
                marketplaceExportRunService.findRun(storeId, marketplace, catalogId, runId);

        if (runFile.isEmpty()) {
            model.addAttribute("error", "Export run not found");
            return "error";
        }

        MarketplaceExportRunFile presentRunFile = runFile.get();

        model.addAttribute("runId", runId);
        model.addAttribute("runTimestamp", MarketplaceExportRunId.readable(runId));
        model.addAttribute("failed", presentRunFile.failed());
        model.addAttribute("view", MarketplaceExportRunView.of(presentRunFile.rows()));
        model.addAttribute("fileHref", SettingsPaths.store(storeId,
                "/marketplaces/exports/" + marketplace + "/" + catalogId + "/" + runId + "/file"));
        model.addAttribute("marketplace", marketplace);
        model.addAttribute("catalogId", catalogId);
        model.addAttribute("storeId", storeId);
        model.addAttribute("isSuperAdmin", isSuperAdmin());
        String displayName = marketplaces.displayName(marketplace);
        model.addAttribute("displayName", displayName);
        model.addAttribute("catalogName", catalogNames(storeId).getOrDefault(catalogId, catalogId));
        model.addAttribute("historyHref", SettingsPaths.store(storeId, "/marketplaces/exports/" + marketplace));
        model.addAttribute("historyLabel", messageSource.getMessage("store.marketplaces.exports.history.title",
                new Object[]{displayName}, locale));

        return "store-marketplace-export-run";
    }

    private ResponseEntity<?> renderRunFile(String storeId, String marketplace, String catalogId, String runId) {
        Optional<MarketplaceExportRunFile> runFile =
                marketplaceExportRunService.findRun(storeId, marketplace, catalogId, runId);

        if (runFile.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + runId + ".csv\"")
                .body(new ByteArrayResource(runFile.get().raw()));
    }

    /** Catalog names by id: a run file names its catalog by id only, and a deleted catalog keeps showing its id. */
    private Map<String, String> catalogNames(String storeId) {
        Map<String, String> names = new HashMap<>();
        catalogRepository.findAll(storeId).forEach(catalog -> names.put(catalog.getCatalogId(),
                StringUtils.isBlank(catalog.getName()) ? catalog.getCatalogId() : catalog.getName()));
        return names;
    }

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }

    private boolean isSuperAdmin() {
        return CustomSecurityContext.hasRole("SUPER_ADMIN");
    }
}
