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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.financials.*;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.Blocked;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.Closed;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.NotAllowed;
import pl.commercelink.warehouse.builtin.StockLedgerMonthClosing;
import pl.commercelink.web.reports.StockLedgerClosingBlocker;
import pl.commercelink.web.reports.StockLedgerClosingView;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private StockLedgerMonthClosing stockLedgerMonthClosing;

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
        model.addAttribute("ledgerClosing", StockLedgerClosingView.of(stockLedgerMonthClosing.status(getStoreId()), locale));

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

    @GetMapping("/dashboard/reports/stock-ledger/{month}")
    public void closedStockLedger(@PathVariable String month, HttpServletResponse response) throws IOException {
        YearMonth closed = parseMonth(month);
        byte[] csv = stockLedgerMonthClosing.closedReport(getStoreId(), closed)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        writeStockLedger(response, closed.atDay(1).toString(), closed.atEndOfMonth().toString(), csv);
    }

    @PostMapping("/dashboard/reports/stock-ledger/{month}/close")
    public String closeStockLedgerMonth(@PathVariable String month, RedirectAttributes redirectAttributes, Locale locale) throws IOException {
        YearMonth closing = parseMonth(month);
        return showClosingResult(stockLedgerMonthClosing.close(getStoreId(), closing), closing,
                "reports.stockLedger.closing.closed", "reports.stockLedger.closing.blocked", redirectAttributes, locale);
    }

    @PostMapping("/dashboard/reports/stock-ledger/{month}/regenerate")
    public String regenerateStockLedgerMonth(@PathVariable String month, RedirectAttributes redirectAttributes, Locale locale) throws IOException {
        YearMonth regenerated = parseMonth(month);
        return showClosingResult(stockLedgerMonthClosing.regenerate(getStoreId(), regenerated), regenerated,
                "reports.stockLedger.closing.regenerated", "reports.stockLedger.closing.regenerateBlocked", redirectAttributes, locale);
    }

    private String showClosingResult(StockLedgerClosingResult result, YearMonth month, String doneKey, String blockedKey,
                                     RedirectAttributes redirectAttributes, Locale locale) {
        String label = StockLedgerClosingView.label(month, locale);
        switch (result) {
            case Closed closed -> redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage(doneKey, new Object[]{label}, locale));
            case Blocked blocked -> {
                SupplierLabelMap labels = supplierLabels.forStoreId(getStoreId());
                redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(blockedKey, new Object[]{label}, locale));
                redirectAttributes.addFlashAttribute("ledgerBlockers",
                        blocked.deliveries().stream().map(delivery -> StockLedgerClosingBlocker.of(delivery, labels)).toList());
            }
            case NotAllowed notAllowed -> redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage(notAllowed.messageKey(), new Object[]{label}, locale));
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

    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }

}
