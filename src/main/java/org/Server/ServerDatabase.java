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
        Map<Integer, List<Venda>> dataToSave;
        int dayToSave;

        ordersLock.lock();
        try {
            // 1. "Swap" atómico do estado
            // Captura os dados atuais para uma variável local
            dataToSave = this.ordersActualDay;
            dayToSave = this.currentDay;

            // Reseta o estado global para o novo dia
            this.ordersActualDay = new HashMap<>();
            this.currentDay++;
            
            System.out.println("Dia avançado para: " + this.currentDay);

        } finally {
            ordersLock.unlock(); // Liberta o lock imediatamente
        }

        // 2. Operação de I/O pesada feita SEM bloquear os clientes
        // Como 'dataToSave' é uma variável local e a referência global já mudou,
        // nenhuma outra thread vai mexer neste mapa. É seguro.
        if (!dataToSave.isEmpty()) {
            try {
                Files.createDirectories(Paths.get("storage"));
            
                try (ObjectOutputStream oos = new ObjectOutputStream(
                        new FileOutputStream("storage/orders_day_" + dayToSave + ".sales"))) {
                    oos.writeObject(dataToSave);
                }
            } catch (IOException e) {
                System.err.println("Error saving orders for day " + dayToSave + ": " + e.getMessage());
            }
        }
    }
}

