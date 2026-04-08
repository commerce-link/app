package pl.commercelink.web.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SizeDto {
    @JsonProperty("size")
    private int size;

    public SizeDto(int size) {
        this.size = size;
    }

    public int getSize() {
        return size;
    }
}
