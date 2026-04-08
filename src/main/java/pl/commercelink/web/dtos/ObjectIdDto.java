package pl.commercelink.web.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ObjectIdDto {
    @JsonProperty("id")
    private String id;

    private ObjectIdDto() {
    }

    public ObjectIdDto(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
