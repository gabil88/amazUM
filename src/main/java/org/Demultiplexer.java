package org;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Demultiplexer implements AutoCloseable {
    
    private final TaggedConnection conn;
    private Map<Integer, byte[]> responses; // Guarda respostas recebidas
    private Map<Integer, Condition> conditions; // Guarda conditions para threads à espera
    private Lock lock = new ReentrantLock();
    private Thread receiverThread;
    private volatile boolean running = true;

    public Demultiplexer(TaggedConnection conn) {
        this.conn = conn;
        this.responses = new HashMap<>();
        this.conditions = new HashMap<>();
    }

    // Inicia thread que recebe mensagens do servidor continuamente
    public void start() {
        receiverThread = new Thread(() -> {
            try {
                while (running) {
                    // Recebe frame do servidor
                    TaggedConnection.Frame frame = conn.receive();
                    
                    lock.lock();
                    try {
                        // Guarda a resposta
                        responses.put(frame.tag, frame.data);
                        
                        // Se há uma thread à espera desta tag, acorda-a
                        Condition condition = conditions.get(frame.tag);
                        if (condition != null) {
                            condition.signal();
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (IOException e) {
                if (running) { // Só mostra erro se não foi close() intencional
                    e.printStackTrace();
                }
            }
        });
        receiverThread.start();
    }
    
    // Envia pedido ao servidor (não guarda nada)
    public void send(TaggedConnection.Frame frame) throws IOException {
        conn.send(frame);
    }
    
    public void send(int tag, byte[] data) throws IOException { 
        send(new TaggedConnection.Frame(tag, data));
    }

    // Espera por resposta com a tag especificada
    public byte[] receive(int tag) throws InterruptedException {
        lock.lock();
        try {
            // Se já recebemos a resposta, retorna imediatamente
            byte[] data = responses.remove(tag);
            if (data != null) {
                conditions.remove(tag);
                return data;
            }
            
            // Caso contrário, cria condition e espera
            Condition condition = lock.newCondition();
            conditions.put(tag, condition);
            
            // Espera até a thread de receção acordar esta thread
            while (!responses.containsKey(tag)) {
                condition.await();
            }
            
            // Quando acordar, pega a resposta e limpa
            data = responses.remove(tag);
            conditions.remove(tag);
            return data;
            
        } finally {
            lock.unlock();
        }
    }

    public void close() throws IOException {
        running = false;
        conn.close();
        if (receiverThread != null) {
            receiverThread.interrupt();
        }
    }
}