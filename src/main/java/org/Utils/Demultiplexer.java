package org.Utils;

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

// Em org/Demultiplexer.java

    public void start() {
        receiverThread = new Thread(() -> {
            try {
                while (running) {
                    // Tenta ler. Se o socket fechar, lança exceção aqui.
                    TaggedConnection.Frame frame = conn.receive();
                    
                    lock.lock();
                    try {
                        // Ignora mensagens se já estivermos a fechar
                        if (!running) return;

                        responses.put(frame.tag, frame.data);
                        Condition condition = conditions.get(frame.tag);
                        if (condition != null) {
                            condition.signal();
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (IOException e) {
                // Se o erro for EOF, significa que o servidor fechou a conexão.
                // Se 'running' for false, fomos nós que fechámos.
                // Em ambos os casos, saímos silenciosamente.
                if (running && !(e instanceof java.io.EOFException)) {
                    e.printStackTrace();
                }
            } finally {
                // Garante que se a thread morrer, tudo é limpo
                try {
                    close(); 
                } catch (IOException ignored) {}
            }
        });
        receiverThread.start();
    }
    
    // Envia pedido ao servidor (não guarda nada)
    public void send(TaggedConnection.Frame frame) throws IOException {
        conn.send(frame);
    }
    
    public void send(int tag, short requestType, byte[] data) throws IOException { 
        send(new TaggedConnection.Frame(tag, requestType, data));
    }


    public byte[] receive(int tag) throws IOException, InterruptedException {
        lock.lock();
        try {
            // Fast-path: resposta já chegou
            byte[] data = responses.remove(tag);
            if (data != null) return data;
            
            // Regista a espera
            Condition condition = lock.newCondition();
            conditions.put(tag, condition);
            
            try {
                // Ciclo de espera robusto:
                // Continua a dormir SÓ SE não houver resposta E o sistema ainda estiver a correr
                while (!responses.containsKey(tag) && running) {
                    condition.await();
                }
                
                // Se acordou e o sistema fechou (running == false), lança erro
                if (!responses.containsKey(tag) && !running) {
                    throw new IOException("Conexão fechada enquanto aguardava resposta.");
                }
                
                return responses.remove(tag);
                
            } finally {
                // Limpeza garantida
                conditions.remove(tag);
            }
        } finally {
            lock.unlock();
        }
    }


    public void close() throws IOException {
        lock.lock();
        try {
            // 1. Marcar como fechado
            if (!running) return; // Já estava fechado
            running = false;
            
            // 2. Acordar TODAS as threads que estão no 'await()'
            // Isto impede que o cliente fique bloqueado infinitamente
            conditions.forEach((tag, condition) -> condition.signalAll());
            conditions.clear();
            
        } finally {
            lock.unlock();
        }

        // 3. Fechar a conexão física (vai causar exceção na receiverThread)
        conn.close();
        
        // 4. (Opcional) Interromper a thread para ser mais rápido
        if (receiverThread != null && Thread.currentThread() != receiverThread) {
            receiverThread.interrupt();
        }
    }
}