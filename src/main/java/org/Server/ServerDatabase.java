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
    

    public boolean authenticate(String username, String password){
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
     * Registers an user given its username and password.
     * 
     * @param username The username of the user/client.
     * @param password The password of the user/client.
     */
    public boolean registerUser(String username, String password) {
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
        

    public boolean addSale(String produto, int quantidade, double preco) {
        ordersLock.lock();
        try {
            Venda venda = new Venda(dictionary.get(produto), quantidade, preco);
            ordersActualDay.computeIfAbsent(currentDay, k -> new ArrayList<>()).add(venda);
            return true;
        } finally {
            ordersLock.unlock();
        }
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

