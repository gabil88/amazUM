package org.Server;

import java.util.concurrent.locks.ReentrantLock;

import org.Utils.RequestType;
import org.Utils.TaggedConnection;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;

/**
 * The main server class that listens for incoming client connections.
 */
public class Server {

    private ServerDatabase database;
    private Handlers handlers;
    private TaskPool taskPool;

    // Configuration Constants
    private static final int MAX_CLIENTS = 2;
    private static final int DEFAULT_PORT = 12345;
    private static final int TASK_POOL_SIZE = 10;

    private final Thread[] workers = new Thread[MAX_CLIENTS]; 
    private final ReentrantLock lock = new ReentrantLock();

    private boolean running = true;
    private ServerSocket serverSocket;

    /**
     * Initializes a Server instance with a fresh database.
     */
    public Server() {
        this.database = new ServerDatabase();
        this.handlers = new Handlers(database);
        this.taskPool = new TaskPool(TASK_POOL_SIZE);
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
     * @param port The port number on which the server will listen for client
     *             connections.
     */
    public void start(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("=== Servidor Iniciado ===");
            System.out.println("Porta: " + port);
            System.out.println("Aguardando conexões...\n");

            while (isRunning()) {
                try {
                    Socket clientSocket = serverSocket.accept();

                    lock.lock();
                    try {
                        int slot = findFreeSlot();
                        TaggedConnection tc = new TaggedConnection(clientSocket);
                        
                        if (slot == -1) {
                            System.out.println("Limite de clientes atingido. Conexão rejeitada.");
                            tc.send(RequestType.Confirmation.getValue(), 
                                   RequestType.Confirmation.getValue(),
                                   new byte[] { 0 });
                            tc.close();
                            clientSocket.close(); 
                        } else {
                            tc.send(RequestType.Confirmation.getValue(), 
                                   RequestType.Confirmation.getValue(),
                                   new byte[] { 1 });

                            ServerWorker worker = new ServerWorker(clientSocket, database, handlers, taskPool);
                            
                            workers[slot] = new Thread(() -> {
                                worker.run();
                                // Cleanup quando termina
                                lock.lock();
                                try {
                                    workers[slot] = null;  
                                } finally {
                                    lock.unlock();
                                }
                            });
                            workers[slot].start();
                        }
                    } finally {
                        lock.unlock();
                    }
                } catch (IOException e) {
                    if (!isRunning()) {
                        System.out.println("Server socket closed, stopping accept loop.");
                        break;
                    }
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
        } finally {
            // Cleanup
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException ignored) {
            }
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
        server.start(DEFAULT_PORT);
        // Quando start() termina (após shutdown), o programa acaba naturalmente
        System.out.println("Server process ending.");
    }

    public boolean isRunning() {
        lock.lock();
        try {
            return running;
        } finally {
            lock.unlock();
        }
    }
}
