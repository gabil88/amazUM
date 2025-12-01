package org.Server;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.io.*;
import java.nio.file.*;

/**
 * Class that represents the database of the server, including methods for handling users/clients, products and sales.
 */
class ServerDatabase {
    private int currentDay = 0;

    Dictionary dictionary = new Dictionary();

    /* Map that stores the actual day's orders */
    Map<Integer, List<Venda>> ordersCurDay;
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
        this.ordersCurDay = new HashMap<>();
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
            int id = dictionary.get(produto);
            Venda venda = new Venda(id, quantidade, preco);
            ordersCurDay.computeIfAbsent(id, k -> new ArrayList<>()).add(venda);
            return true;
        } finally {
            ordersLock.unlock();
        }
    }
    
    public boolean endDay() {
        Map<Integer, List<Venda>> dataToSave;
        int dayToSave;

        ordersLock.lock();
        try {
            // 1. "Swap" atómico do estado
            // Captura os dados atuais para uma variável local
            dataToSave = this.ordersCurDay;
            dayToSave = this.currentDay;

            // Reseta o estado global para o novo dia
            this.ordersCurDay = new HashMap<>();
            this.currentDay++;
            
            System.out.println("Dia avançado para: " + this.currentDay);

        } finally {
            ordersLock.unlock(); // Liberta o lock imediatamente
        }

        // 2. Operação de I/O pesada feita SEM bloquear os clientes
        // Como 'dataToSave' é uma variável local e a referência global já mudou,
        // nenhuma outra thread vai mexer neste mapa. É seguro.
        try {
            serializeDay(dataToSave, dayToSave);
            return true;
        } catch (IOException e) {
            System.err.println("Erro ao salvar vendas do dia " + dayToSave + ": " + e.getMessage());
            return false;
        }
    }

    public double getAveragePrice(String productName, int days) {
        int productId = dictionary.get(productName);
        if (productId == -1) {
            return 0.0; // Produto não existe
        }

        double totalPrice = 0.0;
        int totalCount = 0;

        for (int day = currentDay - days; day < currentDay; day++) {
            if (day < 0) continue; // Ignora dias negativos

            Map<Integer, List<Venda>> allData = deserializeDay(day);
            List<Venda> vendas = allData.get(productId);

            if (vendas != null) {
                for (Venda v : vendas) {
                    totalPrice += v.getPreco();
                    totalCount += v.getQuantidade();
                }
            }
        }

        return totalCount == 0 ? 0.0 : totalPrice / totalCount;
    }

    // Em org.Server.ServerDatabase

    public void serializeDay(Map<Integer, List<Venda>> vendas, int dayToSave) throws IOException {
        // Se não há vendas, não cria ficheiro
        if (vendas.isEmpty()) return;

        Files.createDirectories(Paths.get("storage"));

        // Usamos DataOutputStream + BufferedOutputStream para eficiência máxima
        try (DataOutputStream dos = new DataOutputStream(
                new java.io.BufferedOutputStream(new FileOutputStream("storage/orders_day_" + dayToSave + ".sales")))) {

            // 1. Escrevemos quantos produtos distintos existem no total (ajuda na leitura)
            dos.writeInt(vendas.size());

            // 2. Iteramos sobre o mapa
            for (Map.Entry<Integer, List<Venda>> entry : vendas.entrySet()) {
                int productId = entry.getKey();
                List<Venda> listaVendas = entry.getValue();

                // Escreve o ID do produto (Ex: 3)
                dos.writeInt(productId);

                // IMPORTANTE: Escreve o número de vendas para este produto
                // Sem isto, não sabes quando parar de ler as vendas do produto 3!
                dos.writeInt(listaVendas.size());

                // Escreve as vendas seguidas (Qtd, Preço, Qtd, Preço...)
                for (Venda v : listaVendas) {
                    dos.writeInt(v.getQuantidade()); // Ex: 5
                    dos.writeDouble(v.getPreco());   // Ex: 2.5
                }
            }
            
            // No final fica
            // [3] [3] [5][2.5] [7][5.6] [9][9.00]
            // (ID) (Nº) (Venda1) (Venda2) (Venda3)
        }
    }

    public Map<Integer, List<Venda>> deserializeDay(int dayToLoad) {
        String filePath = "storage/orders_day_" + dayToLoad + ".sales";
        Path path = Paths.get(filePath);

        // 1. Verifica se o ficheiro existe antes de tentar abrir
        if (!Files.exists(path)) {
            return new HashMap<>(); // Retorna mapa vazio se o dia não tiver vendas
        }

        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(filePath)))) {

            // 2. Lê o número total de produtos (chaves do mapa)
            int numProducts = dis.readInt();

            // Otimização: Define capacidade inicial para evitar re-hashing
            Map<Integer, List<Venda>> vendasDoDia = new HashMap<>(numProducts);

            // 3. Itera sobre cada produto
            for (int i = 0; i < numProducts; i++) {
                int productId = dis.readInt();
                int numVendas = dis.readInt(); // Quantas vendas este produto teve

                // Cria a lista já com o tamanho certo
                List<Venda> listaVendas = new ArrayList<>(numVendas);

                // 4. Lê todas as vendas desse produto
                for (int j = 0; j < numVendas; j++) {
                    int quantidade = dis.readInt();
                    double preco = dis.readDouble();
                    
                    // Reconstrói o objeto Venda
                    // Nota: O ID é passado aqui porque o construtor de Venda o pede
                    listaVendas.add(new Venda(productId, quantidade, preco));
                }

                // Coloca no mapa
                vendasDoDia.put(productId, listaVendas);
            }

            return vendasDoDia;

        } catch (IOException e) {
            System.err.println("Erro ao ler vendas do dia " + dayToLoad + ": " + e.getMessage());
            return new HashMap<>(); // Em caso de erro, retorna vazio para não crashar o servidor
        }
    }

    /**
     * Populates the database with test data for testing purposes.
     * Creates sample users, products, and sales across multiple days.
     */
    public void populateTestData() {
        System.out.println("=== Populating test data ===");

        // 1. Add test users
        registerUser("admin", "admin123");
        registerUser("user1", "pass1");
        registerUser("user2", "pass2");
        System.out.println("✓ Added 3 test users");

        // 2. Define test products
        String[] products = {"banana", "apple", "orange", "milk", "bread", "cheese", "water", "juice"};

        // 3. Simulate multiple days of sales
        int numDays = 5;
        
        for (int day = 0; day < numDays; day++) {
            System.out.println("Generating sales for day " + day + "...");
            
            // Add random sales for each day
            for (String product : products) {
                // Random number of sales per product (1-5)
                int numSales = (int) (Math.random() * 5) + 1;
                
                for (int i = 0; i < numSales; i++) {
                    int quantity = (int) (Math.random() * 10) + 1;  // 1-10 units
                    double price = Math.round((Math.random() * 10 + 0.5) * 100.0) / 100.0; // 0.50 - 10.50
                    addSale(product, quantity, price);
                }
            }
            
            // End day to persist sales (except for the last day - keep some in memory)
            if (day < numDays - 1) {
                endDay();
            }
        }

        System.out.println("=== Test data population complete ===");
        System.out.println("Current day: " + currentDay);
        System.out.println("Products in dictionary: " + products.length);
        System.out.println("Users registered: 3");
        System.out.println("Days with historical data: " + (numDays - 1));
        System.out.println("Current day has sales in memory: yes");
    }

}

