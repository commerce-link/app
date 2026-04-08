package pl.commercelink.warehouse.builtin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Controller
class WarehouseDocumentsController {

    private static final int PAGE_SIZE = 25;

    @Autowired
    private WarehouseDocumentRepository warehouseDocumentRepository;

    @Autowired
    private WarehouseDocumentItemRepository warehouseDocumentItemRepository;

    @Autowired
    private WarehouseDocumentMfnHistoryService warehouseDocumentMfnHistoryService;

    @Autowired
    private StoresRepository storesRepository;

    @GetMapping("/dashboard/warehouse-documents")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    String listDocuments(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String warehouseId,
            @RequestParam(required = false, defaultValue = "1") int page,
            Model model
    ) {
        return showDocumentsList(getStoreId(), type, dateFrom, dateTo, warehouseId, page, model);
    }

    @GetMapping("/dashboard/store/{storeId}/warehouse-documents")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    String listDocumentsForSuperAdmin(
            @PathVariable String storeId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String warehouseId,
            @RequestParam(required = false, defaultValue = "1") int page,
            Model model
    ) {
        return showDocumentsList(storeId, type, dateFrom, dateTo, warehouseId, page, model);
    }

    private String showDocumentsList(String storeId, String type, LocalDate dateFrom, LocalDate dateTo,
                                     String warehouseId, int page, Model model) {
        Store store = storesRepository.findById(storeId);

        if (!store.hasDocumentsGenerationEnabled()) {
            model.addAttribute("documentsDisabled", true);
            if (isSuperAdmin()) {
                model.addAttribute("storeId", storeId);
                model.addAttribute("isSuperAdmin", true);
            }
            return "warehouse-documents";
        }

        DocumentType documentType = (type != null && !type.trim().isEmpty()) ? DocumentType.valueOf(type) : null;
        LocalDateTime from = dateFrom != null ? dateFrom.atStartOfDay() : null;
        LocalDateTime to = dateTo != null ? dateTo.atTime(LocalTime.MAX) : null;

        List<WarehouseDocument> pagedDocuments = warehouseDocumentRepository.search(
                storeId, documentType, from, to, warehouseId, page, PAGE_SIZE + 1);

        boolean hasNextPage = pagedDocuments.size() > PAGE_SIZE;
        if (hasNextPage) {
            pagedDocuments = pagedDocuments.subList(0, PAGE_SIZE);
        }

        model.addAttribute("currentPage", page);
        model.addAttribute("hasNextPage", hasNextPage);

        Map<String, Object> searchParams = new HashMap<>();
        searchParams.put("type", type);
        searchParams.put("dateFrom", dateFrom);
        searchParams.put("dateTo", dateTo);
        searchParams.put("warehouseId", warehouseId);

        model.addAttribute("documents", pagedDocuments);
        model.addAttribute("documentTypes", getWarehouseDocumentTypes());
        model.addAttribute("searchParams", searchParams);
        model.addAttribute("documentsDisabled", false);
        model.addAttribute("isSuperAdmin", isSuperAdmin());

        if (isSuperAdmin()) {
            model.addAttribute("storeId", storeId);
        }

        return "warehouse-documents";
    }

    @GetMapping("/dashboard/warehouse-documents/details")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    String documentDetails(@RequestParam String documentNo, Model model) {
        return showDocumentDetails(getStoreId(), documentNo, model);
    }

    @GetMapping("/dashboard/store/{storeId}/warehouse-documents/details")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    String documentDetailsForSuperAdmin(
            @PathVariable String storeId,
            @RequestParam String documentNo,
            Model model
    ) {
        return showDocumentDetails(storeId, documentNo, model);
    }

    private String showDocumentDetails(String storeId, String documentNo, Model model) {
        Store store = storesRepository.findById(storeId);
        String redirectUrl = isSuperAdmin()
                ? "redirect:/dashboard/store/" + storeId + "/warehouse-documents"
                : "redirect:/dashboard/warehouse-documents";

        if (!store.hasDocumentsGenerationEnabled()) {
            return redirectUrl;
        }

        WarehouseDocument document = warehouseDocumentRepository.findByDocumentNo(storeId, documentNo);

        if (document == null) {
            return redirectUrl;
        }

        List<WarehouseDocumentItem> items = warehouseDocumentItemRepository.findByDocumentNo(documentNo);

        model.addAttribute("document", document);
        model.addAttribute("items", items);
        model.addAttribute("isSuperAdmin", isSuperAdmin());
        model.addAttribute("documentReasons", DocumentReason.values());

        if (isSuperAdmin()) {
            model.addAttribute("storeId", storeId);
        }

        return "warehouse-document-details";
    }

    @GetMapping("/dashboard/warehouse-documents/delivery-mfn-history")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    String deliveryMfnHistory(@RequestParam String deliveryId, @RequestParam String mfn, Model model) {
        model.addAttribute("rows", warehouseDocumentMfnHistoryService.getMfnHistory(deliveryId, mfn));
        model.addAttribute("deliveryId", deliveryId);
        model.addAttribute("mfn", mfn);
        return "warehouse-document-mfn-history";
    }

    private List<DocumentType> getWarehouseDocumentTypes() {
        return Arrays.asList(
                DocumentType.GoodsReceipt,
                DocumentType.GoodsIssue,
                DocumentType.InternalReceipt,
                DocumentType.InternalIssue,
                DocumentType.StockTransfer
        );
    }

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }

    private boolean isSuperAdmin() {
        return CustomSecurityContext.hasRole("SUPER_ADMIN");
    }
}
