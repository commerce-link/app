package pl.commercelink.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.financials.*;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@PreAuthorize("hasRole('ADMIN')")
public class FinancialReportsController {

    @Autowired
    private OrdersExport ordersExport;

    @Autowired
    private GoogleOfflineConversionsExport googleOfflineConversionsExport;

    @Autowired
    private FinancialReportGenerator financialReportGenerator;

    @GetMapping("/dashboard/reports")
    public String reports(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                  Model model) {
        if (dateFrom == null || dateTo == null) {
            LocalDate now = LocalDate.now();
            dateFrom = now.minusMonths(1).withDayOfMonth(1);
            dateTo = dateFrom.withDayOfMonth(dateFrom.lengthOfMonth());
        }

        FinancialReport report = financialReportGenerator.generate(getStoreId(), dateFrom, dateTo);

        Map<String, Integer> salesVolumeByProvider = report.getSalesVolumeByProvider();
        List<String> providerNames = new ArrayList<>(salesVolumeByProvider.keySet());
        List<Integer> providerSales = new ArrayList<>(salesVolumeByProvider.values());

        model.addAttribute("report", report);
        model.addAttribute("providerNames", providerNames);
        model.addAttribute("providerSales", providerSales);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);

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

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }

}
