package org;

import java.io.*;

public class Venda implements Serializable {
    // Para persistência em disco (compacto)
    private int productId;
    private int quantidade;
    private double preco;
    
    public Venda(int productId, int quantidade, double preco) {
        this.productId = productId;
        this.quantidade = quantidade;
        this.preco = preco;
    }
    
    // Serialização PARA DISCO (eficiente - 24 bytes)
    public byte[] toBytesForDisk() throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeInt(productId);
            dos.writeInt(quantidade);
            dos.writeDouble(preco);
            dos.flush();
            return baos.toByteArray();
        }
    }
    
    // Serialização PARA REDE (com nome do produto - maior mas seguro)
    public byte[] toBytesForNetwork(ProductCatalog catalog) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(catalog.getProductName(productId)); // Nome completo
            dos.writeInt(quantidade);
            dos.writeDouble(preco);
            dos.flush();
            return baos.toByteArray();
        }
    }
    
    // Desserialização DA REDE (sem precisar de catálogo sincronizado)
    public static Venda fromBytesNetwork(byte[] data) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream dis = new DataInputStream(bais)) {
            String productName = dis.readUTF();
            int quantidade = dis.readInt();
            double preco = dis.readDouble();
            
            // Cliente recebe o nome, não precisa do ID
            Venda venda = new Venda(0, quantidade, preco); // ID temporário
            return venda;
        }
    }
    
    // Desserialização DO DISCO
    public static Venda fromBytesDisk(byte[] data) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream dis = new DataInputStream(bais)) {
            int productId = dis.readInt();
            int quantidade = dis.readInt();
            double preco = dis.readDouble();
            
            return new Venda(productId, quantidade, preco);
        }
    }
    
    public int getProductId() { return productId; }
    public int getQuantidade() { return quantidade; }
    public double getPreco() { return preco; }
}