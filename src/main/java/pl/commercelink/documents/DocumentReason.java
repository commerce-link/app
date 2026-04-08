package pl.commercelink.documents;

import pl.commercelink.starter.localization.LocalizedEnum;

public enum DocumentReason implements LocalizedEnum<DocumentReason> {
    SupplierDelivery("Dostawa od dostawcy"),
    CustomerReturn("Zwrot od klienta"),
    ServiceReturn("Powrót z serwisu"),
    CustomerOrder("Wydanie do klienta"),
    ServiceOut("Wysłanie do serwisu"),
    StockAdjustment("Korekta stanu"),
    Destruction("Zniszczenie"),
    InternalUse("Zużycie wewnętrzne"),
    Theft("Kradzież"),
    TransferIn("Przesunięcie przychodzące"),
    TransferOut("Przesunięcie wychodzące");

    private final String localizedName;

    DocumentReason(String localizedName) {
        this.localizedName = localizedName;
    }

    @Override
    public String getLocalizedName() {
        return localizedName;
    }
}
