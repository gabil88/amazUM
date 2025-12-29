package org.Server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class that represents the database of the server, including methods for handling users/clients, products and sales.
 */
class ServerDatabase {
    private int currentDay = 0;

    private final Dictionary dictionary;
    private final PersistenceManager persistence;

    /* Map that stores the actual day's orders */
    private Map<Integer, List<Venda>> ordersCurDay;
    private final ReentrantLock ordersLock = new ReentrantLock();

    /* Map that stores all registered users in the Server */
    private Map<String, String> users;
    private final ReentrantLock usersLock = new ReentrantLock();

    private final NotificationManager notificationManager;
    
    /**
     * Default constructor for the ServerDatabase class.
     * 
     * Initializes the PersistenceManager and loads data from disk.
     */
    public ServerDatabase() {
        this.persistence = new PersistenceManager();
        this.ordersCurDay = new HashMap<>();
        
        // Load persisted data via PersistenceManager
        this.currentDay = persistence.loadCurrentDay();
        this.users = persistence.loadUsers();
        this.dictionary = persistence.loadDictionary();

        this.notificationManager = new NotificationManager(this.currentDay);
        
        // DEBUG
        System.out.println("=== ServerDatabase Loaded ===");
        System.out.println("currentDay: " + this.currentDay);
        System.out.println("users: " + this.users.size());
        System.out.println("dictionary entries: " + this.dictionary);
    }
    
    public int getCurrentDay() {
        ordersLock.lock();
        try {
            return this.currentDay;
        } finally {
            ordersLock.unlock();
        }
    }

    /**
     * Gets the product ID for a given product name.
     * 
     * @param productName The name of the product
     * @return The product ID, or -1 if not found (note: Dictionary auto-creates)
     */
    public int getProductId(String productName) {
        return dictionary.get(productName);
    }

    // Expor metodos do NotificationManager via Database
    
    public boolean checkSimultaneousSales(String p1, String p2) {
        int id1 = dictionary.get(p1);
        int id2 = dictionary.get(p2);
        return notificationManager.checkSimultaneousSales(id1, id2);
    }

    public boolean waitForSimultaneousSales(String p1, String p2) throws InterruptedException {
        int id1 = dictionary.get(p1);
        int id2 = dictionary.get(p2);
        return notificationManager.waitForSimultaneousSales(id1, id2);
    }

    public String checkConsecutiveSales(int n) {
        int id = notificationManager.checkConsecutiveSales(n);
        if (id == -1) return null;
        return dictionary.get(id);
    }

    public String waitForConsecutiveSales(int n) throws InterruptedException {
        int id = notificationManager.waitForConsecutiveSales(n);
        if (id == -1) return null;
        return dictionary.get(id);
    }


    /**
     * Checks if the provided credentials are valid.
     * 
     * @param username The username to check
     * @param password The password to verify
     * @return true if credentials match, false otherwise
     */
    public boolean checkUserCredentials(String username, String password) {
        this.usersLock.lock();
        try {
            String storedPassword = this.users.get(username);
            if (storedPassword == null) {
                return false; // User does not exist
            }
            return storedPassword.equals(password);
        } finally {
            this.usersLock.unlock();
        }
    }


    /**
     * Creates a new user in the database.
     * 
     * @param username The username of the new user
     * @param password The password of the new user
     * @return true if user created, false if username already exists
     */
    public boolean createUser(String username, String password) {
        this.usersLock.lock();
        try {
            if (this.users.containsKey(username)) {
                return false; // User already exists
            }
            users.put(username, password);
            return true;
        } finally {
            this.usersLock.unlock();
        }
    }
        

    /**
     * Adds a sale record to the current day's orders.
     * 
     * @param produto Product name
     * @param quantidade Quantity sold
     * @param preco Total price
     * @return true if sale record added successfully
     */
    public boolean addSaleRecord(String produto, int quantidade, double preco) {
        ordersLock.lock();
        try {
            int id = dictionary.get(produto);

            Venda venda = new Venda(id, quantidade, preco);
            ordersCurDay.computeIfAbsent(id, k -> new ArrayList<>()).add(venda);

            notificationManager.registerSale(id);

            return true;
        } finally {
            ordersLock.unlock();
        }
    }
    
    public boolean endDay() {
        Map<Integer, List<Venda>> dataToSave;
        int dayToSave;
        int newDay;

        ordersLock.lock();
        try {
            // 1. "Swap" atómico do estado
            // Captura os dados atuais para uma variável local
            dataToSave = this.ordersCurDay;
            dayToSave = this.currentDay;

            // Reseta o estado global para o novo dia
            this.ordersCurDay = new HashMap<>();
            this.currentDay++;
            notificationManager.advanceDay();
            newDay = this.currentDay; // Captura o novo valor dentro do lock
            System.out.println("Dia avançado para: " + this.currentDay);

            try {
                persistence.serializeDay(dataToSave, dayToSave);
                persistence.saveCurrentDay(newDay); // Usa a variável local capturada
                persistence.saveDictionary(dictionary);
                return true;
            } catch (IOException e) {
                System.err.println("Erro ao salvar vendas do dia " + dayToSave + ": " + e.getMessage());
                return false;
            }
        } finally {
            ordersLock.unlock(); // Só liberta o lock após persistência
        }
    }


    public int shutdown() {
        ordersLock.lock();
        usersLock.lock();

        try {
            // ----- Secção crítica protegida -----

            persistence.saveCurrentDay(currentDay);
            persistence.saveDictionary(dictionary);

            // Só serializa se houver dados no dia atual
            if (!ordersCurDay.isEmpty()) {
                try {
                    persistence.serializeDay(ordersCurDay, currentDay);
                } catch (IOException e) {
                    System.err.println("Error saving current day orders: " + e.getMessage());
                }
            }

            persistence.saveUsers(users);

            // Acorda todos os clientes à espera de notificações
            notificationManager.shutdown();

            return currentDay;

        } finally {
            // FASE DE ENCOLHIMENTO: libertar locks (ordem inversa)
            usersLock.unlock();
            ordersLock.unlock();
        }
    }


    /**
     * Retrieves data from the last N days, including the current in-memory day.
     * 
     * @param n The number of days to look back.
     * @return A map containing the combined sales data.
     */
    public Map<Integer, List<Venda>> getNDaysData(int n) {
        int currentDaySnapshot;
        ordersLock.lock();
        try {
            currentDaySnapshot = this.currentDay;
        } finally {
            ordersLock.unlock();
        }

        Map<Integer, List<Venda>> combinedData = new HashMap<>();

        // For n=1, only day (currentDay-1) is included.
        // For n=2, days (currentDay-1) and (currentDay-2), etc.
        for (int i = 1; i <= n; i++) {
            int dayToLoad = currentDaySnapshot - i;
            if (dayToLoad < 0)
                break;

            Map<Integer, List<Venda>> pastDayData = persistence.deserializeDay(dayToLoad);

            for (Map.Entry<Integer, List<Venda>> entry : pastDayData.entrySet()) {
                int productId = entry.getKey();
                List<Venda> pastSales = entry.getValue();

                combinedData.merge(productId, pastSales, (existingSales, newSales) -> {
                    existingSales.addAll(newSales);
                    return existingSales;
                });
            }
        }

        return combinedData;
    }

}


