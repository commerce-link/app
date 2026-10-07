package pl.commercelink.warehouse.builtin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListQuery;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Controller
class WarehouseDocumentsController {

    private static final String LIST_PATH = "/dashboard/warehouse-documents";

    @Autowired
    private WarehouseDocumentRepository warehouseDocumentRepository;

    @Autowired
    private WarehouseDocumentItemRepository warehouseDocumentItemRepository;

    @Autowired
    private WarehouseDocumentMfnHistoryService warehouseDocumentMfnHistoryService;

    @Autowired
    private WarehouseDocumentListService warehouseDocumentListService;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private WarehouseLabelPrintService warehouseLabelPrintService;

    @GetMapping("/dashboard/warehouse-documents")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    String listDocuments(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        return list(LIST_PATH, getStoreId(), params, locale, model, "warehouse-documents");
    }

    @GetMapping("/dashboard/warehouse-documents/list")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    String listDocumentsFragment(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        return list(LIST_PATH, getStoreId(), params, locale, model, "warehouse-documents :: results");
    }

    @GetMapping("/dashboard/store/{storeId}/warehouse-documents")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    String listDocumentsForSuperAdmin(@PathVariable String storeId, @RequestParam MultiValueMap<String, String> params,
                                      Locale locale, Model model) {
        return list(storeListPath(storeId), storeId, params, locale, model, "warehouse-documents");
    }

    @GetMapping("/dashboard/store/{storeId}/warehouse-documents/list")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    String listDocumentsFragmentForSuperAdmin(@PathVariable String storeId, @RequestParam MultiValueMap<String, String> params,
                                              Locale locale, Model model) {
        return list(storeListPath(storeId), storeId, params, locale, model, "warehouse-documents :: results");
    }

    private String list(String path, String storeId, MultiValueMap<String, String> params, Locale locale, Model model, String view) {
        Optional<String> legacy = WarehouseDocumentListQuery.legacyRedirect(path, params);
        if (legacy.isPresent()) {
            return "redirect:" + legacy.get();
        }
        model.addAttribute("page", warehouseDocumentListService.page(storeId, isSuperAdmin(),
                WarehouseDocumentListQuery.parse(path, params), locale));
        return view;
    }

    private static String storeListPath(String storeId) {
        return "/dashboard/store/" + storeId + "/warehouse-documents";
    }

    @GetMapping("/dashboard/warehouse-documents/details")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    String documentDetails(@RequestParam String documentId, Model model) {
        return showDocumentDetails(getStoreId(), documentId, model);
    }

    @GetMapping("/dashboard/store/{storeId}/warehouse-documents/details")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    String documentDetailsForSuperAdmin(
            @PathVariable String storeId,
            @RequestParam String documentId,
            Model model
    ) {
        return showDocumentDetails(storeId, documentId, model);
    }

    private String showDocumentDetails(String storeId, String documentId, Model model) {
        Store store = storesRepository.findById(storeId);
        String redirectUrl = isSuperAdmin()
                ? "redirect:/dashboard/store/" + storeId + "/warehouse-documents"
                : "redirect:/dashboard/warehouse-documents";

        if (!store.hasDocumentsGenerationEnabled()) {
            return redirectUrl;
        }

        WarehouseDocument document = warehouseDocumentRepository.findByDocumentId(storeId, documentId);

        if (document == null) {
            return redirectUrl;
        }

        List<WarehouseDocumentItem> items = warehouseDocumentItemRepository.findByDocumentId(documentId);

        model.addAttribute("document", document);
        model.addAttribute("items", items);
        model.addAttribute("isSuperAdmin", isSuperAdmin());
        model.addAttribute("documentReasons", DocumentReason.values());
        model.addAttribute("printers", store.getWarehouseConfiguration() != null
                ? store.getWarehouseConfiguration().getPrinters()
                : List.of());

        if (isSuperAdmin()) {
            model.addAttribute("storeId", storeId);
        }

        return "warehouse-document-details";
    }

    @GetMapping(value = "/dashboard/warehouse-documents/print-labels", produces = "application/zpl")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @ResponseBody
    String printLabels(@RequestParam String documentId, @RequestParam String printer) {
        return warehouseLabelPrintService.renderDocumentLabels(getStoreId(), documentId, printer).content();
    }

    @GetMapping("/dashboard/warehouse-documents/delivery-mfn-history")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    String deliveryMfnHistory(@RequestParam String deliveryId, @RequestParam String mfn, Model model) {
        model.addAttribute("rows", warehouseDocumentMfnHistoryService.getMfnHistory(getStoreId(), deliveryId, mfn));
        model.addAttribute("deliveryId", deliveryId);
        model.addAttribute("mfn", mfn);
        return "warehouse-document-mfn-history";
    }

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }

    private boolean isSuperAdmin() {
        return CustomSecurityContext.hasRole("SUPER_ADMIN");
    }
}
