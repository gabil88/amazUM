package org.Server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.Common.IAmazUM;
import org.Utils.RequestType;
import org.Utils.TaggedConnection;

/**
 * Class that represents a worker thread that handles communication with a
 * single client.
 * Each client connection has a dedicated ServerWorker that listens for incoming
 * requests, processes them and sends back responses.
 */
class ServerWorker implements Runnable {
    private Server server;
    private Socket socket;
    private IAmazUM skeleton;
    private TaskPool taskPool;
    private TaggedConnection taggedConnection;
    private volatile boolean running;
    private String clientId; // For logging purposes
    private boolean clientAuthenticated;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private interface ResponseWriter {
        void write(DataOutputStream out) throws IOException;
    }

    /**
     * Initializes a ServerWorker for a specific client connection.
     * 
     * @param socket   The client socket representing the connection.
     * @param skeleton The server skeleton implementing IAmazUM interface.
     * @param taskPool The shared task pool for heavy operations.
     * 
     * @throws RuntimeException if creating the TaggedConnection fails.
     */
    public ServerWorker(Socket socket, IAmazUM skeleton, TaskPool taskPool, Server server) {
        this.server = server;
        this.socket = socket;
        this.skeleton = skeleton;
        this.taskPool = taskPool;
        this.running = true;
        this.clientId = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        try {
            this.taggedConnection = new TaggedConnection(socket);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create TaggedConnection for client " + clientId, e);
        }
    }
    
    /**
     * Logs an error message with timestamp and client ID.
     */
    private void logError(String message) {
        System.err.println("[" + LocalDateTime.now().format(TIME_FORMAT) + "] [ERRO] [Client " + clientId + "] " + message);
    }
    
    /**
     * Logs an error with exception details.
     */
    private void logError(String message, Throwable e) {
        logError(message + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
    }
    
    /**
     * Logs an informational message.
     */
    private void logInfo(String message) {
        System.out.println("[" + LocalDateTime.now().format(TIME_FORMAT) + "] [INFO] [Client " + clientId + "] " + message);
    }

    /**
     * Verifies if a client is authenticated to the Server, preventing clients
     * from requesting server operations if they are not authenticated.
     * 
     * @throws IOException If a client is not authenticated and tries to do a request.
     */
    private void requireAuth() throws IOException {
        if (!clientAuthenticated || clientId == null) {
            throw new IOException("Client not authenticated");
        }
    }

    /**
     * Main execution method of the worker thread.
     * 
     * Listens for client requests in a loop, for each frame received, processes the
     * request.
     * Automatically closes the socket when the client requests a disconnection or
     * in case of exceptions.
     */
    @Override
    public void run() {
        try {
            logInfo("Client connected");

            while (running) {
                TaggedConnection.Frame frame = null;
                
                try {
                    // 1. Recebe o pacote (bloqueante, mas com timeout)
                    frame = taggedConnection.receive();
                } catch (SocketTimeoutException e) {
                    // Timeout reached, loop back to check running status
                    continue;
                } catch (SocketException e) {
                    // Client disconnected abruptly
                    logInfo("Client disconnected (connection reset)");
                    break;
                } catch (EOFException e) {
                    // Client closed connection gracefully
                    logInfo("Client closed connection");
                    break;
                } catch (IOException e) {
                    logError("Error receiving frame", e);
                    break;
                }

                // 2. Process the request with comprehensive error handling
                try {
                    processRequest(frame);
                } catch (Exception e) {
                    logError("Unexpected error processing request " + frame.requestType, e);
                    
                    // Try to send an error response to the client
                    try {
                        sendErrorResponse(frame, "Internal server error");
                    } catch (Exception sendError) {
                        logError("Failed to send error response", sendError);
                    }
                }
            }

        } catch (Exception e) {
            logError("Fatal error in worker thread", e);
        } finally {
            cleanup();
        }
    }
    
    /**
     * Processes a single client request.
     */
    private void processRequest(TaggedConnection.Frame frame) throws IOException {
        // 2. Identifica o tipo de operação
        RequestType requestType = RequestType.fromValue(frame.requestType);

        if (requestType == null) {
            logError("Unknown request type: " + frame.requestType);
            return;
        }

        // Streams para ler os argumentos (Input)
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame.data));

        switch (requestType) {
            case Login:
                String username = in.readUTF();
                String password = in.readUTF();
                boolean userExists = skeleton.authenticate(username, password);
                if(userExists)
                    this.clientAuthenticated = true;
                sendResponse(frame, requestType, (out) -> out.writeBoolean(userExists));
                break;
            case Register:
                String regUsername = in.readUTF();
                String regPassword = in.readUTF();
                boolean registered = skeleton.register(regUsername, regPassword);
                if(registered)
                    this.clientAuthenticated = true;
                sendResponse(frame, requestType, (out) -> out.writeBoolean(registered));
                break;
            /*--Operations that need parallel processing--*/
            case AddSale:
                requireAuth();

                String productName = in.readUTF();
                int quantity = in.readInt();
                double price = in.readDouble();
                taskPool.submit(
                    () -> skeleton.addSale(productName, quantity, price),
                    (result) -> sendResponse(frame, requestType, (out) -> out.writeBoolean(result))
                );
                break;
            case SalesAveragePrice:
                requireAuth();

                String prodName = in.readUTF();
                int days = in.readInt();
                taskPool.submit(
                    () -> skeleton.getSalesAveragePrice(prodName, days),
                    (result) -> sendResponse(frame, requestType, (out) -> out.writeDouble(result))
                );
                break;
            case SalesMaxPrice:
                requireAuth();

                String productNameMax = in.readUTF();
                int daysMax = in.readInt();
                taskPool.submit(
                    () -> skeleton.getSalesMaxPrice(productNameMax, daysMax),
                    (result) -> sendResponse(frame, requestType, (out) -> out.writeDouble(result))
                );
                break;
            case SalesQuantity:
                requireAuth();

                String productNameQty = in.readUTF();
                int daysQty = in.readInt();
                taskPool.submit(
                    () -> skeleton.getSalesQuantity(productNameQty, daysQty),
                    (result) -> sendResponse(frame, requestType, (out) -> out.writeInt(result))
                );
                break;
            case SalesVolume:
                requireAuth();

                String productNameVol = in.readUTF();
                int daysVol = in.readInt();
                taskPool.submit(
                    () -> skeleton.getSalesVolume(productNameVol, daysVol),
                    (result) -> sendResponse(frame, requestType, (out) -> out.writeDouble(result))
                );
                break;
            case EndDay:
                requireAuth();

                taskPool.submit(
                    () -> {
                        skeleton.endDay();
                        return true;
                    },
                    (result) -> sendResponse(frame, requestType, (out) -> out.writeBoolean(result))
                );
                break;
            /*-----------------------------------------*/
            case SimultaneousSales:
                requireAuth();

                String p1 = in.readUTF();
                String p2 = in.readUTF();
                new Thread(() -> {
                    try {
                        boolean result = skeleton.waitForSimultaneousSales(p1, p2);
                        sendResponse(frame, requestType, (out) -> out.writeBoolean(result));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logError("Simultaneous sales monitoring interrupted");
                    } catch (Exception e) {
                        logError("Error in simultaneous sales monitoring", e);
                    }
                }).start();
                break;

            case ConsecutiveSales:
                requireAuth();

                int n = in.readInt();
                new Thread(() -> {
                    try {
                        String result = skeleton.waitForConsecutiveSales(n);
                        sendResponse(frame, requestType, (out) -> {
                            if (result != null) {
                                out.writeBoolean(true);
                                out.writeUTF(result);
                            } else {
                                out.writeBoolean(false);
                            }
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logError("Consecutive sales monitoring interrupted");
                    } catch (Exception e) {
                        logError("Error in consecutive sales monitoring", e);
                    }
                }).start();
                break;

            // -------- Filtro de Eventos ----------
            case FilterEvents:
                requireAuth();

                int count = in.readInt();
                List<String> products = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    products.add(in.readUTF());
                }
                int daysAgo = in.readInt();

                taskPool.submit(
                    () -> skeleton.filterEvents(this.clientId, products, daysAgo),
                    (result) -> sendResponse(frame, requestType,
                        (out) -> result.serialize(out))
                );
                break;
                
            case Disconnect:
                logInfo("Client disconnecting");
                sendResponse(frame, requestType, (out) -> out.writeUTF("Disconnect acknowledged"));
                running = false;
                break;
            
            case Confirmation:
                // Handled at connection time, should not appear here
                break;
            
            case Shutdown:
                requireAuth();

                logInfo("Shutdown requested");
                skeleton.shutdown();
                server.close();
                running = false;
                taskPool.shutdown();
                sendResponse(frame, requestType, (out) -> out.writeUTF("Shutdown acknowledged"));
                break;
        }
    }
    
    /**
     * Cleanup resources when worker terminates.
     */
    private void cleanup() {
        logInfo("Cleaning up connection");
        try {
            if (taggedConnection != null) {
                taggedConnection.close();
            }
        } catch (IOException e) {
            logError("Error closing tagged connection", e);
        }
        
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            logError("Error closing socket", e);
        }
    }

    /**
     * Sends an error response to the client.
     */
    private void sendErrorResponse(TaggedConnection.Frame frame, String errorMessage) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeBoolean(false); // Indicate error
            out.writeUTF(errorMessage);
            out.flush();
            taggedConnection.send(frame.tag, frame.requestType, baos.toByteArray());
        } catch (IOException e) {
            logError("Failed to send error response", e);
        }
    }

    /**
     * Envia uma resposta ao cliente.
     * Usada tanto para respostas síncronas (Login/Register/Disconnect) 
     * como assíncronas (chamadas pela thread pool).
     */
    private void sendResponse(TaggedConnection.Frame frame, RequestType requestType, ResponseWriter writer) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            writer.write(out);
            out.flush();
            taggedConnection.send(frame.tag, requestType.getValue(), baos.toByteArray());
        } catch (IOException e) {
            logError("Failed to send response for request type " + requestType, e);
        }
    }
}
