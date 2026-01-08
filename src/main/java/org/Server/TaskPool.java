package org.Server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Thread pool para executar tarefas pesadas (agregações, I/O, etc.)
 * Todas as tarefas retornam respostas via handler.
 * 
 * Inclui tratamento robusto de exceções para evitar que falhas em tarefas
 * individuais comprometam as threads da pool.
 */
public class TaskPool {
    private final Thread[] threads;
    private final Queue<Runnable> taskQueue;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private volatile boolean shutdown = false;
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public TaskPool(int poolSize) {
        this.threads = new Thread[poolSize];
        this.taskQueue = new ArrayDeque<>();

        for (int i = 0; i < poolSize; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> workerLoop(threadId), "PoolThread-" + i);
            threads[i].setUncaughtExceptionHandler((t, e) -> {
                logError("Uncaught exception in thread " + t.getName(), e);
            });
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
                logError("Interrupted while waiting for thread shutdown", e);
            }
        }
        System.out.println("TaskPool encerrada.");
    }

    /**
     * Submete uma tarefa para execução assíncrona.
     * 
     * @param task O Callable a executar
     * @param responseHandler O handler a chamar com o resultado (ou null em caso de erro)
     */
    public <T> void submit(Callable<T> task, Consumer<T> responseHandler) {
        lock.lock();
        try {
            if (shutdown) {
                logError("Tentativa de submeter tarefa em TaskPool já encerrada");
                return;
            }
            
            taskQueue.add(() -> {
                T result = null;
                try {
                    result = task.call();
                    responseHandler.accept(result);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logError("Tarefa interrompida");
                } catch (Exception e) {
                    logError("Erro na execução da tarefa: " + e.getClass().getSimpleName(), e);
                    // Tenta notificar o handler com null para indicar erro
                    try {
                        responseHandler.accept(null);
                    } catch (Exception handlerException) {
                        logError("Erro ao chamar responseHandler com null", handlerException);
                    }
                } catch (Throwable t) {
                    // Captura qualquer erro crítico que possa ocorrer
                    logError("Erro crítico na execução da tarefa: " + t.getClass().getSimpleName(), t);
                }
            });
            
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Loop principal de cada thread worker.
     * Protegido contra exceções para garantir que threads não morrem silenciosamente.
     */
    private void workerLoop(int threadId) {
        try {
            while (true) {
                Runnable task = null;
                
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
                    logError("Thread " + threadId + " interrompida");
                    return;
                } finally {
                    lock.unlock();
                }

                if (task != null) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        logError("Thread " + threadId + " encontrou erro ao executar tarefa", e);
                    } catch (Throwable t) {
                        logError("Thread " + threadId + " encontrou erro crítico", t);
                        // Mesmo com erro crítico, a thread continua viva
                    }
                }
            }
        } catch (Throwable t) {
            // Último recurso: log e morte da thread
            logError("Thread " + threadId + " terminando devido a erro fatal", t);
        }
    }
    
    /**
     * Log de erros com timestamp.
     */
    private void logError(String message) {
        System.err.println("[" + LocalDateTime.now().format(TIME_FORMAT) + "] [ERRO] [TaskPool] " + message);
    }
    
    /**
     * Log de erros com exceção.
     */
    private void logError(String message, Throwable e) {
        logError(message + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
    }
}
