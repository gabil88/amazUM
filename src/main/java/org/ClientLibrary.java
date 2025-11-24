package org;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;

import org.utils.RequestType;

/**
 * /**
 * The ClientLibrary class provides methods for communication between the client and the server.
 * Provides methods responsible for authentication, (...)
 */
public class ClientLibrary {
    /* Handles tagged communication with the server */
    private TaggedConnection taggedConnection;
    /* Handles message multiplexing/demultiplexing */
    private Demultiplexer demultiplexer;
    /* Ensures thread-safe operations */
    private final ReentrantLock lock = new ReentrantLock();
    /* Unique identifier for each request */
    private int tag = 0;

    /**
     * Constructor to initialize the Client Library given server's host and port.
     * 
     * @param host The server hostname.
     * @param port The server port.
     */
    public ClientLibrary(String host, int port) {
        try{
            Socket socket = new Socket(host, port);
            this.taggedConnection = new TaggedConnection(socket);
            this.demultiplexer = new Demultiplexer(taggedConnection);
            this.demultiplexer.start();
            System.out.println("Connected to the server: " + host + ":" + port);
        } catch (Exception e) {
            System.err.println("Error Connecting: " + e.getMessage());
            e.printStackTrace();
        }
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
        lock.lock();
        try {
            TaggedConnection.Frame frame = new TaggedConnection.Frame(this.tag, requestType, requestData);
            this.tag++;
            taggedConnection.send(frame.tag, requestType, requestData);
            
            try {
                return demultiplexer.receive(frame.tag);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for response", e);
            }
        } finally {
            lock.unlock();
        }
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
    public boolean authenticate(String username, String password) throws IOException {
        lock.lock();
        try {
            // Envia um pedido de autenticação com as credenciais
            byte[] requestData;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos)) {
                //dos.writeShort(RequestType.Login.getValue()); <- Não faz sentido estar dentro do data, porque o requestType já se encontra no header
                dos.writeUTF(username);
                dos.writeUTF(password);
                requestData = baos.toByteArray();
            }
            System.out.println("Sending authentication request");
            byte[] responseData = sendWithTag(RequestType.Login.getValue(), requestData);
            // Lê a resposta
            try (ByteArrayInputStream bais = new ByteArrayInputStream(responseData);
                 DataInputStream dis = new DataInputStream(bais)) {
                return dis.readBoolean();
            }
        } finally {
            lock.unlock();
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
    public boolean register(String username, String password) throws IOException {
        lock.lock();
        try {
            byte[] requestData;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos)) {
                //dos.writeShort(RequestType.Register.getValue()); <- Não faz sentido estar dentro do data, porque o requestType já se encontra no header
                dos.writeUTF(username);
                dos.writeUTF(password);
                requestData = baos.toByteArray();
            }
            System.out.println("Sending registration request");
            byte[] responseData = sendWithTag(RequestType.Register.getValue(), requestData);
            // Lê a resposta
            try (ByteArrayInputStream bais = new ByteArrayInputStream(responseData);
                 DataInputStream dis = new DataInputStream(bais)) {
                return dis.readBoolean();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Closes the connection with the server.
     *
     * @throws IOException if there is an issue closing the connection.
     */
    public void close() throws IOException {
        lock.lock();
        try {
            sendDisconnectMessage();
            demultiplexer.close();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sends a disconnect message to the server and waits for its response.
     *
     * @throws IOException if there is an issue sending the disconnect message.
     */
    public void sendDisconnectMessage() throws IOException {
        lock.lock();
        try {
            byte[] disconnect = new byte[0];

            byte[] response = sendWithTag(RequestType.Disconnect.getValue(), disconnect);
            // Read ack message from server
            try (ByteArrayInputStream bais = new ByteArrayInputStream(response);
                DataInputStream dis = new DataInputStream(bais)) {
                String message = dis.readUTF();
                System.out.println("Server response: " + message);
            }

        } finally {
            lock.unlock();
        }
    }

}