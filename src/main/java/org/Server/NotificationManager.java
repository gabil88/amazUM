package org.Server;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class NotificationManager {
    private final ReentrantLock lock = new ReentrantLock();

    // Agora as chaves são Sets de Inteiros (IDs)
    private final Map<Set<Integer>, Condition> simultaneousWaiters = new HashMap<>();
    private final Map<Integer, Condition> consecutiveWaiters = new HashMap<>();

    // Estado leve (apenas inteiros)
    private final Set<Integer> soldProductIds = new HashSet<>();
    private int lastSoldId = -1;
    private int currentStreak = 0;
    private int currentDay;

    public NotificationManager(int initialDay) {
        this.currentDay = initialDay;
    }

    // Recebe ID em vez de String
    public void registerSale(int productId) {
        lock.lock();
        try {
            soldProductIds.add(productId);

            if (productId == lastSoldId) {
                currentStreak++;
            } else {
                lastSoldId = productId;
                currentStreak = 1;
            }

            // Verificar Simultâneas (com IDs)
            for (Map.Entry<Set<Integer>, Condition> entry : simultaneousWaiters.entrySet()) {
                if (soldProductIds.containsAll(entry.getKey())) {
                    entry.getValue().signalAll();
                }
            }

            // Verificar Consecutivas
            Condition cond = consecutiveWaiters.get(currentStreak);
            if (cond != null) {
                cond.signalAll();
            }

        } finally {
            lock.unlock();
        }
    }

    public void advanceDay() {
        lock.lock();
        try {
            currentDay++;
            soldProductIds.clear();
            lastSoldId = -1;
            currentStreak = 0;

            for (Condition c : simultaneousWaiters.values()) c.signalAll();
            simultaneousWaiters.clear();

            for (Condition c : consecutiveWaiters.values()) c.signalAll();
            consecutiveWaiters.clear();
        } finally {
            lock.unlock();
        }
    }

    // --- MÉTODOS DE VERIFICAÇÃO RÁPIDA (NON-BLOCKING) ---

    // CORREÇÃO: Recebe int (IDs) em vez de String. A tradução é feita na ServerDatabase.
    public boolean checkSimultaneousSales(int id1, int id2) {
        lock.lock();
        try {
            return soldProductIds.contains(id1) && soldProductIds.contains(id2);
        } finally {
            lock.unlock();
        }
    }

    public int checkConsecutiveSales(int n) {
        lock.lock();
        try {
            if (currentStreak >= n) {
                return lastSoldId;
            }
            return -1;
        } finally {
            lock.unlock();
        }
    }

    // --- MÉTODOS DE ESPERA (BLOCKING) ---
    
    // CORREÇÃO: Recebe int (IDs) em vez de String.
    public boolean waitForSimultaneousSales(int id1, int id2) throws InterruptedException {
        lock.lock();
        try {
            int startDay = this.currentDay;
            // Usamos HashSet para evitar crash do Set.of se id1 == id2
            Set<Integer> pair = new HashSet<>();
            pair.add(id1);
            pair.add(id2);

            if (soldProductIds.containsAll(pair)) return true;

            Condition cond = simultaneousWaiters.computeIfAbsent(pair, k -> lock.newCondition());

            while (!soldProductIds.containsAll(pair) && currentDay == startDay) {
                cond.await();
            }

            return currentDay == startDay;
        } finally {
            lock.unlock();
        }
    }

    public int waitForConsecutiveSales(int n) throws InterruptedException {
        lock.lock();
        try {
            int startDay = this.currentDay;

            if (currentStreak >= n) return lastSoldId;

            Condition cond = consecutiveWaiters.computeIfAbsent(n, k -> lock.newCondition());

            while (currentStreak < n && currentDay == startDay) {
                cond.await();
            }

            if (currentDay != startDay) return -1;

            return lastSoldId;
        } finally {
            lock.unlock();
        }
    }
}
