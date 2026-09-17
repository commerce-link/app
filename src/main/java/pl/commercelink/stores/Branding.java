package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import lombok.Getter;
import lombok.Setter;

@DynamoDBDocument
@Getter
@Setter
public class Branding {

    @DynamoDBAttribute(attributeName = "logo")
    private String logo;
    // Changes only when a new file is stored; the file keeps its address, so the dashboard adds this to bypass the
    // browser cache for a replaced logo without re-fetching an unchanged one on every save.
    @DynamoDBAttribute(attributeName = "logoVersion")
    private Long logoVersion;
    @DynamoDBAttribute(attributeName = "primaryColor")
    private String primaryColor;

    /** The primary colour for customer pages, or null when the stored value is not a hex colour. */
    @DynamoDBIgnore
    public String getSafePrimaryColor() {
        return BrandColor.normalize(primaryColor).orElse(null);
    }
}
