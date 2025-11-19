package org;

public class Client {

    //Teste simples de cliente para autenticação
    public static void main(String[] args) {
        try {
            System.out.println("=== Cliente Iniciado ===");
            System.out.println("Conectando ao servidor localhost:8080...\n");
            
            ClientLibrary client = new ClientLibrary("localhost", 8080);
            
            // Pequena pausa para garantir conexão
            Thread.sleep(500);
            
            // Teste 1: Credenciais corretas
            System.out.println("=== Teste 1: Login Correto ===");
            System.out.println("Tentando: admin / 1234");
            boolean result1 = client.authenticate("admin", "1234");
            System.out.println("Resultado: " + (result1 ? "SUCESSO" : "FALHA") + "\n");
            
            // Teste 2: Credenciais incorretas
            System.out.println("=== Teste 2: Login Incorreto ===");
            System.out.println("Tentando: user / wrong");
            boolean result2 = client.authenticate("user", "wrong");
            System.out.println("Resultado: " + (result2 ? "SUCESSO" : "FALHA") + "\n");
            
            System.out.println("=== Testes Concluídos ===");
            
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
