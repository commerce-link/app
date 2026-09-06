package pl.commercelink.financials;

import pl.commercelink.starter.csv.CSVReady;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

public record PaymentsReportRow(
        String type,
        String number,
        LocalDateTime registeredAt,
        String direction,
        Double amount,
        Double fee,
        String referenceNo,
        String bankTransactionNo,
        LocalDate bankTransactionDate,
        String source
) implements CSVReady {

    public static String[] headers() {
        return new String[]{
                "Typ", "Numer", "Data rejestracji", "Kierunek", "Kwota płatności", "Prowizja",
                "Nr referencyjny", "Nr operacji bankowej", "Data operacji bankowej", "Metoda płatności"
        };
    }

    @Override
    public String[] asStringArray() {
        return new String[]{
                type,
                number != null ? number : "",
                registeredAt != null ? registeredAt.toLocalDate().toString() : "",
                direction != null ? direction : "",
                formatMoney(amount),
                formatMoney(fee),
                referenceNo != null ? referenceNo : "",
                bankTransactionNo != null ? bankTransactionNo : "",
                bankTransactionDate != null ? bankTransactionDate.toString() : "",
                source != null ? source : ""
        };
    }

    private static String formatMoney(Double value) {
        return value != null ? String.format(Locale.US, "%.2f", value).replace('.', ',') : "";
    }
}
