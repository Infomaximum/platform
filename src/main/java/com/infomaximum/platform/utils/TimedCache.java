package com.infomaximum.platform.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.checkerframework.checker.nullness.qual.Nullable;

public class TimedCache<K, V> {

    private final ConcurrentHashMap<K, CacheEntry<V>> storage;
    private final ReentrantLock lock;
    private final long TTL_MS;
    private final AtomicLong counter = new AtomicLong(0);
    private final Consumer<V> onExpire;

    public TimedCache(long ttlMs) {
        this(ttlMs, null);
    }

    /**
     * @param ttlMs    время жизни записи в миллисекундах.
     * @param onExpire вызывается для значения записи, вытесняемой по истечении TTL
     *                 ({@code null} — нет колбэка). На явный {@link #remove(Object)} НЕ
     *                 вызывается — это позволяет «забрать» запись без срабатывания колбэка.
     */
    public TimedCache(long ttlMs, @Nullable Consumer<V> onExpire) {
        this.storage = new ConcurrentHashMap<>();
        this.lock = new ReentrantLock();
        this.TTL_MS = ttlMs;
        this.onExpire = onExpire;
    }

    public void put(K key, V value) {
        put(key, value, TTL_MS);
    }

    public void put(K key, V value, long duration, TimeUnit unit) {
        long ttlMs = unit.toMillis(duration);
        put(key, value, ttlMs);
    }

    public void put(K key, V value, long ttlMs) {
        long expirationTime = System.currentTimeMillis() + ttlMs;
        storage.put(key, new CacheEntry<>(value, expirationTime));
        if (counter.incrementAndGet() >= 100) {
            removeExpired();
        }
    }

    public V get(K key) {
        CacheEntry<V> entry = storage.get(key);
        if (entry == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (entry.isExpired(now)) {
            removeExpired();
            return null;
        }
        return entry.value;
    }

    public boolean containsKey(K key) {
        V value = get(key);
        return value != null;
    }

    public void remove(K key) {
        storage.remove(key);
    }

    /**
     * Атомарно извлекает и удаляет значение по ключу (one-shot): из конкурентных вызовов
     * с одним ключом значение получит ровно один. Просроченная запись не возвращается —
     * для неё срабатывает колбэк вытеснения.
     *
     * @param key ключ.
     * @return значение либо {@code null}, если ключа нет либо запись просрочена.
     */
    public @Nullable V poll(K key) {
        CacheEntry<V> entry = storage.remove(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(System.currentTimeMillis())) {
            if (onExpire != null) {
                onExpire.accept(entry.value);
            }
            return null;
        }
        return entry.value;
    }

    public int size() {
        return storage.size();
    }

    private void removeExpired() {
        List<V> expiredValues = null;
        try (LockGuard guard = new LockGuard(lock)) {
            counter.set(0);
            if (storage.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            if (onExpire == null) {
                storage.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
                return;
            }
            // Значения собираем под локом, а колбэк (возможный I/O) дёргаем уже без лока.
            List<V> collected = new ArrayList<>();
            storage.entrySet().removeIf(entry -> {
                if (entry.getValue().isExpired(now)) {
                    collected.add(entry.getValue().value);
                    return true;
                }
                return false;
            });
            expiredValues = collected;
        }
        if (expiredValues != null) {
            for (V value : expiredValues) {
                onExpire.accept(value);
            }
        }
    }

    private record CacheEntry<V>(V value, long expirationTime) {

        boolean isExpired(long currentTime) {
            return currentTime > expirationTime;
        }
    }
}
