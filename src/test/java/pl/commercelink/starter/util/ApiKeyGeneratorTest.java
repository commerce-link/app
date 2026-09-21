package pl.commercelink.starter.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyGeneratorTest {

    @Test
    void generatesUrlSafeHighEntropyKeys() {
        // when
        String key = ApiKeyGenerator.generate();

        // then
        assertTrue(key.length() >= 40, "expected a long key, was: " + key.length());
        assertTrue(key.matches("[A-Za-z0-9_-]+"), "expected url-safe characters, was: " + key);
    }

    @Test
    void generatesDistinctKeys() {
        // given
        Set<String> keys = new HashSet<>();

        // when
        for (int i = 0; i < 1000; i++) {
            keys.add(ApiKeyGenerator.generate());
        }

        // then
        assertEquals(1000, keys.size());
    }

    @Test
    void hashesDeterministicallyAsSha256Hex() {
        // when
        String hash = ApiKeyGenerator.hash("some-key");

        // then
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertEquals(hash, ApiKeyGenerator.hash("some-key"));
    }

    @Test
    void differentKeysProduceDifferentHashes() {
        // when / then
        assertNotEquals(ApiKeyGenerator.hash("key-a"), ApiKeyGenerator.hash("key-b"));
    }
}
