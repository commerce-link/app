package pl.commercelink.starter.storage;

import org.springframework.http.MediaType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileImageStorage {

    public static final List<String> EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "gif", "webp");

    private final FileStorage fileStorage;
    private final String bucketName;
    private final Map<String, String> mimeTypes;

    public FileImageStorage(FileStorage fileStorage, String bucketName) {
        this.fileStorage = fileStorage;
        this.bucketName = bucketName;

        this.mimeTypes = new HashMap<>();
        mimeTypes.put("jpg", "image/jpeg");
        mimeTypes.put("jpeg", "image/jpeg");
        mimeTypes.put("png", "image/png");
        mimeTypes.put("gif", "image/gif");
        mimeTypes.put("webp", "image/webp");
    }

    public void storeImage(String location, byte[] image) {
        fileStorage.put(bucketName, location, image);
    }

    public byte[] getImage(String location) {
        return fileStorage.canRead(bucketName, location) ? fileStorage.getBytes(bucketName, location) : null;
    }

    public void deleteImage(String location) {
        fileStorage.delete(bucketName, location);
    }

    public MediaType getMediaType(String fileName) {
        String ext = getFileExtension(fileName).toLowerCase();
        String mimeType = mimeTypes.getOrDefault(ext, "application/octet-stream");
        return MediaType.parseMediaType(mimeType);
    }

    public String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return (lastDotIndex > 0) ? fileName.substring(lastDotIndex + 1) : "";
    }

}
