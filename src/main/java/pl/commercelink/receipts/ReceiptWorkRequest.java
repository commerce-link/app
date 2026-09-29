package pl.commercelink.receipts;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** "Wake this attempt up": the attempt itself says what to do. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ReceiptWorkRequest {
    private String storeId;
    private String receiptKey;
}
