package pl.commercelink.starter.email;

import java.io.*;
import java.net.*;
import java.util.regex.*;

import pl.commercelink.templates.EmailAttachment;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sesv2.model.Attachment;

public class EmailAttachmentBuilder {

    public static Attachment createAttachment(EmailAttachment emailAttachment) {
        try {
            String downloadUrl = emailAttachment.getUrl();
            if (isGoogleDriveUrl(downloadUrl)) {
                String fileId = extractFileIdFromDriveUrl(downloadUrl);
                downloadUrl = "https://drive.google.com/uc?export=download&id=" + fileId;
            }

            byte[] fileBytes = fetchFileFromUrl(downloadUrl);
            return createAttachmentFromBytes(fileBytes, emailAttachment.getName());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch attachment from URL: " + emailAttachment.getUrl() + " - " + e.getMessage());
        }
    }

    public static Attachment createAttachmentFromBytes(byte[] fileBytes, String fileName) {
        return Attachment.builder()
                .fileName(fileName)
                .rawContent(SdkBytes.fromByteArray(fileBytes))
                .contentType("application/pdf")
                .contentDisposition("ATTACHMENT")
                .contentTransferEncoding("BASE64")
                .build();
    }

    private static boolean isGoogleDriveUrl(String url) {
        return url.contains("drive.google.com") && url.contains("/d/");
    }

    private static String extractFileIdFromDriveUrl(String sharedUrl) throws IllegalArgumentException {
        Pattern pattern = Pattern.compile("/d/([a-zA-Z0-9_-]+)");
        Matcher matcher = pattern.matcher(sharedUrl);

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new IllegalArgumentException("Invalid Google Drive shared URL format: " + sharedUrl);
        }
    }

    private static byte[] fetchFileFromUrl(String url) throws Exception {
        try (InputStream in = new URL(url).openStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }
}
