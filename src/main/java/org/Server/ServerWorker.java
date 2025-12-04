package org.Server;

import java.net.Socket;

import org.Utils.RequestType;
import org.Utils.TaggedConnection;

import java.io.*;

/**
 * Class that represents a worker thread that handles communication with a
 * single client.
 * Each client connection has a dedicated ServerWorker that listens for incoming
 * requests, processes them and sends back responses.
 */
class ServerWorker implements Runnable {
    private Socket socket;
    private ServerDatabase database;
    private Handlers handlers;
    private TaskPool taskPool;
    private TaggedConnection taggedConnection;
    private volatile boolean running;

    @FunctionalInterface
    private interface ResponseWriter {
        void write(DataOutputStream out) throws IOException;
    }

    /**
     * Initializes a ServerWorker for a specific client connection.
     * 
     * @param socket   The client socket representing the connection.
     * @param database The database for CRUD operations.
     * @param handlers The handlers for analytics/query operations.
     * @param taskPool The shared task pool for heavy operations.
     * @param server   The server instance for shutdown coordination.
     * 
     * @throws RuntimeException if creating the TaggedConnection fails.
     */
    public ServerWorker(Socket socket, ServerDatabase database, Handlers handlers, TaskPool taskPool) {
        this.socket = socket;
        this.database = database;
        this.handlers = handlers;
        this.taskPool = taskPool;
        this.running = true;
        try {
            this.taggedConnection = new TaggedConnection(socket);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create TaggedConnection", e);
        }
    }

    public boolean isRunning() {
        return running;
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
            socket.setSoTimeout(10000);
            System.out.println("✓ Client connected: " + socket.getInetAddress());

            while (running) {
                // 1. Recebe o pacote (bloqueante, mas com timeout)
                TaggedConnection.Frame frame;
                try {
                    frame = taggedConnection.receive();
                } catch (java.net.SocketTimeoutException e) {
                    // Timeout reached, loop back to check running status
                    continue;
                }

                // 2. Identifica o tipo de operação
                RequestType requestType = RequestType.fromValue(frame.requestType);

                if (requestType == null) {
                    System.err.println("Unknown request type: " + frame.requestType);
                    continue;
                }

                // Streams para ler os argumentos (Input)
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame.data));

                switch (requestType) {
                    case Login:
                        String username = in.readUTF();
                        String password = in.readUTF();
                        boolean userExists = database.checkUserCredentials(username, password);
                        sendSyncResponse(frame, requestType, (out) -> out.writeBoolean(userExists));
                        break;
                    case Register:
                        String regUsername = in.readUTF();
                        String regPassword = in.readUTF();
                        boolean registered = database.createUser(regUsername, regPassword);
                        sendSyncResponse(frame, requestType, (out) -> out.writeBoolean(registered));
                        break;
                    /*--Operations that need parallel processing--*/
                    case AddSale:
                        String productName = in.readUTF();
                        int quantity = in.readInt();
                        double price = in.readDouble();
                        taskPool.submit(
                            () -> database.addSaleRecord(productName, quantity, price),
                            (result) -> sendAsyncResponse(frame, requestType, (out) -> out.writeBoolean(result))
                        );
                        break;
                    case SalesAveragePrice:
                        String prodName = in.readUTF();
                        int days = in.readInt();
                        taskPool.submit(
                            () -> handlers.getAveragePrice(prodName, days),
                            (result) -> sendAsyncResponse(frame, requestType, (out) -> out.writeDouble(result))
                        );
                        break;
                    case SalesMaxPrice:
                        String productNameMax = in.readUTF();
                        int daysMax = in.readInt();
                        taskPool.submit(
                            () -> handlers.getMaxPrice(productNameMax, daysMax),
                            (result) -> sendAsyncResponse(frame, requestType, (out) -> out.writeDouble(result))
                        );
                        break;
                    case SalesQuantity:
                        String productNameQty = in.readUTF();
                        int daysQty = in.readInt();
                        taskPool.submit(
                            () -> handlers.getTotalQuantitySold(productNameQty, daysQty),
                            (result) -> sendAsyncResponse(frame, requestType, (out) -> out.writeInt(result))
                        );
                        break;
                    case SalesVolume:
                        String productNameVol = in.readUTF();
                        int daysVol = in.readInt();
                        taskPool.submit(
                            () -> handlers.getTotalSalesVolume(productNameVol, daysVol),
                            (result) -> sendAsyncResponse(frame, requestType, (out) -> out.writeDouble(result))
                        );
                        break;
                    case EndDay:
                        taskPool.submit(
                            () -> database.endDay(),
                            (result) -> sendAsyncResponse(frame, requestType, (out) -> out.writeBoolean(result))
                        );
                        break;
                    /*-----------------------------------------*/
                    case Disconnect:
                        System.out.println("Client: " + socket.getInetAddress() + " is disconnecting ...");
                        sendSyncResponse(frame, requestType, (out) -> out.writeUTF("Disconnect acknowledged"));
                        running = false;
                        break;
                    case Confirmation:
                        // Handled at connection time, should not appear here
                        break;
                    case Shutdown:
                        // NO DO
                        break;
                }
            }

        } catch (Exception e) {
            System.out.println("✗ Client disconnected: " + socket.getInetAddress());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Envia resposta síncrona (bloqueante, usada para Login/Register/Disconnect).
     */
    private void sendSyncResponse(TaggedConnection.Frame frame, RequestType requestType, ResponseWriter writer) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            writer.write(out);
            out.flush();
            taggedConnection.send(frame.tag, requestType.getValue(), baos.toByteArray());
        } catch (IOException e) {
            System.err.println("Failed to send sync response: " + e.getMessage());
        }
    }

    /**
     * Envia resposta assíncrona (chamada pela thread pool após conclusão da tarefa).
     */
    private void sendAsyncResponse(TaggedConnection.Frame frame, RequestType requestType, ResponseWriter writer) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            writer.write(out);
            out.flush();
            taggedConnection.send(frame.tag, requestType.getValue(), baos.toByteArray());
        } catch (IOException e) {
            System.err.println("Failed to send async response: " + e.getMessage());
        }
    }
}
