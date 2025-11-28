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
    /* Handles message multiplexing/demultiplexing */
    private Demultiplexer demultiplexer;
    /* Ensures thread-safe operations */
    private final ReentrantLock lock = new ReentrantLock();
    /* Unique identifier for each request */
    private int tag = 0;

    public ClientLibrary(String host, int port) throws IOException {
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
        newTag = this.tag++; // Atribui o valor
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
        // Envia um pedido de autenticação com as credenciais
        byte[] requestData;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos)) {
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
    }

    public boolean addSale(String ProductName, int Quantity, double Price) throws IOException {
        byte[] requestData;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(ProductName);
            dos.writeInt(Quantity);
            dos.writeDouble(Price);
            requestData = baos.toByteArray();
        }
        System.out.println("Sending add event request");
        byte[] responseData = sendWithTag(RequestType.AddSale.getValue(), requestData);
        // Lê a resposta
        try (ByteArrayInputStream bais = new ByteArrayInputStream(responseData);
                DataInputStream dis = new DataInputStream(bais)) {
            return dis.readBoolean();
        }
    }


    public void close() throws IOException {
        // 1. Enviar pedido de disconnect
        byte[] disconnect = new byte[0];
        
        // Nota: Como o servidor fecha logo a seguir ao ack, pode haver uma race condition aqui.
        // O 'sendWithTag' espera pela resposta. Se tudo correr bem, recebes o "Disconnect acknowledged".
        try {
            byte[] response = sendWithTag(RequestType.Disconnect.getValue(), disconnect);
            
            try (ByteArrayInputStream bais = new ByteArrayInputStream(response);
                DataInputStream dis = new DataInputStream(bais)) {
                String message = dis.readUTF();
                System.out.println("Server response: " + message);
            }
        } catch (IOException e) {
            // É normal falhar se o servidor fechar muito rápido
            System.out.println("Desconectado.");
        }

        // 2. Fechar o demultiplexer localmente para limpar threads
        demultiplexer.close();
    }

    public String endDay() throws IOException {
        byte[] requestData = new byte[0]; // No data needed
        byte[] responseData = sendWithTag(RequestType.EndDay.getValue(), requestData);
        ByteArrayInputStream bais = new ByteArrayInputStream(responseData);
        DataInputStream dis = new DataInputStream(bais);
        boolean success = dis.readBoolean();
        String message = dis.readUTF();
        return success ? message : "Failed to end day.";
    }
}