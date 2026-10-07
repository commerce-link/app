package pl.commercelink.warehouse.builtin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.Printer;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListQuery;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentPageMapper;

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
    private MessageSource messageSource;

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
    String documentDetails(@RequestParam String documentId, Locale locale, Model model, RedirectAttributes redirect) {
        return showDocumentDetails(getStoreId(), documentId, locale, model, redirect);
    }

    @GetMapping("/dashboard/store/{storeId}/warehouse-documents/details")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    String documentDetailsForSuperAdmin(
            @PathVariable String storeId,
            @RequestParam String documentId,
            Locale locale,
            Model model,
            RedirectAttributes redirect
    ) {
        return showDocumentDetails(storeId, documentId, locale, model, redirect);
    }

    private String showDocumentDetails(String storeId, String documentId, Locale locale, Model model, RedirectAttributes redirect) {
        Store store = storesRepository.findById(storeId);
        String listUrl = "redirect:" + (isSuperAdmin() ? storeListPath(storeId) : LIST_PATH);

        if (!store.hasDocumentsGenerationEnabled()) {
            return listUrl;
        }

        // the key holds the store: another store's document id finds nothing
        WarehouseDocument document = warehouseDocumentRepository.findByDocumentId(storeId, documentId);

        if (document == null) {
            redirect.addFlashAttribute("documentsNotice", messageSource.getMessage("warehouse.documents.notFound", null, locale));
            return listUrl;
        }

        List<Printer> printers = store.getWarehouseConfiguration() != null
                ? store.getWarehouseConfiguration().getPrinters()
                : List.of();
        model.addAttribute("page", new WarehouseDocumentPageMapper(messageSource, locale).page(
                document, warehouseDocumentItemRepository.findByDocumentId(documentId), printers, isSuperAdmin()));
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
