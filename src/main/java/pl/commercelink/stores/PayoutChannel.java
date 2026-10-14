package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.commercelink.orders.PaymentSource;

/**
 * Orders that a payer settles many at a time with one transfer: a marketplace or gateway payout, cash on delivery
 * handed over by a carrier, the loans of an installment bank. A statement operation whose sender or title contains
 * {@code match} is offered the open orders this row describes; a payer may have several rows (the same bank paying
 * for loans taken in the shop and over the phone, one gateway paying out for two marketplaces, each with its own fees).
 * <p>
 * Every criterion left empty means any: {@code orderSource} is {@link #SHOP} for the store's own orders or a
 * marketplace's name, {@code method} the payment method the order waits for, {@code provider} the name its payment
 * carries (a gateway) or a carrier of its shipments. With {@code netOfFees} the transfer is the orders' amounts less
 * the fees recorded on their payments.
 */
@DynamoDBDocument
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PayoutChannel {

    public static final String SHOP = "SHOP";

    @DynamoDBAttribute(attributeName = "match")
    private String match;
    @DynamoDBAttribute(attributeName = "orderSource")
    private String orderSource;
    @DynamoDBAttribute(attributeName = "method")
    @DynamoDBTypeConvertedEnum
    private PaymentSource method;
    @DynamoDBAttribute(attributeName = "provider")
    private String provider;
    @DynamoDBAttribute(attributeName = "netOfFees")
    private boolean netOfFees;
}
