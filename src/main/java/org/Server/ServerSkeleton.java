package org.Server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.Common.FilteredEvents;
import org.Common.IAmazUM;

/**
 * Skeleton do servidor - implementação da interface IAmazUM com a lógica real.
 * 
 * Esta classe centraliza todas as operações do servidor, incluindo
 * ServerDatabase (persistência) e operações de consulta/agregações com Cache.
 * 
 * O ServerWorker utiliza esta classe para processar os pedidos recebidos.
 */
public class ServerSkeleton implements IAmazUM {

    private final ServerDatabase database;
    private final Cache cache;

    /**
     * Cria um novo ServerSkeleton.
     * 
     * @param database Base de dados do servidor
     * @param cache Cache para operações de consulta
     */
    public ServerSkeleton(ServerDatabase database, Cache cache) {
        this.database = database;
        this.cache = cache;
    }

    // ==================== Autenticação ====================

    @Override
    public boolean authenticate(String username, String password) throws IOException {
        return database.checkUserCredentials(username, password);
    }

    @Override
    public boolean register(String username, String password) throws IOException {
        return database.createUser(username, password);
    }

    // ==================== Operações de Venda ====================

    @Override
    public boolean addSale(String productName, int quantity, double price) throws IOException {
        return database.addSaleRecord(productName, quantity, price);
    }

    // ==================== Consultas/Agregações ====================

    @Override
    public double getSalesAveragePrice(String productName, int days) throws IOException {
        return getAveragePrice(productName, days);
    }

    @Override
    public int getSalesQuantity(String productName, int days) throws IOException {
        return getTotalQuantitySold(productName, days);
    }

    @Override
    public double getSalesVolume(String productName, int days) throws IOException {
        return getTotalSalesVolume(productName, days);
    }

    @Override
    public double getSalesMaxPrice(String productName, int days) throws IOException {
        return getMaxPrice(productName, days);
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
    private double getAveragePrice(String productName, int days) {
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
    private double getMaxPrice(String productName, int days) {
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
    private int getTotalQuantitySold(String productName, int days) {
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
    private double getTotalSalesVolume(String productName, int days) {
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

    // ==================== Operações Administrativas ====================

    @Override
    public String endDay() throws IOException {
        boolean success = database.endDay();
        return success ? "Day ended successfully." : "Failed to end day.";
    }

    @Override
    public String shutdown() throws IOException {
        int lastDay = database.shutdown();
        return "Server shutdown. Last day saved: " + lastDay;
    }

    // ==================== Notificações (Novas Funções) ====================

    @Override
    public boolean waitForSimultaneousSales(String p1, String p2) throws IOException, InterruptedException {

        if (database.checkSimultaneousSales(p1, p2)) {
            return true;
        }
        return database.waitForSimultaneousSales(p1, p2);
    }

    @Override
    public String waitForConsecutiveSales(int n) throws IOException, InterruptedException {
        if (database.checkConsecutiveSales(n) != null) {
            return database.checkConsecutiveSales(n);
        }
        return database.waitForConsecutiveSales(n);
    }


    /**
     * Helper method to filter sales events grouped by product ID.
     * 
     * @param productIds    The list of product to filter.
     * @param days          The number of days to look back.
     * 
     * @return The filter sales events map.
     */
    private Map<Integer, List<FilteredEvents.Event>> collectEvents(List<Integer> productIds, int days) {

        int currentDay = database.getCurrentDay();
        Map<Integer, List<FilteredEvents.Event>> eventsByProduct = new HashMap<>();

        for (int pid : productIds) {
            eventsByProduct.put(pid, new ArrayList<>());
        }

        for (int i = 1; i <= days; i++) {
            int day = currentDay - i;
            if (day < 0) break;

            Map<Integer, List<Venda>> dayData = database.getDayData(day);

            for (int pid : productIds) {
                List<Venda> vendas = dayData.get(pid);
                if (vendas != null) {
                    for (Venda v : vendas) {
                        eventsByProduct.get(pid).add(
                            new FilteredEvents.Event(
                                v.getQuantidade(),
                                v.getPreco()
                            )
                        );
                    }
                }
            }
        }

        return eventsByProduct;
    }


    /**
     * Helper method to update the client's personal dictionary
     * This method will check if the client's personal dictionary has the
     * mapping for the product ids collection and update the personal dictionary
     * if necessary.
     * 
     * @param username The username of the client to update.
     * @param productIds The collection of product Ids to update the personal dictionary.
     * 
     * @return The updated personal dictionary of the client.
     */
    private Map<Integer, String> updateDictionary(String username, Collection<Integer> productIds) {

        Map<Integer, String> dictionaryUpdate = new HashMap<>();
        Map<Integer, String> userDict = database.getUserDictionary(username);

        for (Integer pid : productIds) {
            if (!userDict.containsKey(pid)) {
                String productName = database.getProductName(pid);
                dictionaryUpdate.put(pid, productName);
            }
        }

        if (!dictionaryUpdate.isEmpty()) {
            userDict.putAll(dictionaryUpdate);
        }

        return dictionaryUpdate;
    }
   
    /**
     * Builds and returns the FilteredEvents object from a list of product names
     * and the number of days to look back, containing all filtered sales events
     * grouped by product ID.
     *
     * Along with the events, this method also includes a dictionary update
     * containing only the product IDs unknown to the requesting user.
     *
     * @param username  The user requesting the events.
     * @param products  The list of product names to filter.
     * @param days      The number of days to look back.
     * @return          The FilteredEvents object.
     */
    @Override
    public FilteredEvents filterEvents(String username, List<String> products, int days) {

        int currentDay = database.getCurrentDay();
        if (days < 1 || currentDay < 0) {
            return new FilteredEvents(Map.of(), Map.of());
        }

        List<Integer> productIds = new ArrayList<>();
        for (String p : products) {
            productIds.add(database.getProductId(p));
        }

        Map<Integer, List<FilteredEvents.Event>> events = collectEvents(productIds, days);

        Map<Integer, String> dictUpdate = updateDictionary(username, events.keySet());

        return new FilteredEvents(dictUpdate, events);
    }

    @Override
    public void disconnect() throws IOException {
        // Talvez adicionar algo que impeça um user de estar logado em dois terminais?
    }
}