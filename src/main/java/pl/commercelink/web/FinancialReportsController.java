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
import pl.commercelink.reports.ReportGenerationPublisher;
import pl.commercelink.reports.ReportType;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.Blocked;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.NotAllowed;
import pl.commercelink.warehouse.builtin.StockLedgerPeriod;
import pl.commercelink.warehouse.builtin.StockLedgerPeriodClosing;
import pl.commercelink.web.reports.ReportPeriod;
import pl.commercelink.web.reports.ReportsPageFactory;
import pl.commercelink.web.reports.StockLedgerClosingView;
import pl.commercelink.web.reports.StockLedgerPeriodLabel;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.reports.StockLedgerClosingBlocker;
import pl.commercelink.web.reports.StockLedgerClosingWarning;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Controller
@PreAuthorize("hasRole('ADMIN')")
public class FinancialReportsController {

    private static final String REPORTS_PATH = "/dashboard/reports";
    private static final String STOCK_LEDGER_PATH = REPORTS_PATH + "/stock-ledger";

    @Autowired
    private OrdersExport ordersExport;

    @Autowired
    private GoogleOfflineConversionsExport googleOfflineConversionsExport;

    @Autowired
    private ReportGenerationPublisher reportGenerationPublisher;


    @Autowired
    private StockLedgerExport stockLedgerExport;

    @Autowired
    private ProductWeightOriginComplianceReportExport productWeightOriginComplianceReportExport;

    @Autowired
    private PurchaseReportExport purchaseReportExport;

    @Autowired
    private PaymentsExport paymentsExport;

    @Autowired
    private ReportsPageFactory reportsPageFactory;

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
        model.addAttribute("page", reportsPageFactory.create(getStoreId(), dateFrom, dateTo, LocalDate.now(), locale));
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

    @GetMapping(STOCK_LEDGER_PATH)
    public String stockLedger(Model model, Locale locale,
                              @RequestParam(value = "from", required = false) String from,
                              @RequestParam(value = "to", required = false) String to) {
        ReportPeriod fallback = ReportPeriod.of(null, null, LocalDate.now());
        List<StockLedgerPeriod> closedPeriods = stockLedgerPeriodClosing.closedPeriods(getStoreId());
        Map<StockLedgerPeriod, Instant> generatedAt = new HashMap<>();
        closedPeriods.forEach(closed -> stockLedgerPeriodClosing.generatedAt(getStoreId(), closed)
                .ifPresent(moment -> generatedAt.put(closed, moment)));
        StockLedgerClosingView closing = StockLedgerClosingView.of(closedPeriods, generatedAt, fallback.from(), fallback.to(), locale);
        // after a held closing the form keeps the range that was held, so it can be closed once settled
        StockLedgerClosingWarning warning = (StockLedgerClosingWarning) model.getAttribute("ledgerWarning");
        ReportPeriod report = reportPeriod(from, to);
        model.addAttribute("backHref", report == null ? REPORTS_PATH : REPORTS_PATH + "?" + report.query());
        model.addAttribute("report", report);
        model.addAttribute("ledgerClosing", warning == null ? closing : closing.withRange(warning.dateFrom(), warning.dateTo()));
        return "reports/stock-ledger";
    }

    /** The report the page was opened from, carried through its forms so "‹ Reports" returns to it; none if unreadable. */
    private static ReportPeriod reportPeriod(String from, String to) {
        if (from == null || to == null) {
            return null;
        }
        try {
            return new ReportPeriod(LocalDate.parse(from), LocalDate.parse(to));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String stockLedgerPath(ReportPeriod report) {
        return report == null ? STOCK_LEDGER_PATH : STOCK_LEDGER_PATH + "?from=" + report.from() + "&to=" + report.to();
    }

    /**
     * Without JavaScript "Generate again" opens this page; with it, confirm-dialog.js posts to the same address. Only a
     * closed period is generated again, so any other range goes back to the closing page.
     */
    @GetMapping(STOCK_LEDGER_PATH + "/close")
    public String confirmStockLedgerRegeneration(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                                 @RequestParam(value = "from", required = false) String from,
                                                 @RequestParam(value = "to", required = false) String to,
                                                 Model model, Locale locale) {
        StockLedgerPeriod period = new StockLedgerPeriod(dateFrom, dateTo);
        String back = stockLedgerPath(reportPeriod(from, to));
        if (!stockLedgerPeriodClosing.closedPeriods(getStoreId()).contains(period)) {
            return "redirect:" + back;
        }
        String label = StockLedgerPeriodLabel.of(period, locale);
        ReportPeriod report = reportPeriod(from, to);
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("reports.stockLedger.regenerate.confirm.title", new Object[]{label}, locale),
                messageSource.getMessage("reports.stockLedger.regenerate.confirm.message", null, locale),
                messageSource.getMessage("reports.stockLedger.closing.regenerate", null, locale),
                STOCK_LEDGER_PATH + "/close?dateFrom=" + dateFrom + "&dateTo=" + dateTo
                        + (report == null ? "" : "&from=" + report.from() + "&to=" + report.to()),
                back, false));
        model.addAttribute("backLabel", messageSource.getMessage("reports.stockLedger.closing.title", null, locale));
        return "settings-confirm";
    }

    /** Orders the closing of the range, or generating it again when it is closed already; a refusal is answered at once. */
    @PostMapping(STOCK_LEDGER_PATH + "/close")
    public String closeStockLedgerPeriod(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                         @RequestParam(value = "from", required = false) String from,
                                         @RequestParam(value = "to", required = false) String to,
                                         RedirectAttributes redirectAttributes, Locale locale) {
        StockLedgerPeriod period = new StockLedgerPeriod(dateFrom, dateTo);
        Object[] label = {StockLedgerPeriodLabel.of(period, locale)};
        Optional<StockLedgerClosingResult> refusal = stockLedgerPeriodClosing.check(getStoreId(), period);
        if (refusal.isEmpty()) {
            reportGenerationPublisher.order(getStoreId(), ReportType.STOCK_LEDGER_CLOSING, dateFrom, dateTo);
            redirectAttributes.addFlashAttribute("ledgerSuccess",
                    messageSource.getMessage("reports.stockLedger.closing.accepted", label, locale));
        } else if (refusal.get() instanceof Blocked blocked) {
            SupplierLabelMap labels = supplierLabels.forStoreId(getStoreId());
            redirectAttributes.addFlashAttribute("ledgerError",
                    messageSource.getMessage("reports.stockLedger.closing.blocked", label, locale));
            redirectAttributes.addFlashAttribute("ledgerWarning", new StockLedgerClosingWarning(dateFrom, dateTo,
                    blocked.deliveries().stream().map(delivery -> StockLedgerClosingBlocker.of(delivery, labels)).toList()));
        } else if (refusal.get() instanceof NotAllowed notAllowed) {
            List<Object> arguments = new ArrayList<>(List.of(label));
            // a day reads like the rest of the page ("1.10.2026"), not as an ISO date
            notAllowed.arguments().forEach(argument -> arguments.add(
                    argument instanceof LocalDate day ? new ReportPeriod(day, day).rangeText() : argument));
            redirectAttributes.addFlashAttribute("ledgerError",
                    messageSource.getMessage(notAllowed.messageKey(), arguments.toArray(), locale));
        }
        return "redirect:" + stockLedgerPath(reportPeriod(from, to));
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
