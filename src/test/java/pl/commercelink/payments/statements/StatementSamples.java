package pl.commercelink.payments.statements;

import pl.commercelink.stores.BankStatementFormat;

import java.nio.charset.Charset;
import java.util.List;

/** Statement files as Polish banks export them, for the parser, the sniffer and the settings page tests. */
final class StatementSamples {

    static final String MBANK = String.join("\r\n",
            "mBank S.A. Bankowość Detaliczna;",
            "#Klient;",
            "DEMO STORE SP. Z O.O.;",
            ";",
            "#Za okres:;",
            "01.10.2026;13.10.2026;",
            ";",
            "#Waluta;PLN;",
            "#Data księgowania;#Data operacji;#Opis operacji;#Tytuł;#Nadawca/Odbiorca;#Numer konta;#Kwota;#Saldo po operacji;",
            "2026-10-07;2026-10-07;\"PRZELEW ZEWNĘTRZNY PRZYCHODZĄCY\";\"Zamowienie 3f9a2c1e-7b4d-4e1a-9c2f-5d8e6a7b9c01\";\"JAN KOWALSKI  UL. LIPOWA 4\";\"'83102018110000030201459921'\";1 249,00;10 249,00;",
            "2026-10-07;2026-10-07;\"PRZELEW ZEWNĘTRZNY WYCHODZĄCY\";\"Faktura FS 1187/09/2026\";\"AB S.A.\";\"'12345678901234567890123456'\";-2 310,50;7 938,50;",
            "2026-10-08;2026-10-08;\"PRZELEW ZEWNĘTRZNY PRZYCHODZĄCY\";\"zakupy\";\"MAGDALENA KRÓL\";\"'27105014451000009031218812'\";159,00;8 097,50;",
            "2026-10-08;2026-10-08;\"PRZELEW ZEWNĘTRZNY PRZYCHODZĄCY\";\"zakupy\";\"MAGDALENA KRÓL\";\"'27105014451000009031218812'\";159,00;8 256,50;",
            ";;;;;#Saldo końcowe;8 256,50;",
            "");

    private StatementSamples() {
    }

    static byte[] mbank() {
        return MBANK.getBytes(Charset.forName("windows-1250"));
    }

    static BankStatementFormat mbankFormat() {
        BankStatementFormat format = new BankStatementFormat();
        format.setEncoding("windows-1250");
        format.setSeparator(";");
        format.setHeaderRow(9);
        format.setDateFormat("yyyy-MM-dd");
        format.setDecimalComma(true);
        format.setDateColumn("#Data księgowania");
        format.setAmountColumn("#Kwota");
        format.setTitleColumns(List.of("#Opis operacji", "#Tytuł"));
        format.setSenderColumn("#Nadawca/Odbiorca");
        format.setSenderAccountColumn("#Numer konta");
        return format;
    }
}
