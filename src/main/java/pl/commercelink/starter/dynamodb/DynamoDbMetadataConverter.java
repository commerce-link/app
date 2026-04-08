package pl.commercelink.starter.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class DynamoDbMetadataConverter implements DynamoDBTypeConverter<Map<String, String>, List<Metadata>> {

    @Override
    public Map<String, String> convert(List<Metadata> object) {
        Map<String, String> map = new HashMap<>();
        for (Metadata metadata : object) {
            map.put(metadata.getKey(), metadata.getValue());
        }
        return map;
    }

    @Override
    public List<Metadata> unconvert(Map<String, String> object) {
        List<Metadata> list = new LinkedList<>();
        for (Map.Entry<String, String> entry : object.entrySet()) {
            Metadata metadata = new Metadata();
            metadata.setKey(entry.getKey());
            metadata.setValue(entry.getValue());
            list.add(metadata);
        }
        return list;
    }
}