package org.Common;

import java.io.IOException;
import java.util.List;

/**
 * Interface que define todas as operações disponíveis no sistema amazUM.
 * 
 * Esta interface é implementada tanto pelo cliente (stub) como pelo servidor (skeleton):
 * - ClientLibrary: implementa enviando pedidos pela rede
 * - ServerSkeleton: implementa executando a lógica real localmente
 */
public interface IAmazUM {
    
    // ==================== Autenticação ====================
    
    /**
     * Autentica um utilizador no sistema.
     * 
     * @param username Nome de utilizador
     * @param password Palavra-passe
     * @return true se autenticação bem-sucedida, false caso contrário
     * @throws IOException se houver erro de comunicação
     */
    boolean authenticate(String username, String password) throws IOException;
    
    /**
     * Regista um novo utilizador no sistema.
     * 
     * @param username Nome de utilizador
     * @param password Palavra-passe
     * @return true se registo bem-sucedido, false caso contrário
     * @throws IOException se houver erro de comunicação
     */
    boolean register(String username, String password) throws IOException;
    
    // ==================== Operações de Venda ====================
    
    /**
     * Adiciona uma nova venda ao sistema.
     * 
     * @param productName Nome do produto
     * @param quantity Quantidade vendida
     * @param price Preço unitário
     * @return true se venda adicionada com sucesso, false caso contrário
     * @throws IOException se houver erro de comunicação
     */
    boolean addSale(String productName, int quantity, double price) throws IOException;
    
    // ==================== Consultas/Agregações ====================
    
    /**
     * Obtém o preço médio de um produto nos últimos N dias.
     * 
     * @param productName Nome do produto
     * @param days Número de dias a considerar
     * @return Preço médio do produto
     * @throws IOException se houver erro de comunicação
     */
    double getSalesAveragePrice(String productName, int days) throws IOException;
    
    /**
     * Obtém a quantidade total vendida de um produto nos últimos N dias.
     * 
     * @param productName Nome do produto
     * @param days Número de dias a considerar
     * @return Quantidade total vendida
     * @throws IOException se houver erro de comunicação
     */
    int getSalesQuantity(String productName, int days) throws IOException;
    
    /**
     * Obtém o volume total de vendas de um produto nos últimos N dias.
     * (volume = quantidade * preço)
     * 
     * @param productName Nome do produto
     * @param days Número de dias a considerar
     * @return Volume total de vendas
     * @throws IOException se houver erro de comunicação
     */
    double getSalesVolume(String productName, int days) throws IOException;
    
    /**
     * Obtém o preço máximo de um produto nos últimos N dias.
     * 
     * @param productName Nome do produto
     * @param days Número de dias a considerar
     * @return Preço máximo registado
     * @throws IOException se houver erro de comunicação
     */
    double getSalesMaxPrice(String productName, int days) throws IOException;
    
    // ==================== Operações Administrativas ====================
    
    /**
     * Termina o dia atual e persiste os dados.
     * 
     * @return Mensagem indicando o resultado da operação
     * @throws IOException se houver erro de comunicação
     */
    String endDay() throws IOException;
    
    /**
     * Desliga o servidor de forma controlada.
     * 
     * @return Mensagem de confirmação
     * @throws IOException se houver erro de comunicação
     */
    String shutdown() throws IOException;

    // ==================== Notificações de Ocorrências ====================

    /**
     * Bloqueia até que dois produtos específicos sejam vendidos no mesmo dia.
     * 
     * @param p1 Nome do primeiro produto
     * @param p2 Nome do segundo produto
     * @return true se ocorreram, false se o dia terminou sem ocorrerem
     * @throws IOException Erro de rede
     * @throws InterruptedException Se a thread for interrompida
     */
    boolean waitForSimultaneousSales(String p1, String p2) throws IOException, InterruptedException;

    /**
     * Bloqueia até que N vendas consecutivas de um mesmo produto ocorram.
     * 
     * @param n Número de vendas consecutivas
     * @return Nome do produto que atingiu a meta, ou null se o dia terminou
     * @throws IOException Erro de rede
     * @throws InterruptedException Se a thread for interrompida
     */
    String waitForConsecutiveSales(int n) throws IOException, InterruptedException;

    /**
     * Filtra eventos de vendas de um conjunto de produtos relativos aos últimos N dias.
     *
     * @param username Nome do cliente.
     * @param products Conjunto de produtos.
     * @param daysAgo Número de dias a considerar.
     * @throws IOException Erro de rede.
     * @return Estrutura serializável compacta com os eventos.
     */
    FilteredEvents filterEvents(String username, List<String> products, int days) throws IOException;

    void disconnect() throws IOException;
}
