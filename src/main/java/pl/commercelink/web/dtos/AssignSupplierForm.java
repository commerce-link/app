package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;

/**
 * "Assign supplier" dialog: the price arrives as text (comma or dot) and may be typed net or gross. The EAN is optional:
 * left empty, it is looked up by the manufacturer code.
 */
@Getter
@Setter
public class AssignSupplierForm {

    private String itemId;
    private String manufacturerCode;
    private String cost;
    private String priceType = "net";
    private String supplier;
    private String customSupplier;
    private String ean;

    public static AssignSupplierForm of(String itemId, String manufacturerCode, String cost, String priceType,
                                        String supplier, String customSupplier) {
        AssignSupplierForm form = new AssignSupplierForm();
        form.itemId = itemId;
        form.manufacturerCode = manufacturerCode;
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
