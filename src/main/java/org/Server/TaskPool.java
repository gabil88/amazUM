package org.Server;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Thread pool para executar tarefas pesadas (agregações, I/O, etc.)
 * Todas as tarefas retornam respostas via handler.
 */
public class TaskPool {
    private final Thread[] threads;
    private final Queue<Runnable> taskQueue;  // ← Agora é Runnable simples
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private volatile boolean shutdown = false;

    public TaskPool(int poolSize) {
        this.threads = new Thread[poolSize];
        this.taskQueue = new ArrayDeque<>();

        for (int i = 0; i < poolSize; i++) {
            threads[i] = new Thread(this::workerLoop, "PoolThread-" + i);
            threads[i].start();
        }
        System.out.println("TaskPool iniciada com " + poolSize + " threads.");
    }

    public void shutdown() {
        lock.lock();
        try {
            shutdown = true;
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("TaskPool encerrada.");
    }

    public <T> void submit(Callable<T> task, Consumer<T> responseHandler) {
        lock.lock();
        try {
            if (shutdown) {
                throw new IllegalStateException("TaskPool já foi encerrada");
            }
            taskQueue.add(() -> {
                try {
                    T result = task.call();
                    responseHandler.accept(result);
                } catch (Exception e) {
                    System.err.println("Erro na execução da tarefa: " + e.getMessage());
                }
            });
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    private void workerLoop() {
        while (true) {
            Runnable task;  // ← Agora é Runnable
            
            lock.lock();
            try {
                while (taskQueue.isEmpty() && !shutdown) {
                    notEmpty.await();
                }
                
                if (shutdown && taskQueue.isEmpty()) {
                    return;
                }
                
                task = taskQueue.poll();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                lock.unlock();
            }

            if (task != null) {
                task.run();  // ← Só executa
            }
        }
    }

    
}