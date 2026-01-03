package org.Server;

import java.util.List;
import java.util.Map;


/**
 * Handles query operations over sales data.
 * Usa cache de agregações por (dia, produto) para evitar recálculos.
 * Dias passados são imutáveis, então a cache nunca precisa de invalidação.
 */
public class Handlers {
    
    private final ServerDatabase database;
    private final Cache cache;
    
    public Handlers(ServerDatabase database, Cache cache) {
        this.database = database;
        this.cache = cache;
    }
    
    // ==================== Query Operations ====================
    
    /**
     * Calculates the average price per unit for a product over the last N days.
     * Usa volume/quantidade para calcular a média.
     * 
     * @param productName The name of the product
     * @param days Number of past days to consider
     * @return Average price per unit, or 0.0 if no sales found
     */
    public double getAveragePrice(String productName, int days) {
        int currentDay = database.getCurrentDay();
        
        double totalVolume = 0.0;
        int totalQuantity = 0;
        
        for (int i = 1; i <= days; i++) {
            int day = currentDay - i;
            if (day < 0) break;
            
            // Usa as agregações cacheadas
            totalVolume += getVolumeForDay(day, productName);
            totalQuantity += getQuantityForDay(day, productName);
        }

        return totalQuantity == 0 ? 0.0 : totalVolume / totalQuantity;
    }

    /**
     * Finds the maximum unit price for a product over the last N days.
     * 
     * @param productName The name of the product
     * @param days Number of past days to consider
     * @return Maximum unit price, or 0.0 if no sales found
     */
    public double getMaxPrice(String productName, int days) {
        int currentDay = database.getCurrentDay();
        
        double maxPrice = 0.0;
        
        for (int i = 1; i <= days; i++) {
            int day = currentDay - i;
            if (day < 0) break;
            
            double dayMax = getMaxPriceForDay(day, productName);
            if (dayMax > maxPrice) {
                maxPrice = dayMax;
            }
        }
        
        return maxPrice;
    }

    /**
     * Calculates the total quantity sold for a product over the last N days.
     * 
     * @param productName The name of the product
     * @param days Number of past days to consider
     * @return Total quantity sold, or 0 if no sales found
     */
    public int getTotalQuantitySold(String productName, int days) {
        int currentDay = database.getCurrentDay();
        
        int totalQuantity = 0;
        
        for (int i = 1; i <= days; i++) {
            int day = currentDay - i;
            if (day < 0) break;
            
            totalQuantity += getQuantityForDay(day, productName);
        }
        
        return totalQuantity;
    }

    /**
     * Calculates the total sales volume (revenue) for a product over the last N days.
     * 
     * @param productName The name of the product
     * @param days Number of past days to consider
     * @return Total sales volume, or 0.0 if no sales found
     */
    public double getTotalSalesVolume(String productName, int days) {
        int currentDay = database.getCurrentDay();
        
        double totalVolume = 0.0;
        
        for (int i = 1; i <= days; i++) {
            int day = currentDay - i;
            if (day < 0) break;
            
            totalVolume += getVolumeForDay(day, productName);
        }
        
        return totalVolume;
    }
    
    // ==================== Métodos auxiliares com Cache ====================
    
    /**
     * Obtém a quantidade vendida de um produto num dia específico.
     * Verifica cache primeiro, senão calcula e guarda.
     */
    private int getQuantityForDay(int day, String productName) {
        // 1. Verifica cache
        Integer cached = cache.getQuantidade(day, productName);
        if (cached != null) {
            return cached;
        }
        
        // 2. Cache miss - calcula a partir dos dados do dia
        int productId = database.getProductId(productName);
        Map<Integer, List<Venda>> dayData = database.getDayData(day);
        List<Venda> vendas = dayData.get(productId);
        
        int quantity = 0;
        if (vendas != null) {
            for (Venda v : vendas) {
                quantity += v.getQuantidade();
            }
        }
        
        // 3. Guarda na cache para próximas consultas
        cache.setQuantidade(day, productName, quantity);
        
        return quantity;
    }
    
    /**
     * Obtém o volume de vendas de um produto num dia específico.
     * Verifica cache primeiro, senão calcula e guarda.
     */
    private double getVolumeForDay(int day, String productName) {
        // 1. Verifica cache
        Double cached = cache.getVolume(day, productName);
        if (cached != null) {
            return cached;
        }
        
        // 2. Cache miss - calcula
        int productId = database.getProductId(productName);
        Map<Integer, List<Venda>> dayData = database.getDayData(day);
        List<Venda> vendas = dayData.get(productId);
        
        double volume = 0.0;
        if (vendas != null) {
            for (Venda v : vendas) {
                volume += v.getPreco();
            }
        }
        
        // 3. Guarda na cache
        cache.setVolume(day, productName, volume);
        
        return volume;
    }
    
    /**
     * Obtém o preço máximo unitário de um produto num dia específico.
     * Verifica cache primeiro, senão calcula e guarda.
     */
    private double getMaxPriceForDay(int day, String productName) {
        // 1. Verifica cache
        Double cached = cache.getMaxPrice(day, productName);
        if (cached != null) {
            return cached;
        }
        
        // 2. Cache miss - calcula
        int productId = database.getProductId(productName);
        Map<Integer, List<Venda>> dayData = database.getDayData(day);
        List<Venda> vendas = dayData.get(productId);
        
        double maxPrice = 0.0;
        if (vendas != null) {
            for (Venda v : vendas) {
                double unitPrice = v.getPreco() / v.getQuantidade();
                if (unitPrice > maxPrice) {
                    maxPrice = unitPrice;
                }
            }
        }
        
        // 3. Guarda na cache
        cache.setMaxPrice(day, productName, maxPrice);
        
        return maxPrice;
    }
}
