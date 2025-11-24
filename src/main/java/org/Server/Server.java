package org.Server;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * The main server class that listens for incoming client connections.
 */
public class Server {

    private ServerDatabase database;
    /* 
    private int connectedClients = 0;
     Lock for managing the number of active clients 
    private ReentrantLock lock = new ReentrantLock();
     Lock for managing client connections 
    private ReentrantLock lockC = new ReentrantLock();
    */

    /**
     * Initializes a Server instance with a fresh database.
     */
    public Server() {
        this.database = new ServerDatabase();
    }
    
    /**
     * Starts the Server on the specified port.
     * 
     * The Server class creates a {@link ServerSocket} on a specified port and waits
     * for client connections. 
     * 
     * For each incoming connection, it spawns a new {@link ServerWorker} thread 
     * to handle communication with that client.
     * 
     * @param port The port number on which the server will listen for client connections.
     */
    public void start(int port) {
        try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(port)) {
            System.out.println("=== Servidor Iniciado ===");
            System.out.println("Porta: " + port);
            System.out.println("Aguardando conexões...\n");
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                
                // Cria worker thread para cada cliente
                Thread worker = new Thread(new ServerWorker(clientSocket, database));
                worker.start();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * The main entry point for the server application.
     * Initializes a new Server instance and starts it on port 12345.
     */
    public static void main(String[] args) {
        Server server = new Server();
        server.start(12345);
    }
}
