package pl.commercelink.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.financials.*;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.Blocked;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.Closed;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.NotAllowed;
import pl.commercelink.warehouse.builtin.StockLedgerPeriod;
import pl.commercelink.warehouse.builtin.StockLedgerPeriodClosing;
import pl.commercelink.web.reports.StockLedgerClosingBlocker;
import pl.commercelink.web.reports.StockLedgerClosingView;
import pl.commercelink.web.reports.StockLedgerClosingWarning;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@PreAuthorize("hasRole('ADMIN')")
public class FinancialReportsController {

    private static final String REPORTS_PATH = "/dashboard/reports";
    private static final String CLOSING_SECTION = REPORTS_PATH + "#stock-ledger-closing";

    @Autowired
    private OrdersExport ordersExport;

    @Autowired
    private GoogleOfflineConversionsExport googleOfflineConversionsExport;

    @Autowired
    private StockLedgerExport stockLedgerExport;

    @Autowired
    private ProductWeightOriginComplianceReportExport productWeightOriginComplianceReportExport;

    @Autowired
    private PurchaseReportExport purchaseReportExport;

    @Autowired
    private PaymentsExport paymentsExport;

    @Autowired
    private FinancialReportGenerator financialReportGenerator;

    @Autowired
    private SupplierLabels supplierLabels;

    @Autowired
    private StockLedgerPeriodClosing stockLedgerPeriodClosing;

    @Autowired
    private MessageSource messageSource;

    @GetMapping(REPORTS_PATH)
    public String reports(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                  Model model, Locale locale) {
        if (dateFrom == null || dateTo == null) {
            LocalDate now = LocalDate.now();
            dateFrom = now.minusMonths(1).withDayOfMonth(1);
            dateTo = dateFrom.withDayOfMonth(dateFrom.lengthOfMonth());
        }

        FinancialReports reports = financialReportGenerator.generate(getStoreId(), dateFrom, dateTo);

        Map<String, Integer> salesVolumeByProvider = reports.ownSources().getSalesVolumeByProvider();
        SupplierLabelMap labels = supplierLabels.forStoreId(getStoreId());
        List<String> providerNames = salesVolumeByProvider.keySet().stream().map(labels::of).toList();
        List<Integer> providerSales = new ArrayList<>(salesVolumeByProvider.values());

        model.addAttribute("reportOwn", reports.ownSources());
        model.addAttribute("reportMarketplace", reports.marketplace());
        model.addAttribute("providerNames", providerNames);
        model.addAttribute("providerSales", providerSales);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        // after a held closing the range picker keeps the range that was held, so it can be closed once settled
        StockLedgerClosingWarning warning = (StockLedgerClosingWarning) model.getAttribute("ledgerWarning");
        StockLedgerClosingView closing = StockLedgerClosingView.of(stockLedgerPeriodClosing.closedPeriods(getStoreId()), dateFrom, dateTo);
        model.addAttribute("ledgerClosing", warning == null ? closing : closing.withRange(warning.dateFrom(), warning.dateTo()));

        return "reports";
    }

    @GetMapping("/dashboard/reports/ordersExport")
    public void ordersExport(@RequestParam("dateFrom") String dateFrom, @RequestParam("dateTo") String dateTo, HttpServletResponse response) throws IOException {
        String csv = ordersExport.run(getStoreId(), LocalDate.parse(dateFrom), LocalDate.parse(dateTo));

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"orders.csv\"");
        response.getWriter().write(csv);
    }

    @GetMapping("/dashboard/reports/googleOfflineConversionsExport")
    public void googleOfflineConversionsExport(@RequestParam("dateFrom") String dateFrom, @RequestParam("dateTo") String dateTo, HttpServletResponse response) throws IOException {
        String csv = googleOfflineConversionsExport.run(getStoreId(), LocalDate.parse(dateFrom), LocalDate.parse(dateTo));

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"google-offline-conversions.csv\"");
        response.getWriter().write(csv);
    }

    @GetMapping("/dashboard/reports/stockLedgerExport")
    public void stockLedgerExport(@RequestParam("dateFrom") String dateFrom, @RequestParam("dateTo") String dateTo, HttpServletResponse response) throws IOException {
        byte[] csv = stockLedgerExport.run(getStoreId(), LocalDate.parse(dateFrom), LocalDate.parse(dateTo));
        writeStockLedger(response, dateFrom, dateTo, csv);
    }

    @GetMapping("/dashboard/reports/stock-ledger/closed")
    public void closedStockLedger(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                  HttpServletResponse response) throws IOException {
        byte[] csv = stockLedgerPeriodClosing.closedReport(getStoreId(), new StockLedgerPeriod(dateFrom, dateTo))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        writeStockLedger(response, dateFrom.toString(), dateTo.toString(), csv);
    }

    /** Closes the range, or generates it again when it is closed already. */
    @PostMapping("/dashboard/reports/stock-ledger/close")
    public String closeStockLedgerPeriod(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                         RedirectAttributes redirectAttributes, Locale locale) throws IOException {
        StockLedgerPeriod period = new StockLedgerPeriod(dateFrom, dateTo);
        Object[] label = {period.label()};
        switch (stockLedgerPeriodClosing.close(getStoreId(), period)) {
            case Closed closed -> {
                redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage(
                        closed.regenerated() ? "reports.stockLedger.closing.regenerated" : "reports.stockLedger.closing.closed",
                        label, locale));
                if (!closed.outdatedLaterPeriods().isEmpty()) {
                    String later = closed.outdatedLaterPeriods().stream().map(StockLedgerPeriod::label).collect(Collectors.joining(", "));
                    redirectAttributes.addFlashAttribute("ledgerOutdated", messageSource.getMessage(
                            "reports.stockLedger.closing.outdated", new Object[]{period.label(), later}, locale));
                }
            }
            case Blocked blocked -> {
                SupplierLabelMap labels = supplierLabels.forStoreId(getStoreId());
                redirectAttributes.addFlashAttribute("errorMessage",
                        messageSource.getMessage("reports.stockLedger.closing.blocked", label, locale));
                redirectAttributes.addFlashAttribute("ledgerWarning", new StockLedgerClosingWarning(dateFrom, dateTo,
                        blocked.deliveries().stream().map(delivery -> StockLedgerClosingBlocker.of(delivery, labels)).toList()));
            }
            case NotAllowed notAllowed -> {
                List<Object> arguments = new ArrayList<>(List.of(label));
                arguments.addAll(notAllowed.arguments());
                redirectAttributes.addFlashAttribute("errorMessage",
                        messageSource.getMessage(notAllowed.messageKey(), arguments.toArray(), locale));
            }
        }
        return "redirect:" + CLOSING_SECTION;
    }

    @GetMapping("/dashboard/reports/productWeightOriginComplianceExport")
    public void productWeightOriginComplianceExport(@RequestParam("dateFrom") String dateFrom, @RequestParam("dateTo") String dateTo, HttpServletResponse response) throws IOException {
        byte[] csv = productWeightOriginComplianceReportExport.run(getStoreId(), LocalDate.parse(dateFrom), LocalDate.parse(dateTo));

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"product-weight-origin-compliance-" + dateFrom + "_" + dateTo + ".csv\"");
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        response.getOutputStream().write(csv);
    }

    @GetMapping("/dashboard/reports/purchaseExport")
    public void purchaseExport(@RequestParam("dateFrom") String dateFrom, @RequestParam("dateTo") String dateTo, HttpServletResponse response) throws IOException {
        byte[] csv = purchaseReportExport.run(getStoreId(), LocalDate.parse(dateFrom), LocalDate.parse(dateTo));

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"purchases-" + dateFrom + "_" + dateTo + ".csv\"");
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        response.getOutputStream().write(csv);
    }

    @GetMapping("/dashboard/reports/paymentsExport")
    public void paymentsExport(@RequestParam("dateFrom") String dateFrom, @RequestParam("dateTo") String dateTo, HttpServletResponse response) throws IOException {
        byte[] csv = paymentsExport.run(getStoreId(), LocalDate.parse(dateFrom), LocalDate.parse(dateTo));

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"payments-" + dateFrom + "_" + dateTo + ".csv\"");
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        response.getOutputStream().write(csv);
    }

    private static void writeStockLedger(HttpServletResponse response, String dateFrom, String dateTo, byte[] csv) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"stock-ledger-" + dateFrom + "_" + dateTo + ".csv\"");
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        response.getOutputStream().write(csv);
    }

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }

}
