package org.Server;

import org.TaggedConnection;
import java.net.Socket;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.utils.RequestType;

/**
 * Class that represents a worker thread that handles communication with a single client.
 * Each client connection has a dedicated ServerWorker that listens for incoming
 * requests, processes them and sends back responses.
 */
class ServerWorker implements Runnable {
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

                TaggedConnection.Frame frame = taggedConnection.receive();
                RequestType requestType = RequestType.values()[frame.requestType];

                if(requestType == RequestType.Disconnect) {
                    System.out.println("Client: " + socket.getInetAddress() + " is disconnecting ...");
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataOutputStream out = new DataOutputStream(baos);
                    out.writeUTF("Disconnect acknowledged");
                    out.flush();
                    taggedConnection.send(frame.tag, requestType.getValue(), baos.toByteArray());
                    
                    running = false;
                    break;
                }

                ByteArrayInputStream bais = new ByteArrayInputStream(frame.data);
                DataInputStream in = new DataInputStream(bais);

                byte[] response = handleRequest(requestType, in, frame.tag);

                taggedConnection.send(frame.tag, requestType.getValue(), response);
            }

        } catch (Exception e) {
            System.out.println("✗ Client disconnected: " + socket.getInetAddress());
        } finally {
            try { 
                socket.close(); 
            } catch (IOException ignored) {}
        }
    }
    /** Handles a client request based on its type.
     * 
     * @param requestType   The type of request sent by the client.
     * @param in            The input stream containing any additional request data.
     * @param tag           The tag associated with the request frame for proper response matching.
     * 
     * @return The array of bytes representing the serialized response to be sent to the client.
     */
    public byte[] handleRequest(RequestType requestType, DataInputStream in, int tag) {
        // Lógica para processar o pedido e gerar a resposta
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            
            switch (requestType) {
                case Login:
                    authenticate(in, out);
                    break;
                case Register:
                    // Processar pedido de registo
                    register(in, out);
                    break;
                // to do: remaining cases
                case AddSale:
                    handleAddSale(in, out);
                    break;
                case EndDay:
                    handleEndDay(in, out);
                    break;

                default:
                    // Pedido desconhecido
                    out.writeUTF("Unknown request type");
                    break;
            }
            
            out.flush();
            return baos.toByteArray(); // Retorna os dados serializados
            
        } catch (IOException e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    /**
     * Handles an authentication request from the client.
     * 
     * @param in    The input stream containing username and password.
     * @param out   The output stream to write the authentication result.
     * 
     * @throws IOException If an I/O error occurs during reading or writing.
     */
    private void authenticate(DataInputStream in, DataOutputStream out) throws IOException {
        
        database.usersLock.lock();

        String username = in.readUTF();
        String password = in.readUTF();
        try {
            boolean userExists = database.userAlreadyExists(username);
            if(!userExists){
                out.writeBoolean(false);
            }

            String storedPassword = database.users.get(username);
            if (!storedPassword.equals(password)) {
                out.writeBoolean(false);
            }

            out.writeBoolean(true);
            
        } finally{
            database.usersLock.unlock();
        }
    }

    /**
     * Handles a registration request from the client.
     * 
     * @param in    The input stream containing username and password.
     * @param out   The output stream to write the registration result.
     * 
     * @throws IOException If an I/O error occurs during reading or writing.
     */
    private void register(DataInputStream in, DataOutputStream out) throws IOException {

        database.usersLock.lock();

        String username = in.readUTF();
        String password = in.readUTF();
        
        try {
            boolean userAlreadyExists = database.userAlreadyExists(username);

            if(userAlreadyExists){
                out.writeBoolean(false);
            }
            else{
                database.registerUser(username, password);
                out.writeBoolean(true);
            }
            
        } finally{
            database.usersLock.unlock();
        }

    }

    /**
     * Handles a request to add a sale (Venda) to the database.
     *
     * @param in    The input stream containing product name, quantity, and price.
     * @param out   The output stream to write the result of the operation.
     *
     * @throws IOException If an I/O error occurs during reading or writing.
     */
    private void handleAddSale(DataInputStream in, DataOutputStream out) throws IOException {
        String productName = in.readUTF();
        int quantity = in.readInt();
        double price = in.readDouble();

        Venda venda = new Venda(database.dictionary.get(productName), quantity, price);
        database.addVenda(venda);

        out.writeBoolean(true);
        out.writeUTF("Sale added successfully");
    }

    private void handleEndDay(DataInputStream in, DataOutputStream out) throws IOException {
        database.endDay();
        out.writeBoolean(true); // Confirmation
        out.writeUTF("Day ended. Sales saved to storage.");
    }
}
