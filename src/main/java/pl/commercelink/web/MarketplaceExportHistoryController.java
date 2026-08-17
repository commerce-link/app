package pl.commercelink.web;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import pl.commercelink.marketplace.MarketplaceExportExcludedItem;
import pl.commercelink.marketplace.MarketplaceExportRunFile;
import pl.commercelink.marketplace.MarketplaceExportSkipReason;
import pl.commercelink.marketplace.MarketplaceExportRunService;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Controller
public class MarketplaceExportHistoryController {

    private static final String RUN_PATH =
            "/{marketplace:[A-Za-z0-9_.-]+}/{catalogId:[A-Za-z0-9_-]+}/{runId:\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}}";
    private static final int MAX_INLINE_RAW_BYTES = 512 * 1024;

    private final MarketplaceExportRunService marketplaceExportRunService;
    private final MessageSource messageSource;

    MarketplaceExportHistoryController(MarketplaceExportRunService marketplaceExportRunService,
                                       MessageSource messageSource) {
        this.marketplaceExportRunService = marketplaceExportRunService;
        this.messageSource = messageSource;
    }

    @GetMapping("/dashboard/store/marketplaces/exports" + RUN_PATH)
    @PreAuthorize("hasRole('ADMIN')")
    public String exportRun(@PathVariable String marketplace,
                            @PathVariable String catalogId,
                            @PathVariable String runId,
                            Model model) {
        return renderRun(getStoreId(), marketplace, catalogId, runId, model);
    }

    @GetMapping("/dashboard/store/{storeId}/marketplaces/exports" + RUN_PATH)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminExportRun(@PathVariable String storeId,
                                      @PathVariable String marketplace,
                                      @PathVariable String catalogId,
                                      @PathVariable String runId,
                                      Model model) {
        return renderRun(storeId, marketplace, catalogId, runId, model);
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

    private String renderRun(String storeId, String marketplace, String catalogId, String runId, Model model) {
        Optional<MarketplaceExportRunFile> runFile =
                marketplaceExportRunService.findRun(storeId, marketplace, catalogId, runId);

        if (runFile.isEmpty()) {
            model.addAttribute("error", "Export run not found");
            return "error";
        }

        MarketplaceExportRunFile presentRunFile = runFile.get();
        boolean rawTooLarge = presentRunFile.raw().length > MAX_INLINE_RAW_BYTES;

        model.addAttribute("run", presentRunFile.document());
        model.addAttribute("reasonLabels", reasonLabels(presentRunFile));
        model.addAttribute("marketplace", marketplace);
        model.addAttribute("catalogId", catalogId);
        model.addAttribute("runId", runId);
        model.addAttribute("rawTooLarge", rawTooLarge);
        model.addAttribute("raw", rawTooLarge ? null : new String(presentRunFile.raw(), StandardCharsets.UTF_8));
        model.addAttribute("isSuperAdmin", isSuperAdmin());
        model.addAttribute("storeId", storeId);

        return "store-marketplace-export-run";
    }

    private ResponseEntity<?> renderRunFile(String storeId, String marketplace, String catalogId, String runId) {
        Optional<MarketplaceExportRunFile> runFile =
                marketplaceExportRunService.findRun(storeId, marketplace, catalogId, runId);

        if (runFile.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + runId + ".json\"")
                .body(new ByteArrayResource(runFile.get().raw()));
    }

    private Map<String, String> reasonLabels(MarketplaceExportRunFile runFile) {
        Locale locale = LocaleContextHolder.getLocale();
        Map<String, String> reasonLabels = new HashMap<>();

        runFile.document().excludedOrEmpty().stream()
                .map(MarketplaceExportExcludedItem::reason)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(reason -> reasonLabels.put(reason, messageSource.getMessage(
                        MarketplaceExportSkipReason.class.getSimpleName() + "." + reason, null, reason, locale)));

        return reasonLabels;
    }

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }

    private boolean isSuperAdmin() {
        return CustomSecurityContext.hasRole("SUPER_ADMIN");
    }
}
