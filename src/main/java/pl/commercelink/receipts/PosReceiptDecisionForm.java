package pl.commercelink.receipts;

import lombok.Getter;
import lombok.Setter;
import pl.commercelink.stores.PosReceiptMode;

/**
 * The receipt decision sent with a point-of-sale order's move to Delivered from the order status form. {@code choice}
 * is {@link PosReceiptMode#CASH_REGISTER} (with the printed receipt's number) or {@link PosReceiptMode#E_RECEIPT}
 * (with the customer's e-mail).
 */
@Getter
@Setter
public class PosReceiptDecisionForm {

    private PosReceiptMode choice;
    private String receiptNumber;
    private String customerEmail;
}
