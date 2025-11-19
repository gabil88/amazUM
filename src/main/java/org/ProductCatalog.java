package org;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Catálogo de produtos com mapeamento bidirecional entre IDs e nomes.
 * Estou a pensar usar isto para pelo menos serializar os produtos para disco.
 */
public class ProductCatalog {
    private Map<Integer, String> idToProduct;
    private Map<String, Integer> productToId;
    private Lock lock;

    public ProductCatalog() {
        this.idToProduct = new HashMap<>();
        this.productToId = new HashMap<>();
        this.lock = new ReentrantLock();
    }
    
    private void initProduct(int id, String name) {
        idToProduct.put(id, name);
        productToId.put(name, id);
    }

    public boolean addProduct(int id, String name) {
        lock.lock();
        try {
            if (!idToProduct.containsKey(id) && !productToId.containsKey(name)) {
                initProduct(id, name);
                return true;
            }
            return false; // Produto já existe
        } finally {
            lock.unlock();
        }
    }
    
    public Integer getProductId(String productName) {
        lock.lock();
        try {
            return productToId.get(productName);
        } finally {
            lock.unlock();
        }
    }
    
    public String getProductName(int productId) {
        lock.lock();
        try {
            return idToProduct.get(productId);
        } finally {
            lock.unlock();
        }
    }
    
    public boolean exists(String productName) {
        lock.lock();
        try {
            return productToId.containsKey(productName);
        } finally {
            lock.unlock();
        }
    }
    
    public Map<Integer, String> getAllProducts() {
        lock.lock();
        try {
            return new HashMap<>(idToProduct);
        } finally {
            lock.unlock();
        }
    }
}