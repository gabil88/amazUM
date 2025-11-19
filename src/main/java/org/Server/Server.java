package org.Server;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.net.Socket;


public class Server {

    private int connectedClients = 0;
    private ServerDatabase database;

    /* Lock for managing the number of active clients */
    private ReentrantLock lock = new ReentrantLock();
    /* Lock for managing client connections */
    private ReentrantLock lockC = new ReentrantLock();
    private Condition allowClientConnection = lockC.newCondition();

    public Server() {
        this.database = new ServerDatabase();
    }
    
    //TEste para já
    public void start(int port) {
        try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(port)) {
            System.out.println("=== Servidor Iniciado ===");
            System.out.println("Porta: " + port);
            System.out.println("Aguardando conexões...\n");
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                
                // Cria worker thread para cada cliente
                ServerWorker worker = new ServerWorker(clientSocket, database);
                new Thread(worker).start();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.start(8080);
    }
}
