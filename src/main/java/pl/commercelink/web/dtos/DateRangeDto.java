package pl.commercelink.web.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class DateRangeDto {
    @JsonProperty("dateFrom")
    private LocalDate dateFrom;

    @JsonProperty("dateTo")
    private LocalDate dateTo;

    public DateRangeDto() {
    }

    public DateRangeDto(LocalDate dateFrom, LocalDate dateTo) {
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public static List<DateRangeDto> generateDateRanges(LocalDate startDate) {
        List<DateRangeDto> dateRanges = new ArrayList<>();
        LocalDate currentDate = LocalDate.now();
        YearMonth startMonth = YearMonth.from(startDate);
        YearMonth currentMonth = YearMonth.from(currentDate);

        while (!startMonth.isAfter(currentMonth)) {
            LocalDate dateFrom = startMonth.atDay(1);
            LocalDate dateTo = startMonth.atEndOfMonth();
            dateRanges.add(new DateRangeDto(dateFrom, dateTo));
            startMonth = startMonth.plusMonths(1);
        }

        return dateRanges;
    }
}
