package org.Server;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Handles all disk I/O operations for the server.
 * Uses optimized binary serialization with DataInputStream/DataOutputStream.
 * Thread-safe: uses ReadWriteLocks to allow concurrent reads but exclusive writes.
 * 
 * File formats:
 * - orders_day_X.sales: Binary sales data per day
 * - users.dat: Binary user credentials
 * - dictionary.dat: Binary product name-to-ID mapping
 * - currentDay.dat: Single int for current day number
 */
public class PersistenceManager {
    
    private static final String STORAGE_DIR = "storage/";
    private static final String USERS_FILE = STORAGE_DIR + "users.dat";
    private static final String DICTIONARY_FILE = STORAGE_DIR + "dictionary.dat";
    private static final String CURRENT_DAY_FILE = STORAGE_DIR + "currentDay.dat";

    // Locks granulares para cada tipo de ficheiro
    private final ReentrantReadWriteLock usersLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock dictionaryLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock currentDayLock = new ReentrantReadWriteLock();
    
    // Lock por dia - permite escrita/leitura concorrente de dias diferentes
    private final ConcurrentHashMap<Integer, ReentrantReadWriteLock> dayLocks = new ConcurrentHashMap<>();
    
    public PersistenceManager() {
        // Ensure storage directory exists
        new File(STORAGE_DIR).mkdirs();
    }
    
    /**
     * Gets or creates a lock for a specific day file.
     * 
     * @param day The day number
     * @return The ReentrantReadWriteLock for that day
     */
    private ReentrantReadWriteLock getDayLock(int day) {
        return dayLocks.computeIfAbsent(day, k -> new ReentrantReadWriteLock());
    }
    
    // ==================== Day Sales Serialization ====================
    
    /**
     * Serializes a day's sales data to disk using optimized binary format.
     * Thread-safe: acquires write lock for the specific day.
     * 
     * Format:
     * - int: number of products
     * - For each product:
     *   - int: productId
     *   - int: number of sales
     *   - For each sale:
     *     - int: quantidade
     *     - double: preco
     * 
     * @param orders Map of productId to list of sales
     * @param day The day number
     * @throws IOException if writing fails
     */
    public void serializeDay(Map<Integer, List<Venda>> orders, int day) throws IOException {
        ReentrantReadWriteLock lock = getDayLock(day);
        lock.writeLock().lock();
        try {
            String filename = STORAGE_DIR + "orders_day_" + day + ".sales";
            
            try (DataOutputStream dos = new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(filename)))) {
                
                // Write number of products
                dos.writeInt(orders.size());
                
                for (Map.Entry<Integer, List<Venda>> entry : orders.entrySet()) {
                    int productId = entry.getKey();
                    List<Venda> vendas = entry.getValue();
                    
                    // Write product ID
                    dos.writeInt(productId);
                    // Write number of sales for this product
                    dos.writeInt(vendas.size());
                    
                    // Write each sale (inline, no byte array overhead)
                    for (Venda v : vendas) {
                        dos.writeInt(v.getQuantidade());
                        dos.writeDouble(v.getPreco());
                    }
                }
                
                dos.flush();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Deserializes a day's sales data from disk.
     * Thread-safe: acquires read lock for the specific day.
     * 
     * @param day The day number to load
     * @return Map of productId to list of sales, empty map if file doesn't exist
     */
    public Map<Integer, List<Venda>> deserializeDay(int day) {
        ReentrantReadWriteLock lock = getDayLock(day);
        lock.readLock().lock();
        try {
            String filename = STORAGE_DIR + "orders_day_" + day + ".sales";
            File file = new File(filename);
            
            if (!file.exists()) {
                return new HashMap<>();
            }
            
            Map<Integer, List<Venda>> orders = new HashMap<>();
            
            try (DataInputStream dis = new DataInputStream(
                    new BufferedInputStream(new FileInputStream(file)))) {
                
                // Read number of products
                int numProducts = dis.readInt();
                
                for (int i = 0; i < numProducts; i++) {
                    // Read product ID
                    int productId = dis.readInt();
                    // Read number of sales
                    int numSales = dis.readInt();
                    
                    List<Venda> vendas = new ArrayList<>(numSales);
                    
                    // Read each sale
                    for (int j = 0; j < numSales; j++) {
                        int quantidade = dis.readInt();
                        double preco = dis.readDouble();
                        vendas.add(new Venda(productId, quantidade, preco));
                    }
                    
                    orders.put(productId, vendas);
                }
                
            } catch (IOException e) {
                System.err.println("Error reading sales file for day " + day + ": " + e.getMessage());
                return new HashMap<>();
            }
            
            return orders;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ==================== Users Serialization ====================
    
    /**
     * Saves user credentials to disk.
     * Thread-safe: acquires write lock for users file.
     * 
     * Format:
     * - int: number of users
     * - For each user:
     *   - UTF: username
     *   - UTF: password
     * 
     * @param users Map of username to password
     */
    public void saveUsers(Map<String, String> users) {
        usersLock.writeLock().lock();
        try {
            try (DataOutputStream dos = new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(USERS_FILE)))) {
                
                dos.writeInt(users.size());
                
                for (Map.Entry<String, String> entry : users.entrySet()) {
                    dos.writeUTF(entry.getKey());
                    dos.writeUTF(entry.getValue());
                }
                
                dos.flush();
            } catch (IOException e) {
                System.err.println("Error saving users: " + e.getMessage());
            }
        } finally {
            usersLock.writeLock().unlock();
        }
    }
    
    /**
     * Loads user credentials from disk.
     * Thread-safe: acquires read lock for users file.
     * 
     * @return Map of username to password, empty map if file doesn't exist
     */
    public Map<String, String> loadUsers() {
        usersLock.readLock().lock();
        try {
            File file = new File(USERS_FILE);
            Map<String, String> users = new HashMap<>();
            
            if (!file.exists()) {
                return users;
            }
            
            try (DataInputStream dis = new DataInputStream(
                    new BufferedInputStream(new FileInputStream(file)))) {
                
                int numUsers = dis.readInt();
                
                for (int i = 0; i < numUsers; i++) {
                    String username = dis.readUTF();
                    String password = dis.readUTF();
                    users.put(username, password);
                }
                
            } catch (IOException e) {
                System.err.println("Error loading users: " + e.getMessage());
            }
            
            return users;
        } finally {
            usersLock.readLock().unlock();
        }
    }
    
    // ==================== Dictionary Serialization ====================
    
    /**
     * Saves the dictionary to disk using its built-in serialization.
     * Thread-safe: acquires write lock for dictionary file.
     * 
     * @param dictionary The Dictionary to save
     */
    public void saveDictionary(Dictionary dictionary) {
        dictionaryLock.writeLock().lock();
        try {
            try (DataOutputStream dos = new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(DICTIONARY_FILE)))) {
                dictionary.serialize(dos);
                dos.flush();
            } catch (IOException e) {
                System.err.println("Error saving dictionary: " + e.getMessage());
            }
        } finally {
            dictionaryLock.writeLock().unlock();
        }
    }
    
    /**
     * Loads the dictionary from disk.
     * Thread-safe: acquires read lock for dictionary file.
     * 
     * @return The loaded Dictionary, or a new empty one if file doesn't exist
     */
    public Dictionary loadDictionary() {
        dictionaryLock.readLock().lock();
        try {
            File file = new File(DICTIONARY_FILE);
            
            if (!file.exists()) {
                return new Dictionary();
            }
            
            try (DataInputStream dis = new DataInputStream(
                    new BufferedInputStream(new FileInputStream(file)))) {
                return Dictionary.deserialize(dis);
            } catch (IOException e) {
                System.err.println("Error loading dictionary: " + e.getMessage());
                return new Dictionary();
            }
        } finally {
            dictionaryLock.readLock().unlock();
        }
    }
    
    // ==================== Current Day Serialization ====================
    
    /**
     * Saves the current day number to disk.
     * Thread-safe: acquires write lock for currentDay file.
     * 
     * @param currentDay The current day number
     */
    public void saveCurrentDay(int currentDay) {
        currentDayLock.writeLock().lock();
        try {
            try (DataOutputStream dos = new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(CURRENT_DAY_FILE)))) {
                dos.writeInt(currentDay);
                dos.flush();
            } catch (IOException e) {
                System.err.println("Error saving current day: " + e.getMessage());
            }
        } finally {
            currentDayLock.writeLock().unlock();
        }
    }
    
    /**
     * Loads the current day number from disk.
     * Thread-safe: acquires read lock for currentDay file.
     * 
     * @return The current day number, or 0 if file doesn't exist
     */
    public int loadCurrentDay() {
        currentDayLock.readLock().lock();
        try {
            File file = new File(CURRENT_DAY_FILE);
            
            if (!file.exists()) {
                return 0;
            }
            
            try (DataInputStream dis = new DataInputStream(
                    new BufferedInputStream(new FileInputStream(file)))) {
                return dis.readInt();
            } catch (IOException e) {
                System.err.println("Error loading current day: " + e.getMessage());
                return 0;
            }
        } finally {
            currentDayLock.readLock().unlock();
        }
    }
}
