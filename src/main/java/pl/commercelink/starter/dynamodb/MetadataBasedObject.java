package pl.commercelink.starter.dynamodb;

import java.util.List;

public abstract class MetadataBasedObject<T> {

    private final T type;
    private final List<MetadataField> fieldsList;

    protected MetadataBasedObject(T type, List<MetadataField> fieldsList) {
        this.type = type;
        this.fieldsList = fieldsList;
    }

    public boolean canRun(List<Metadata> metadata) {
        for (MetadataField inventoryFilterField : fieldsList) {
            if (metadata.stream().noneMatch(m -> m.getKey().equals(inventoryFilterField.getName()))) {
                return false;
            }
        }
        return true;
    }

    public T getType() {
        return type;
    }

    public List<MetadataField> getFieldsList() {
        return fieldsList;
    }

    protected Metadata getMetadataByKey(List<Metadata> metadata, String key) {
        return metadata.stream().filter(m -> m.getKey().equals(key)).findFirst().get();
    }
}
