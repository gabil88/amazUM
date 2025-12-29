package org.Server;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.Common.IAmazUM;

/**
 * Skeleton do servidor - implementação da interface IAmazUM com a lógica real.
 * 
 * Esta classe centraliza todas as operações do servidor, delegando para
 * ServerDatabase (persistência) e Handlers (queries/agregações).
 * 
 * O ServerWorker utiliza esta classe para processar os pedidos recebidos.
 */
public class ServerSkeleton implements IAmazUM {
    
    private final ServerDatabase database;
    
    /**
     * Cria um novo ServerSkeleton.
     * 
     * @param database Base de dados do servidor
     * @param handlers Handlers para operações de consulta
     */
    public ServerSkeleton(ServerDatabase database) {
        this.database = database;
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
    
    @Override
    public int getSalesQuantity(String productName, int days) throws IOException {
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
    
    @Override
    public double getSalesVolume(String productName, int days) throws IOException {
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
    
    @Override
    public double getSalesMaxPrice(String productName, int days) throws IOException {
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
    
        if(database.checkSimultaneousSales(p1, p2)){
            return true;
        }
        return database.waitForSimultaneousSales(p1, p2);
    }

    @Override
    public String waitForConsecutiveSales(int n) throws IOException, InterruptedException {
        if(database.checkConsecutiveSales(n) != null){
            return database.checkConsecutiveSales(n);
        }
        return database.waitForConsecutiveSales(n);
    }

    @Override
    public void disconnect() throws IOException {
        // Talvez adicionar algo que impeça um user de estar logado em dois terminais?
    }
}
