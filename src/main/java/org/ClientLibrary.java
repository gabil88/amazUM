package org;

import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;

import org.utils.RequestType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ClientLibrary {

    private TaggedConnection taggedConnection;
    private Demultiplexer demultiplexer;
    
    private final ReentrantLock lock = new ReentrantLock();
    
    /* Unique identifier for each request */
    private int tag = 0;



    public ClientLibrary(String host, int port) {
        try{
            Socket socket = new Socket(host, port);
            this.taggedConnection = new TaggedConnection(socket);
            this.demultiplexer = new Demultiplexer(taggedConnection);
            this.demultiplexer.start();
            System.out.println("Conectado ao servidor " + host + ":" + port);
        } catch (Exception e) {
            System.err.println("Erro ao conectar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    
    public boolean authenticate(String username, String password) {
        lock.lock();
        try {
            byte[] credentials;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos)) {
                dos.writeShort(RequestType.Login.getValue());
                dos.writeUTF(username);
                dos.writeUTF(password);
                dos.flush();
                credentials = baos.toByteArray();
            }
            
            // Envia pedido de autenticação
            int currentTag = tag++;
            demultiplexer.send(currentTag, credentials);
            
            // Espera pela resposta
            byte[] response = demultiplexer.receive(currentTag);
            // Lê a resposta
            try (ByteArrayInputStream bais = new ByteArrayInputStream(response);
                DataInputStream dis = new DataInputStream(bais)) {
                return dis.readBoolean();
            }
            
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        } finally {
            lock.unlock();
        } 
    }

}