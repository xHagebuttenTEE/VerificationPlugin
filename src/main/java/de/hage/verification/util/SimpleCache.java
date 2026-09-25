package de.hage.verification.util;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SimpleCache<K, V> {

    private final long ttlMillis;
    private final Map<K, Entry<V>> cache = new ConcurrentHashMap<>();

    public SimpleCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public void put(K key, V value) {
        cache.put(key, new Entry<>(value, System.currentTimeMillis() + ttlMillis));
    }

    public Optional<V> get(K key) {
        Entry<V> entry = cache.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt < System.currentTimeMillis()) {
            cache.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value);
    }

    public void invalidate(K key) {
        cache.remove(key);
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    private static final class Entry<V> {
        private final V value;
        private final long expiresAt;

        private Entry(V value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }
}