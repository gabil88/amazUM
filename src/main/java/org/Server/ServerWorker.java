package org.Server;

import org.TaggedConnection;
import java.net.Socket;
import java.io.*;
import org.utils.RequestType;

/**
 * Class that represents a worker thread that handles communication with a single client.
 * Each client connection has a dedicated ServerWorker that listens for incoming
 * requests, processes them and sends back responses.
 */
class ServerWorker implements Runnable{
    private Socket socket;
    private ServerDatabase database;
    private TaggedConnection taggedConnection;

    /** Initializes a ServerWorker for a specific client connection and the server database.
     * @param socket    The client socket representing the connection. 
     * @param database  The shared server database instance. 
     * 
     * @throws RuntimeException if creating the TaggedConnection fails. 
     */
    public ServerWorker(Socket socket, ServerDatabase database) {
        this.socket = socket;
        this.database = database;
        try {
            this.taggedConnection = new TaggedConnection(socket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Main execution method of the worker thread. 
     * 
     * Listens for client requests in a loop, for each frame received, processes the request.
     * Automatically closes the socket when the client requests a disconnection or in case of exceptions. 
     */
    @Override
    public void run() {
        try {
            System.out.println("✓ Client connected: " + socket.getInetAddress());

            boolean running = true;
            while (running) {
                // 1. Recebe o pacote (bloqueante)
                TaggedConnection.Frame frame = taggedConnection.receive();

                // 2. Identifica o tipo de operação
                RequestType requestType = RequestType.values()[frame.requestType];

                // Streams para ler os argumentos (Input) e escrever a resposta (Output)
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame.data));
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(baos);

                switch (requestType) {
                    case Login:
                        String username = in.readUTF();
                        String password = in.readUTF();
                        boolean userExists = database.authenticate(username,password);
                        out.writeBoolean(userExists);
                        break;
                    case Register:
                        String regUsername = in.readUTF();
                        String regPassword = in.readUTF();
                        boolean registered = database.registerUser(regUsername, regPassword);
                        out.writeBoolean(registered);
                        break;
                    case AddSale:
                        String productName = in.readUTF();
                        int quantity = in.readInt();
                        double price = in.readDouble();
                        boolean saleAdded = database.addSale(productName, quantity, price);
                        out.writeBoolean(saleAdded);
                        break;
                    case EndDay:
                        database.endDay();
                        out.writeBoolean(true); // Confirmation
                        break;

                    case Disconnect:
                        System.out.println("Client: " + socket.getInetAddress() + " is disconnecting ...");
                        out.writeUTF("Disconnect acknowledged");
                        running = false;
                        break;   
                    
                }

                out.flush();
                taggedConnection.send(frame.tag, requestType.getValue(), baos.toByteArray());
            }

        } catch (Exception e) {
            System.out.println("✗ Client disconnected: " + socket.getInetAddress());
        } finally {
            try { 
                socket.close(); 
            } catch (IOException ignored) {}
        }
    }
}
