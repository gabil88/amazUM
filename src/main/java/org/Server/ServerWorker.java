package org.Server;

import org.TaggedConnection;
import java.net.Socket;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.utils.RequestType;


class ServerWorker implements Runnable {
    private Socket socket;
    private ServerDatabase database;
    private TaggedConnection taggedConnection;

    public ServerWorker(Socket socket, ServerDatabase database) {
        this.socket = socket;
        this.database = database;
        try {
            this.taggedConnection = new TaggedConnection(socket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try{
            System.out.println("✓ Cliente conectado: " + socket.getInetAddress());
            
            while (true){
                System.out.println("Awaiting client request...");
                // Recebe pedido do cliente
                TaggedConnection.Frame frame = taggedConnection.receive();
                
                // Processa o frame recebido
                ByteArrayInputStream bais = new ByteArrayInputStream(frame.data);
                DataInputStream in = new DataInputStream(bais);

                short requestTypeValue = in.readShort();
                RequestType requestType = RequestType.values()[requestTypeValue];
                
                // Processa o pedido
                byte[] responseData = handleRequest(requestType, in, frame.tag);
                
                // Envia resposta de volta ao cliente com a mesma tag
                taggedConnection.send(frame.tag, responseData);
            }
        } catch (Exception e) {
            System.out.println("✗ Cliente desconectado: " + socket.getInetAddress());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

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
                    out.writeShort(RequestType.Error.getValue());
                    out.writeUTF("Register not implemented yet");
                    break;
                // Adicionar outros casos conforme necessário
                default:
                    // Pedido desconhecido
                    out.writeShort(RequestType.Error.getValue());
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

    //DEFINIR OS MÉTODOS DE CADA REQUEST AQUI

    private boolean authenticate(DataInputStream in, DataOutputStream out) {
        // Lógica de autenticação (exemplo simples)
        try {
            String username = in.readUTF();
            String password = in.readUTF();

            //Adicionar lógica real de autenticação aqui
            boolean isAuthenticated = "admin".equals(username) && "1234".equals(password);
            
            // Escreve resposta
            if (isAuthenticated) {
                out.writeBoolean(true);
                System.out.println("✓ Login bem-sucedido: " + username);
            } else {
                out.writeBoolean(false);
            }
            
            return isAuthenticated;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

}

