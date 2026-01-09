package org.Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.locks.ReentrantLock;

import org.Utils.RequestType;
import org.Utils.TaggedConnection;

/**
 * The main server class that listens for incoming client connections.
 */
public class Server {

    private final ServerDatabase database;
    private final ServerSkeleton skeleton;
    private final TaskPool taskPool;

    // Configuration Constants
    private static final int MAX_CLIENTS = 100;
    private static final int DEFAULT_PORT = 12345;
    private static final int TASK_POOL_SIZE = 70;

    private final Thread[] workers = new Thread[MAX_CLIENTS];
    private final ReentrantLock lock = new ReentrantLock();

    private boolean running = true;
    private ServerSocket serverSocket;

    /**
     * Initializes a Server instance with a fresh database.
     */
    public Server(int daysInMemory, int cacheCapacity) {
        this.database = new ServerDatabase(daysInMemory);
        Cache cache = new Cache(cacheCapacity);
        this.skeleton = new ServerSkeleton(database, cache);
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
            // Define timeout de 1 segundo para aceitar conexões
            serverSocket.setSoTimeout(1000);
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

                            ServerWorker worker = new ServerWorker(clientSocket, skeleton, taskPool, this);

                            workers[slot] = new Thread(() -> {
                                worker.run();
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
                } catch (java.net.SocketTimeoutException ste) {
                    // Timeout: permite re-verificar a flag running
                    continue;
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

        Scanner sc = new Scanner(System.in);
        int daysInMemory = 0;
        int cacheCapacity = 0;

        while (daysInMemory <= 1) {
            System.out.print("Enter the number of days to be kept in memory: ");
            try {
                daysInMemory = Integer.parseInt(sc.nextLine());
                if (daysInMemory <= 1) {
                    System.out.println("Number of days must be greater than 1.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid number of days. Please enter a valid number of days.");
            }
        }
        while (cacheCapacity <= 0) {
            System.out.print("Enter the cache capacity: ");
            try {
                cacheCapacity = Integer.parseInt(sc.nextLine());
                if (cacheCapacity <= 0) {
                    System.out.println("Cache capacity must be greater than 0.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid cache capacity. Please enter a valid number for the cache capacity.");
            }
        }
        sc.close();

        Server server = new Server(daysInMemory, cacheCapacity);
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

    public void close() {
        lock.lock();
        try {
            running = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing server socket: " + e.getMessage());
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
