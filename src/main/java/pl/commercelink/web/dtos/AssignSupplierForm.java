package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;

/**
 * "Assign supplier" dialog: the price arrives as text (comma or dot) and may be typed net or gross. The manufacturer
 * code is optional: left empty, it is looked up by the EAN.
 */
@Getter
@Setter
public class AssignSupplierForm {

    private String itemId;
    private String ean;
    private String cost;
    private String priceType = "net";
    private String supplier;
    private String customSupplier;
    private String manufacturerCode;

    public static AssignSupplierForm of(String itemId, String ean, String cost, String priceType,
                                        String supplier, String customSupplier) {
        AssignSupplierForm form = new AssignSupplierForm();
        form.itemId = itemId;
        form.ean = ean;
        form.cost = cost;
        form.priceType = priceType;
        form.supplier = supplier;
        form.customSupplier = customSupplier;
        return form;
    }

    public boolean isGross() {
        return "gross".equals(priceType);
    }
}
