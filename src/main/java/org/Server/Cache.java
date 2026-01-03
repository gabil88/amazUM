package org.Server;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;


/*
* Parece funcional, por favor da proofread, basicamente temos um map que nos leva até a um endereço de memoria através
* de uma key, esse objeto tem presente um prev e next, isso é usado para "ordenar" numa lista duplamente ligada para 
* saber qual node deve ser eliminado mais rapidamente. exemplo tens:
* head <-> A <-> B <-> C <-> tail
* se acederes ao B ele passa a ser o primeiro depois do head:
* head <-> B <-> A <-> C <-> tail
* head e tail existem para conveniencia do codigo.
*/
public class Cache {

    public record CacheKey(int day, String product) {}

    // 1. O NÓ DA LISTA (Guarda os dados e os ponteiros manuais)
    private static class Node {
        final CacheKey key;
        
        // Dados granulares (como pediste antes)
        Integer quantidade;
        Double volume;
        Double maxPrice;

        // Ponteiros para a Lista Duplamente Ligada
        Node prev;
        Node next;

        Node(CacheKey key) { this.key = key; }
    }

    // 2. ESTRUTURAS
    private final int maxCapacity;
    private final Map<CacheKey, Node> map; // Busca rápida
    private final ReentrantLock lock = new ReentrantLock();

    private final Node head; 
    private final Node tail;

    public Cache(int maxCapacity) {
        this.maxCapacity = maxCapacity;
        this.map = new HashMap<>(maxCapacity);

        // Inicializa a lista vazia com sentinelas
        this.head = new Node(null);
        this.tail = new Node(null);
        head.next = tail;
        tail.prev = head;
    }

    // --- MÉTODOS PÚBLICOS (Getters) ---
    // Apenas leem. se existir vai para o inicio

    public Integer getQuantidade(int day, String product) {
        lock.lock();
        try {
            Node node = map.get(new CacheKey(day, product));
            if (node == null) return null;
            
            moveToHead(node); // MANUTENÇÃO MANUAL: Ficou popular ("Hot")
            return node.quantidade;
        } finally {
            lock.unlock();
        }
    }

    public Double getVolume(int day, String product) {
        lock.lock();
        try {
            Node node = map.get(new CacheKey(day, product));
            if (node == null) return null;
            
            moveToHead(node);
            return node.volume;
        } finally {
            lock.unlock();
        }
    }

    public Double getMaxPrice(int day, String product) {
        lock.lock();
        try {
            Node node = map.get(new CacheKey(day, product));
            if (node == null) return null;
            
            moveToHead(node);
            return node.maxPrice;
        } finally {
            lock.unlock();
        }
    }

    // --- MÉTODOS PÚBLICOS (Setters) ---
    // Inserem e gerem o Evict Manualmente

    // Método auxiliar: obtém ou cria nó, gerindo LRU e eviction
    private Node getOrCreateNode(CacheKey key) {
        Node node = map.get(key);
        if (node == null) {
            node = new Node(key);
            map.put(key, node);
            addToHead(node);
            if (map.size() > maxCapacity) removeTail();
        } else {
            moveToHead(node);
        }
        return node;
    }

    public void setQuantidade(int day, String product, int valor) {
        lock.lock();
        try {
            getOrCreateNode(new CacheKey(day, product)).quantidade = valor;
        } finally {
            lock.unlock();
        }
    }

    public void setVolume(int day, String product, double valor) {
        lock.lock();
        try {
            getOrCreateNode(new CacheKey(day, product)).volume = valor;
        } finally {
            lock.unlock();
        }
    }

    public void setMaxPrice(int day, String product, double valor) {
        lock.lock();
        try {
            getOrCreateNode(new CacheKey(day, product)).maxPrice = valor;
        } finally {
            lock.unlock();
        }
    }
    
    // --- GESTÃO LISTA ---

    // Move um nó existente para o início (logo a seguir ao head)
    private void moveToHead(Node node) {
        removeNode(node); // Tira da posição atual
        addToHead(node);  // Mete no início
    }

    // Adiciona fisicamente entre o Head e o primeiro elemento real
    private void addToHead(Node node) {
        node.prev = head;
        node.next = head.next;
        
        head.next.prev = node;
        head.next = node;
    }

    // Remove um nó da lista (ligando o anterior ao seguinte)
    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    // Remove o nó mais antigo (o que está antes do Tail)
    private void removeTail() {
        Node lastRealNode = tail.prev;
        if (lastRealNode == head) return; // Lista vazia

        // 1. Remove da Lista
        removeNode(lastRealNode);
        
        // 2. Remove do Map (Importante!)
        map.remove(lastRealNode.key);
    }
}