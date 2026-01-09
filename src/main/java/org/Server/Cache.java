package org.Server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/*
 * LRU Cache usando LinkedHashMap com accessOrder=true.
 * O LinkedHashMap gere automaticamente a ordem de acesso (LRU) e a evição.
 */
public class Cache {

    // Record gera equals() e hashCode() automaticamente - essencial para chaves de Map
    public record CacheKey(int day, String product) {}

    // Dados guardados para cada chave
    private static class CacheData {
        Integer quantidade;
        Double volume;
        Double maxPrice;
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<CacheKey, CacheData> map;

    public Cache(int maxCapacity) {
        // accessOrder=true: cada get() move a entrada para o fim (mais recente)
        // removeEldestEntry: remove automaticamente a entrada mais antiga quando excede capacidade
        this.map = new LinkedHashMap<>(maxCapacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, CacheData> eldest) {
                return size() > maxCapacity;
            }
        };
    }

    // --- MÉTODOS PÚBLICOS (Getters) ---

    public Integer getQuantidade(int day, String product) {
        lock.lock();
        try {
            CacheData data = map.get(new CacheKey(day, product));
            return data != null ? data.quantidade : null;
        } finally {
            lock.unlock();
        }
    }

    public Double getVolume(int day, String product) {
        lock.lock();
        try {
            CacheData data = map.get(new CacheKey(day, product));
            return data != null ? data.volume : null;
        } finally {
            lock.unlock();
        }
    }

    public Double getMaxPrice(int day, String product) {
        lock.lock();
        try {
            CacheData data = map.get(new CacheKey(day, product));
            return data != null ? data.maxPrice : null;
        } finally {
            lock.unlock();
        }
    }

    // --- MÉTODOS PÚBLICOS (Setters) ---

    public void setQuantidade(int day, String product, int valor) {
        lock.lock();
        try {
            CacheKey key = new CacheKey(day, product);
            map.computeIfAbsent(key, k -> new CacheData()).quantidade = valor;
        } finally {
            lock.unlock();
        }
    }

    public void setVolume(int day, String product, double valor) {
        lock.lock();
        try {
            CacheKey key = new CacheKey(day, product);
            map.computeIfAbsent(key, k -> new CacheData()).volume = valor;
        } finally {
            lock.unlock();
        }
    }

    public void setMaxPrice(int day, String product, double valor) {
        lock.lock();
        try {
            CacheKey key = new CacheKey(day, product);
            map.computeIfAbsent(key, k -> new CacheData()).maxPrice = valor;
        } finally {
            lock.unlock();
        }
    }
}