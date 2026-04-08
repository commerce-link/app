package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@DynamoDBDocument
public class PackageTemplate {
    @DynamoDBAttribute(attributeName = "id")
    private String id;
    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "default")
    private boolean isDefault;
    @DynamoDBAttribute(attributeName = "parcels")
    private List<Parcel> parcels = new LinkedList<>();

    public PackageTemplate() {
    }

    public PackageTemplate(String name, List<Parcel> parcels) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.parcels = parcels;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public List<Parcel> getParcels() {
        return parcels;
    }

    public void setParcels(List<Parcel> parcels) {
        this.parcels = parcels;
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        return isNotBlank(id) && isNotBlank(name);
    }
}
