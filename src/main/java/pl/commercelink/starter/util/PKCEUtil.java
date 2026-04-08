package pl.commercelink.starter.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PKCEUtil {

    public static String generateCodeVerifier() {
        SecureRandom random = new SecureRandom();
        return IntStream.range(0, 40)
                .mapToObj(i -> String.valueOf((char) (random.nextInt(26) + 'a')))
                .collect(Collectors.joining());
    }

    public static String generateCodeChallenge(String codeVerifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
}
