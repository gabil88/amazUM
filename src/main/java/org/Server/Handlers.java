package org.Server;

import java.util.List;
import java.util.Map;

/**
 * Handles query operations over sales data.
 * Delegates data access to ServerDatabase.
 */
public class Handlers {
    
    private final ServerDatabase database;
    
    public Handlers(ServerDatabase database) {
        this.database = database;
    }
    
    // ==================== Query Operations ====================
    
    /**
     * Calculates the average price per unit for a product over the last N days.
     * 
     * @param productName The name of the product
     * @param days Number of past days to consider
     * @return Average price per unit, or 0.0 if no sales found
     */
    public double getAveragePrice(String productName, int days) {
        int productId = database.getProductId(productName);
        if (productId == -1) {
            return 0.0;
        }

        Map<Integer, List<Venda>> data = database.getNDaysData(days);
        List<Venda> vendas = data.get(productId);
        
        if (vendas == null || vendas.isEmpty()) {
            return 0.0;
        }

        double totalPaid = 0.0;
        int totalQuantity = 0;

        for (Venda v : vendas) {
            totalPaid += v.getPreco();
            totalQuantity += v.getQuantidade();
        }

        return totalQuantity == 0 ? 0.0 : totalPaid / totalQuantity;
    }

    /**
     * Finds the maximum unit price for a product over the last N days.
     * 
     * @param productName The name of the product
     * @param days Number of past days to consider
     * @return Maximum unit price, or 0.0 if no sales found
     */
    public double getMaxPrice(String productName, int days) {
        int productId = database.getProductId(productName);
        if (productId == -1) {
            return 0.0;
        }

        Map<Integer, List<Venda>> data = database.getNDaysData(days);
        List<Venda> vendas = data.get(productId);
        
        if (vendas == null || vendas.isEmpty()) {
            return 0.0;
        }

        double maxPrice = 0.0;
        for (Venda v : vendas) {
            double unitPrice = v.getPreco() / v.getQuantidade();
            if (unitPrice > maxPrice) {
                maxPrice = unitPrice;
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
        int productId = database.getProductId(productName);
        if (productId == -1) {
            return 0;
        }

        Map<Integer, List<Venda>> data = database.getNDaysData(days);
        List<Venda> vendas = data.get(productId);
        
        if (vendas == null || vendas.isEmpty()) {
            return 0;
        }

        int totalQuantity = 0;
        for (Venda v : vendas) {
            totalQuantity += v.getQuantidade();
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
        int productId = database.getProductId(productName);
        if (productId == -1) {
            return 0.0;
        }

        Map<Integer, List<Venda>> data = database.getNDaysData(days);
        List<Venda> vendas = data.get(productId);
        
        if (vendas == null || vendas.isEmpty()) {
            return 0.0;
        }

        double totalVolume = 0.0;
        for (Venda v : vendas) {
            totalVolume += v.getPreco();
        }
        
        return totalVolume;
    }
}
