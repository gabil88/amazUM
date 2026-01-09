package org.Server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Class that represents the database of the server, including methods for
 * handling users/clients, products and sales.
 * 
 * Mantém os últimos M dias em memória para acesso rápido.
 */
class ServerDatabase {
    // Configuração: quantos dias manter em memória
    private final int MAX_DAYS_IN_MEMORY;

    private int currentDay = 0;

    private final Dictionary dictionary;
    private final PersistenceManager persistence;

    /* Map que guarda os últimos M dias em memória: dia -> (productId -> vendas) */
    private final Map<Integer, Map<Integer, List<Venda>>> daysInMemory;

    /*
     * Map that stores the actual day's orders (dia em curso, ainda não terminado)
     */
    private Map<Integer, List<Venda>> ordersCurDay;
    private final ReentrantLock ordersLock = new ReentrantLock();

    /* Map that stores all registered users in the Server */
    private Map<String, String> users;
    private final ReentrantLock usersLock = new ReentrantLock();

    private final Map<String, Map<Integer, String>> userDictionaries;
    private final ReentrantReadWriteLock userDictLock = new ReentrantReadWriteLock();

    private final NotificationManager notificationManager;

    /**
     * Default constructor for the ServerDatabase class.
     * 
     * Initializes the PersistenceManager and loads data from disk.
     */
    public ServerDatabase(int MAX_DAYS_IN_MEMORY) {
        this.persistence = new PersistenceManager();
        this.ordersCurDay = new HashMap<>();
        this.daysInMemory = new HashMap<>();
        this.userDictionaries = new HashMap<>();

        // Load persisted data via PersistenceManager
        this.currentDay = persistence.loadCurrentDay();
        this.users = persistence.loadUsers();
        this.dictionary = persistence.loadDictionary();

        this.MAX_DAYS_IN_MEMORY = MAX_DAYS_IN_MEMORY;
        // Carrega os últimos M dias para memória
        loadLastDaysToMemory();

        this.notificationManager = new NotificationManager(this.currentDay);

        // DEBUG
        System.out.println("=== ServerDatabase Loaded ===");
        System.out.println("currentDay: " + this.currentDay);
        System.out.println("users: " + this.users.size());
        System.out.println("dictionary entries: " + this.dictionary);
        System.out.println("days in memory: " + this.daysInMemory.keySet());
    }

    /**
     * Carrega os últimos M dias do disco para memória.
     */
    private void loadLastDaysToMemory() {
        for (int i = 1; i <= MAX_DAYS_IN_MEMORY; i++) {
            int dayToLoad = currentDay - i;
            if (dayToLoad < 0)
                break;

            loadDayToMemory(dayToLoad);
        }
    }

    /**
     * Carrega um dia específico do disco para memória.
     * 
     * @param day O dia a carregar
     * @return true se carregou dados, false se o dia estava vazio/não existe
     */
    private boolean loadDayToMemory(int day) {
        Map<Integer, List<Venda>> dayData = persistence.deserializeDay(day);
        if (!dayData.isEmpty()) {
            daysInMemory.put(day, dayData);
            return true;
        }
        return false;
    }

    /**
     * Remove o dia mais antigo da memória.
     */
    private void removeOldestDayFromMemory() {
        if (daysInMemory.isEmpty())
            return;

        int oldestDay = daysInMemory.keySet().stream()
                .min(Integer::compareTo)
                .orElse(-1);

        if (oldestDay >= 0) {
            daysInMemory.remove(oldestDay);
            System.out.println("Removed day " + oldestDay + " from memory");
        }
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

    /**
     * Gets the product name for a given product ID
     * 
     * @param productId The id of the product
     * @return The corresponding product name
     */
    public String getProductName(int productId) {
        return dictionary.get(productId);
    }

    /**
     * Gets the user's personal dictionary.
     * 
     * @param username The username to find the dictionary associated.
     * @return The corresponding personal dictionary
     */
    public Map<Integer, String> getUserDictionary(String username) {
        userDictLock.readLock().lock();
        try {
            Map<Integer, String> dict = userDictionaries.get(username);
            if (dict != null) return dict;
        } finally {
            userDictLock.readLock().unlock();
        }

        userDictLock.writeLock().lock();
        try {
            return userDictionaries.computeIfAbsent(username, u -> new HashMap<>());
        } finally {
            userDictLock.writeLock().unlock();
        }
    }

    /**
     * Updates the personal dictionary of the user safely using writelock.
     * 
     * @param username  The user to update the personal dictionary.
     * @param updates   New entries in the personal dictionary.
     */
    public void updateUserDictionary(String username, Map<Integer, String> updates) {
        userDictLock.writeLock().lock();
        try {
            Map<Integer, String> dict = userDictionaries.computeIfAbsent(username, u -> new HashMap<>());
            dict.putAll(updates);
        } finally {
            userDictLock.writeLock().unlock();
        }
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
        if (id == -1)
            return null;
        return dictionary.get(id);
    }

    public String waitForConsecutiveSales(int n) throws InterruptedException {
        int id = notificationManager.waitForConsecutiveSales(n);
        if (id == -1)
            return null;
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
     * @param produto    Product name
     * @param quantidade Quantity sold
     * @param preco      Total price
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
            dataToSave = this.ordersCurDay;
            dayToSave = this.currentDay;

            // Reseta o estado global para o novo dia
            this.ordersCurDay = new HashMap<>();
            this.currentDay++;
            newDay = this.currentDay;

            // 2. Adiciona o dia terminado à memória
            if (!dataToSave.isEmpty()) {
                daysInMemory.put(dayToSave, dataToSave);
            }

            // 3. Remove o dia mais antigo se excedemos M dias
            if (daysInMemory.size() > MAX_DAYS_IN_MEMORY) {
                removeOldestDayFromMemory();
            }

            // 4. Avança o dia no NotificationManager
            notificationManager.advanceDay();

            System.out.println("Dia avançado para: " + this.currentDay);
            System.out.println("Dias em memória: " + this.daysInMemory.keySet());

        } finally {
            ordersLock.unlock();
        }

        // 5. Operação de I/O pesada feita SEM bloquear os clientes
        try {
            persistence.serializeDay(dataToSave, dayToSave);
            persistence.saveCurrentDay(newDay);
            persistence.saveDictionary(dictionary);
            return true;
        } catch (IOException e) {
            System.err.println("Erro ao salvar vendas do dia " + dayToSave + ": " + e.getMessage());
            return false;
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
     * Retrieves data from the last N days.
     * Usa memória quando disponível, fallback para disco.
     * 
     * @param n The number of days to look back.
     * @return A map containing the combined sales data.
     */
    public Map<Integer, List<Venda>> getNDaysData(int n) {
        Map<Integer, List<Venda>> combinedData = new HashMap<>();

        ordersLock.lock();
        int currentDaySnapshot;
        try {
            currentDaySnapshot = this.currentDay;
        } finally {
            ordersLock.unlock();
        }

        for (int i = 1; i <= n; i++) {
            int dayToLoad = currentDaySnapshot - i;
            if (dayToLoad < 0)
                break;

            // Tenta primeiro da memória, senão vai ao disco
            Map<Integer, List<Venda>> dayData = getDayData(dayToLoad);

            for (Map.Entry<Integer, List<Venda>> entry : dayData.entrySet()) {
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

    /**
     * Obtém dados de um dia específico.
     * Primeiro verifica memória, depois vai ao disco se necessário.
     * 
     * @param day O dia a obter
     * @return Os dados do dia (pode ser vazio)
     */
    public Map<Integer, List<Venda>> getDayData(int day) {
        // 1. Verifica se está em memória
        Map<Integer, List<Venda>> inMemory = daysInMemory.get(day);
        if (inMemory != null) {
            return inMemory;
        }

        // 2. Fallback: carrega do disco
        return persistence.deserializeDay(day);
    }

}
