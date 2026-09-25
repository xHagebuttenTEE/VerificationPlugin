package de.hage.verification.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class SimpleCacheTest {

    @Test
    void putAndGetReturnsValue() {
        SimpleCache<String, Integer> cache = new SimpleCache<>(1000);
        cache.put("a", 1);
        Optional<Integer> result = cache.get("a");
        assertTrue(result.isPresent());
        assertEquals(1, result.get());
    }

    @Test
    void getReturnsEmptyForUnknownKey() {
        SimpleCache<String, Integer> cache = new SimpleCache<>(1000);
        assertFalse(cache.get("missing").isPresent());
    }

    @Test
    void entryExpiresAfterTtl() throws InterruptedException {
        SimpleCache<String, Integer> cache = new SimpleCache<>(50);
        cache.put("a", 1);
        Thread.sleep(80);
        assertFalse(cache.get("a").isPresent());
    }

    @Test
    void invalidateRemovesEntry() {
        SimpleCache<String, Integer> cache = new SimpleCache<>(1000);
        cache.put("a", 1);
        cache.invalidate("a");
        assertFalse(cache.get("a").isPresent());
    }

    @Test
    void clearRemovesAllEntries() {
        SimpleCache<String, Integer> cache = new SimpleCache<>(1000);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.clear();
        assertEquals(0, cache.size());
    }
}