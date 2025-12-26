package org.Client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;

import org.Common.IAmazUM;
import org.Utils.Demultiplexer;
import org.Utils.RequestType;
import org.Utils.TaggedConnection;

/**
 * Stub do cliente - implementa IAmazUM enviando pedidos pela rede.
 * Esta classe abstrai a comunicação com o servidor, serializando os pedidos
 * e enviando-os através de uma conexão TCP.
 */
public class ClientStub implements IAmazUM, AutoCloseable {
    /* Handles message multiplexing/demultiplexing */
    private Demultiplexer demultiplexer;
    /* Ensures thread-safe operations */
    private final ReentrantLock lock = new ReentrantLock();
    /* Unique identifier for each request */
    private int tag = 0;

    public ClientStub(String host, int port) throws IOException {
        Socket socket = new Socket(host, port);
        TaggedConnection taggedConnection = new TaggedConnection(socket); 
        
        // 1. Receber a confirmação PRIMEIRO
        TaggedConnection.Frame frame = taggedConnection.receive();
        
        if (frame.requestType == RequestType.Confirmation.getValue()) {
            boolean accepted = frame.data[0] != 0;
            if (!accepted) {
                System.out.println("Connection rejected: server full.");
                socket.close(); // <--- Correto: Fechar recurso
                throw new IOException("Connection rejected by server.");
            }
        } else {
            System.out.println("Unexpected response from server.");
            socket.close(); // <--- Correto: Fechar recurso
            throw new IOException("Unexpected response from server.");
        }

        // 2. Só agora, que está tudo confirmado, iniciamos o sistema de multiplexagem
        this.demultiplexer = new Demultiplexer(taggedConnection);
        this.demultiplexer.start();

        System.out.println("Connected to the server: " + host + ":" + port);
    }

    /**
     * Sends a request with a specific tag and waits for a response.
     *
     * @param requestType The type of the request.
     * @param requestData The request data.
     * 
     * @return The response data.
     * 
     * @throws IOException if there is an issue sending the request or receiving the response.
     */
    private byte[] sendWithTag(short requestType, byte[] requestData) throws IOException {
        int newTag;

        lock.lock();
        try {
            newTag = this.tag++;
            demultiplexer.send(newTag, requestType, requestData); 
        } finally {
            lock.unlock();
        }

        try {
            return demultiplexer.receive(newTag);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for response", e);
        }
    }

    /**
     * Sends a request and returns a DataInputStream to read the response.
     * Note: The returned stream wraps a byte array, so closing is not strictly necessary,
     * but callers should use try-with-resources for consistency.
     *
     * @param requestType The type of the request.
     * @param requestData The request data.
     * 
     * @return A DataInputStream to read the response.
     * 
     * @throws IOException if there is an issue sending the request or receiving the response.
     */
    private DataInputStream sendRequest(short requestType, byte[] requestData) throws IOException {
        byte[] responseData = sendWithTag(requestType, requestData);
        return new DataInputStream(new ByteArrayInputStream(responseData));
    }
    
    /**
     * Authenticates a user given an username and password.
     *
     * @param username The username of the user.
     * @param password The password of the user.
     * 
     * @return True if the authentication is successful, False otherwise.
     * 
     * @throws IOException if there is an issue during authentication.
     */
    @Override
    public boolean authenticate(String username, String password) throws IOException {
        byte[] requestData;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(username);
            dos.writeUTF(password);
            requestData = baos.toByteArray();
        }
        System.out.println("Sending authentication request");
        try (DataInputStream dis = sendRequest(RequestType.Login.getValue(), requestData)) {
            return dis.readBoolean();
        }
    }

    /**
     * Registers a new user given an username and password.
     *
     * @param username The username of the user.
     * @param password The password of the user.
     * 
     * @return True if the registration is successful, False otherwise.
     * 
     * @throws IOException if there is an issue during registration.
     */
    @Override
    public boolean register(String username, String password) throws IOException {
        byte[] requestData;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(username);
            dos.writeUTF(password);
            requestData = baos.toByteArray();
        }
        try (DataInputStream dis = sendRequest(RequestType.Register.getValue(), requestData)) {
            return dis.readBoolean();
        }
    }

    @Override
    public boolean addSale(String ProductName, int Quantity, double Price) throws IOException {
        byte[] requestData;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(ProductName);
            dos.writeInt(Quantity);
            dos.writeDouble(Price);
            requestData = baos.toByteArray();
        }
        try (DataInputStream dis = sendRequest(RequestType.AddSale.getValue(), requestData)) {
            return dis.readBoolean();
        }
    }


    public void close() throws IOException {
        byte[] disconnect = new byte[0];
        
        try (DataInputStream dis = sendRequest(RequestType.Disconnect.getValue(), disconnect)) {
            String message = dis.readUTF();
            System.out.println("Server response: " + message);
        } catch (IOException e) {
            System.out.println("Desconectado.");
        }

        demultiplexer.close();
    }

    @Override
    public String endDay() throws IOException {
        byte[] requestData = new byte[0];
        try (DataInputStream dis = sendRequest(RequestType.EndDay.getValue(), requestData)) {
            boolean success = dis.readBoolean();
            return success ? "Day ended successfully." : "Failed to end day.";
        }
    }

    /**
     * Gets the average price of a product in the last d days.
     *
     * @param productName The name of the product.
     * @param days Number of past days to aggregate.
     * @return Average price.
     * @throws IOException if there is an issue during the request.
     */
    @Override
    public double getSalesAveragePrice(String productName, int days) throws IOException {
        byte[] requestData;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(productName);
            dos.writeInt(days);
            requestData = baos.toByteArray();
        }
        
        try (DataInputStream dis = sendRequest(RequestType.SalesAveragePrice.getValue(), requestData)) {
            return dis.readDouble();
        }
    }

    /**
     * Gets the total quantity sold of a product in the last d days.
     *
     * @param productName The name of the product.
     * @param days Number of past days to aggregate.
     * @return Total quantity sold.
     * @throws IOException if there is an issue during the request.
     */
    @Override
    public int getSalesQuantity(String productName, int days) throws IOException {
        byte[] requestData;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(productName);
            dos.writeInt(days);
            requestData = baos.toByteArray();
        }
        
        try (DataInputStream dis = sendRequest(RequestType.SalesQuantity.getValue(), requestData)) {
            return dis.readInt();
        }
    }

    /**
     * Gets the total sales volume of a product in the last d days.
     *
     * @param productName The name of the product.
     * @param days Number of past days to aggregate.
     * @return Total sales volume.
     * @throws IOException if there is an issue during the request.
     */
    @Override
    public double getSalesVolume(String productName, int days) throws IOException {
        byte[] requestData;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(productName);
            dos.writeInt(days);
            requestData = baos.toByteArray();
        }
        
        try (DataInputStream dis = sendRequest(RequestType.SalesVolume.getValue(), requestData)) {
            return dis.readDouble();
        }
    }

    /**
     * Gets the maximum price of a product in the last d days.
     *
     * @param productName The name of the product.
     * @param days Number of past days to aggregate.
     * @return Maximum price.
     * @throws IOException if there is an issue during the request.
     */
    @Override
    public double getSalesMaxPrice(String productName, int days) throws IOException {
        byte[] requestData;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(productName);
            dos.writeInt(days);
            requestData = baos.toByteArray();
        }
        
        try (DataInputStream dis = sendRequest(RequestType.SalesMaxPrice.getValue(), requestData)) {
            return dis.readDouble();
        }
    }

    /**
     * Sends a shutdown request to the server.
     * This will save all data and terminate the server.
     *
     * @return A message indicating the shutdown status.
     * @throws IOException if there is an issue during the request.
     */
    @Override
    public String shutdown() throws IOException {
        byte[] requestData = new byte[0];
        try (DataInputStream dis = sendRequest(RequestType.Shutdown.getValue(), requestData)) {
            return dis.readUTF();
        }
    }
}