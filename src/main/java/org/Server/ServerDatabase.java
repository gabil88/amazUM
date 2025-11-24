package org.Server;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.ProductCatalog;
import org.Venda;

/**
 * Class that represents the database of the server, including methods for handling users/clients, products and sales.
 */
class ServerDatabase {
    /* */
    ProductCatalog productCatalog;
    /* Map that stores for each day a Map of Sales followed by its ID */
    Map<Integer, Map<Integer, Venda>> ordersByDay;
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
        this.productCatalog = new ProductCatalog();
        this.ordersByDay = new HashMap<>();
        this.users = new HashMap<>();
    }

    /**
     * Gets the product catalog.
     * 
     * @return The product catalog object.
     */
    public ProductCatalog getProductCatalog() {
        return this.productCatalog;
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

    /**
     * Gets the orders by day.
     * 
     * @return The map of orders by day.
     */
    public Map<Integer, Map<Integer, Venda>> getOrdersByDay() {
        return this.ordersByDay;
    }

    /**
     * Inserts an order given the day of the order, its ID and the Sale order object.
     * 
     * @param day       The day where the order was requested
     * @param orderId   The order's ID
     * @param order     The order's sale object.
     */
    public void addOrder(int day, int orderId, Venda order) {
        ordersByDay.putIfAbsent(day, new HashMap<>());
        ordersByDay.get(day).put(orderId, order);
    }

}