package pl.commercelink.externaloffers;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Links an offer published by another system on one of the store's order sources (today a marketplace), identified by
 * that system's own offer key (e.g. the Empik {@code offer_sku}), to the EANs of the product it stands for (several
 * when the source sells one offer for a set of equivalent EANs).
 */
@DynamoDBTable(tableName = ExternalOfferLink.TABLE_NAME)
@Getter
@Setter
@NoArgsConstructor
public class ExternalOfferLink {

    public static final String TABLE_NAME = "ExternalOfferLinks";

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;
    @DynamoDBRangeKey(attributeName = "linkKey")
    private String linkKey;
    // the name of the orders' source the offer key belongs to (OrderSource.name, e.g. a marketplace: "Empik")
    @DynamoDBAttribute(attributeName = "orderSource")
    private String orderSource;
    @DynamoDBAttribute(attributeName = "offerKey")
    private String offerKey;
    @DynamoDBAttribute(attributeName = "eans")
    private List<String> eans;
    @DynamoDBAttribute(attributeName = "source")
    @DynamoDBTypeConvertedEnum
    private ExternalOfferLinkSource source;
    @DynamoDBAttribute(attributeName = "createdAt")
    @DynamoDBTypeConverted(converter = ExternalOfferLinkInstantConverter.class)
    private Instant createdAt;
    @DynamoDBAttribute(attributeName = "createdBy")
    private String createdBy;

    public static ExternalOfferLink of(String storeId, String orderSource, String offerKey,
                                          Collection<String> eans, ExternalOfferLinkSource source,
                                          String createdBy, Instant now) {
        ExternalOfferLink link = new ExternalOfferLink();
        link.storeId = storeId;
        link.linkKey = linkKey(orderSource, offerKey);
        link.orderSource = orderSource;
        link.offerKey = offerKey;
        link.eans = unifiedDistinct(eans);
        link.source = source;
        link.createdBy = createdBy;
        link.createdAt = now;
        return link;
    }

    private static List<String> unifiedDistinct(Collection<String> eans) {
        LinkedHashSet<String> unified = new LinkedHashSet<>();
        if (eans != null) {
            for (String ean : eans) {
                String value = OfferEans.unify(ean);
                if (value != null) {
                    unified.add(value);
                }
            }
        }
        return new ArrayList<>(unified);
    }

    /**
     * Not normalised: the offer key comes from the orders' source and is matched verbatim. It is not stable on every
     * marketplace (an Allegro signature can be edited), so a changed key needs a new link.
     */
    public static String linkKey(String orderSource, String offerKey) {
        return orderSource + "#" + offerKey;
    }
}
