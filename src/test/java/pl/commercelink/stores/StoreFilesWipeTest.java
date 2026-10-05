package pl.commercelink.stores;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteMarkerEntry;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.S3Error;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreFilesWipeTest {

    @Mock private S3Client s3Client;

    @InjectMocks
    private StoreFilesWipe wipe;

    private static ObjectVersion version(String key, String versionId) {
        return ObjectVersion.builder().key(key).versionId(versionId).build();
    }

    private static DeleteMarkerEntry marker(String key, String versionId) {
        return DeleteMarkerEntry.builder().key(key).versionId(versionId).build();
    }

    @Test
    void deletesEveryVersionAndDeleteMarkerPageByPage() {
        // given
        ListObjectVersionsResponse first = ListObjectVersionsResponse.builder()
                .versions(version("s1/a.csv", "v2"), version("s1/a.csv", "v1"))
                .deleteMarkers(marker("s1/b.csv", "m1"))
                .isTruncated(true).nextKeyMarker("s1/b.csv").nextVersionIdMarker("m1")
                .build();
        ListObjectVersionsResponse second = ListObjectVersionsResponse.builder()
                .versions(version("s1/c.csv", "v1"))
                .isTruncated(false)
                .build();
        when(s3Client.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(first, second);
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder().build());

        // when
        wipe.deleteAllVersions("stores", "s1/");

        // then
        ArgumentCaptor<ListObjectVersionsRequest> listed = ArgumentCaptor.forClass(ListObjectVersionsRequest.class);
        verify(s3Client, times(2)).listObjectVersions(listed.capture());
        assertThat(listed.getAllValues().get(0).prefix()).isEqualTo("s1/");
        assertThat(listed.getAllValues().get(0).keyMarker()).isNull();
        assertThat(listed.getAllValues().get(1).keyMarker()).isEqualTo("s1/b.csv");
        assertThat(listed.getAllValues().get(1).versionIdMarker()).isEqualTo("m1");

        ArgumentCaptor<DeleteObjectsRequest> deleted = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client, times(2)).deleteObjects(deleted.capture());
        assertThat(deleted.getAllValues().get(0).bucket()).isEqualTo("stores");
        assertThat(deleted.getAllValues().get(0).delete().objects()).containsExactly(
                ObjectIdentifier.builder().key("s1/a.csv").versionId("v2").build(),
                ObjectIdentifier.builder().key("s1/a.csv").versionId("v1").build(),
                ObjectIdentifier.builder().key("s1/b.csv").versionId("m1").build());
        assertThat(deleted.getAllValues().get(1).delete().objects()).containsExactly(
                ObjectIdentifier.builder().key("s1/c.csv").versionId("v1").build());
    }

    @Test
    void emptyPrefixDeletesNothing() {
        // given
        when(s3Client.listObjectVersions(any(ListObjectVersionsRequest.class)))
                .thenReturn(ListObjectVersionsResponse.builder().isTruncated(false).build());

        // when
        wipe.deleteAllVersions("stores", "s1/");

        // then
        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void objectThatCouldNotBeDeletedFailsTheWipe() {
        // given
        when(s3Client.listObjectVersions(any(ListObjectVersionsRequest.class)))
                .thenReturn(ListObjectVersionsResponse.builder()
                        .versions(version("s1/a.csv", "v1")).isTruncated(false).build());
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder()
                .errors(S3Error.builder().key("s1/a.csv").code("AccessDenied").build())
                .build());

        // when / then
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> wipe.deleteAllVersions("stores", "s1/"));
        assertThat(failure.getMessage()).contains("s1/a.csv").contains("AccessDenied");
    }
}
