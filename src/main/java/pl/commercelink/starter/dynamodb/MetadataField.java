package pl.commercelink.starter.dynamodb;

public class MetadataField {
    private String name;
    private String description;

    public MetadataField(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
