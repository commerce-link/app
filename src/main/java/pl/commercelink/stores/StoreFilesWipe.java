package pl.commercelink.stores;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Error;

import java.util.ArrayList;
import java.util.List;

/**
 * Deletes every file of a store for good. The bucket keeps old versions, so deleting only the current objects would
 * leave the store's data recoverable: every version and every delete marker under the prefix goes.
 */
@Component
@RequiredArgsConstructor
class StoreFilesWipe {

    private final S3Client s3Client;

    void deleteAllVersions(String bucket, String prefix) {
        ListObjectVersionsRequest request = ListObjectVersionsRequest.builder().bucket(bucket).prefix(prefix).build();
        ListObjectVersionsResponse page;
        do {
            page = s3Client.listObjectVersions(request);
            // a page lists at most 1000 versions and markers together, as many as one delete request takes
            List<ObjectIdentifier> objects = new ArrayList<>();
            page.versions().forEach(version -> objects.add(identifier(version.key(), version.versionId())));
            page.deleteMarkers().forEach(marker -> objects.add(identifier(marker.key(), marker.versionId())));
            if (!objects.isEmpty()) {
                delete(bucket, objects);
            }
            request = request.toBuilder()
                    .keyMarker(page.nextKeyMarker())
                    .versionIdMarker(page.nextVersionIdMarker())
                    .build();
        } while (Boolean.TRUE.equals(page.isTruncated()));
    }

    private void delete(String bucket, List<ObjectIdentifier> objects) {
        DeleteObjectsResponse response = s3Client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder().objects(objects).quiet(true).build())
                .build());
        if (response.hasErrors() && !response.errors().isEmpty()) {
            S3Error first = response.errors().getFirst();
            throw new IllegalStateException(response.errors().size() + " objects in " + bucket
                    + " could not be deleted, first " + first.key() + ": " + first.code());
        }
    }

    private static ObjectIdentifier identifier(String key, String versionId) {
        return ObjectIdentifier.builder().key(key).versionId(versionId).build();
    }
}
