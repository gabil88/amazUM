package org.Server;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

/**
 * Class that represents the database of the server, including methods for handling users/clients, products and sales.
 */
class ServerDatabase {
    private int currentDay = 0;

    Dictionary dictionary = new Dictionary();

    /* Map that stores the actual day's orders */
    Map<Integer, List<Venda>> ordersActualDay;
    final ReentrantLock ordersLock = new ReentrantLock();

    /* Map that stores all registered users in the Server */
    Map<String, String> users;

    /* Lock to handle client authentication and registration */
    final ReentrantLock usersLock = new ReentrantLock();
    
    /**
     * Default constructor for the ServerDatabase class.
     * 
     * Initializes the Product Catalog object
     * Initializes the orders by day HashMap
     * Initializes the users HashMap
     */
    public ServerDatabase() {
        this.dictionary = new Dictionary();
        this.ordersActualDay = new HashMap<>();
        this.users = new HashMap<>();
    }
    
    /**
     * Gets the map of users/clients.
     * 
     * @return The map of users/clients.
     */
    public Map<String, String> getUsers(){
        return this.users;
    }

    /**
     * Given an username verifies if it already exists in the users map.
     * 
     * @param username The username of the user/client.
     * 
     * @return True if the username already exists, False otherwise.
     */
    public boolean userAlreadyExists(String username){
        return this.users.containsKey(username);
    }

    /**
     * Registers an user given its username and password.
     * 
     * @param username The username of the user/client.
     * @param password The password of the user/client.
     */
    public void registerUser(String username, String password) {
        users.put(username, password);
    }

    public void addVenda(Venda venda) {
        ordersLock.lock();
        try {
            int id = venda.getProductId();
            ordersActualDay.putIfAbsent(id, new ArrayList<>());
            ordersActualDay.get(id).add(venda);
        } finally {
            ordersLock.unlock();
        }
    }
    
    public int getCurrentDay() {
        return currentDay;
    }

    public void endDay() {
        ordersLock.lock();
        try {
            if (!ordersActualDay.isEmpty()) {
                // Garante que a pasta existe
                Files.createDirectories(Paths.get("storage"));
            
                // Serializa as vendas do dia para o ficheiro
                try (ObjectOutputStream oos = new ObjectOutputStream(
                        new FileOutputStream("storage/orders_day_" + currentDay + ".sales"))) {
                    oos.writeObject(ordersActualDay);
                } catch (IOException e) {
                    System.err.println("Error saving orders for day " + currentDay + ": " + e.getMessage());
                }
            }
            ordersActualDay = new HashMap<>();
            currentDay++;
        } catch (IOException e) {
            System.err.println("Error creating storage directory: " + e.getMessage());
        } finally {
            ordersLock.unlock();
        }
    }
}

