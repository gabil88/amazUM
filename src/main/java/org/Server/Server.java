package org.Server;

import java.util.concurrent.locks.ReentrantLock;
import java.net.ServerSocket;
import java.net.Socket;
import org.TaggedConnection;
import org.utils.RequestType;

/**
 * The main server class that listens for incoming client connections.
 */
public class Server {

    private ServerDatabase database;
    private static final int MAX_CLIENTS = 2;
    private final Thread[] workers = new Thread[MAX_CLIENTS];
    private final ReentrantLock lock = new ReentrantLock();

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
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("=== Servidor Iniciado ===");
            System.out.println("Porta: " + port);
            System.out.println("Aguardando conexões...\n");

            while (true) {
                Socket clientSocket = serverSocket.accept();

                lock.lock();
                try {
                    int slot = findFreeSlot();
                    TaggedConnection tc = new TaggedConnection(clientSocket);
                    if (slot == -1) {
                        System.out.println("Limite de clientes atingido. Conexão rejeitada.");
                        // Send confirmation: rejected
                        tc.send(RequestType.Confirmation.getValue(), RequestType.Confirmation.getValue(), new byte[]{0});
                        tc.close();
                        clientSocket.close();
                    } else {
                        // Send confirmation: accepted
                        tc.send(RequestType.Confirmation.getValue(), RequestType.Confirmation.getValue(), new byte[]{1});
                        workers[slot] = new Thread(() -> {
                            new ServerWorker(clientSocket, database).run();
                        });
                        workers[slot].start();
                    }
                } finally {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int findFreeSlot() {
        for (int i = 0; i < MAX_CLIENTS; i++) {
            if (workers[i] == null || !workers[i].isAlive()) {
                return i;
            }
        }
        return -1;
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
