package org.Server;

import java.io.IOException;

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
    private final Handlers handlers;
    
    /**
     * Cria um novo ServerSkeleton.
     * 
     * @param database Base de dados do servidor
     * @param handlers Handlers para operações de consulta
     */
    public ServerSkeleton(ServerDatabase database, Handlers handlers) {
        this.database = database;
        this.handlers = handlers;
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
        return handlers.getAveragePrice(productName, days);
    }
    
    @Override
    public int getSalesQuantity(String productName, int days) throws IOException {
        return handlers.getTotalQuantitySold(productName, days);
    }
    
    @Override
    public double getSalesVolume(String productName, int days) throws IOException {
        return handlers.getTotalSalesVolume(productName, days);
    }
    
    @Override
    public double getSalesMaxPrice(String productName, int days) throws IOException {
        return handlers.getMaxPrice(productName, days);
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
}
