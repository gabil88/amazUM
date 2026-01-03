package org;

import org.Client.ClientStub;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Suite de Testes de Stress e Concorrência Avançada
 * 
 * Execução:
 * mvn compile exec:java -Dexec.mainClass="org.StressTestSuite"
 * 
 * Para testes pesados, use o script run_stress_test.sh
 */
public class StressTestSuite {

    private static final String HOST = "localhost";
    private static final int PORT = 12345;
    
    // Produtos para os testes
    private static final String[] PRODUCTS = {
        "laptop", "mouse", "keyboard", "monitor", "headset",
        "webcam", "speaker", "microphone", "tablet", "phone",
        "charger", "cable", "adapter", "battery", "case"
    };
    
    // Estatísticas globais
    private static final AtomicLong totalOperations = new AtomicLong(0);
    private static final AtomicLong successfulOps = new AtomicLong(0);
    private static final AtomicLong failedOps = new AtomicLong(0);
    private static final AtomicLong totalLatency = new AtomicLong(0);
    private static final ConcurrentHashMap<String, AtomicInteger> errorTypes = new ConcurrentHashMap<>();
    
    // Lock para output thread-safe
    private static final ReentrantLock outputLock = new ReentrantLock();
    
    // Configuração do log
    private static PrintWriter logWriter;
    private static final String LOG_FILE = "stress_test_" + 
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".log";

    public static void main(String[] args) {
        try {
            setupLogging();
            printHeader();
            
            // Parse argumentos se fornecidos
            int numClients = args.length > 0 ? Integer.parseInt(args[0]) : 50;
            int opsPerClient = args.length > 1 ? Integer.parseInt(args[1]) : 100;
            int duration = args.length > 2 ? Integer.parseInt(args[2]) : 60; // segundos
            
            log("Configuração:");
            log("  - Clientes: " + numClients);
            log("  - Operações por cliente: " + opsPerClient);
            log("  - Duração máxima: " + duration + "s");
            log("  - Produtos: " + PRODUCTS.length);
            log("");
            
            // Setup inicial
            setupTestEnvironment(numClients);
            
            // Bateria de Testes
            TestResult r1 = runTest("1. Carga Massiva de Escritas", 
                () -> testMassiveWrites(numClients, opsPerClient));
            
            TestResult r2 = runTest("2. Multiplexagem Extrema (1 Cliente, N Threads)", 
                () -> testExtremeMultiplexing(20, 200));
            
            TestResult r3 = runTest("3. Caos Controlado (Reads/Writes/EndDay)", 
                () -> testControlledChaos(numClients / 2, duration));
            
            TestResult r4 = runTest("4. Race Condition Hunter (Notificações)", 
                () -> testNotificationRaceConditions(10, 50));
            
            TestResult r5 = runTest("5. Stress de Agregações Simultâneas", 
                () -> testAggregationStress(numClients, opsPerClient / 2));
            
            TestResult r6 = runTest("6. Avalanche Test (Conexões Simultâneas)", 
                () -> testConnectionAvalanche(100));
            
            TestResult r7 = runTest("7. Long-Running Stability Test", 
                () -> testLongRunningStability(20, 300)); // 5 min
            
            // Relatório Final
            printFinalReport(Arrays.asList(r1, r2, r3, r4, r5, r6, r7));
            
        } catch (Exception e) {
            log("❌ ERRO FATAL: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (logWriter != null) {
                logWriter.close();
            }
        }
    }

    // =====================================================================
    // TESTE 1: Carga Massiva de Escritas
    // Objetivo: Quebrar o servidor com escritas simultâneas
    // =====================================================================
    private static TestResult testMassiveWrites(int numClients, int opsPerClient) throws Exception {
        long start = System.currentTimeMillis();
        ExecutorService pool = Executors.newFixedThreadPool(numClients);
        CountDownLatch latch = new CountDownLatch(numClients);
        
        AtomicInteger clientErrors = new AtomicInteger(0);
        AtomicInteger writes = new AtomicInteger(0);
        
        log("  Iniciando " + numClients + " clientes...");
        
        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            pool.submit(() -> {
                try (ClientStub client = new ClientStub(HOST, PORT)) {
                    client.authenticate("stress_user" + clientId, "pass");
                    
                    for (int j = 0; j < opsPerClient; j++) {
                        String product = PRODUCTS[ThreadLocalRandom.current().nextInt(PRODUCTS.length)];
                        int quantity = ThreadLocalRandom.current().nextInt(1, 20);
                        double price = ThreadLocalRandom.current().nextDouble(5.0, 500.0);
                        
                        long opStart = System.nanoTime();
                        boolean success = client.addSale(product, quantity, price);
                        long latency = (System.nanoTime() - opStart) / 1_000_000; // ms
                        
                        if (success) {
                            writes.incrementAndGet();
                            successfulOps.incrementAndGet();
                            totalLatency.addAndGet(latency);
                        } else {
                            failedOps.incrementAndGet();
                        }
                        totalOperations.incrementAndGet();
                        
                        // Pequeno delay aleatório para simular comportamento real
                        if (ThreadLocalRandom.current().nextDouble() < 0.1) {
                            Thread.sleep(ThreadLocalRandom.current().nextInt(5, 20));
                        }
                    }
                } catch (Exception e) {
                    clientErrors.incrementAndGet();
                    recordError("MassiveWrites", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);
        
        long duration = System.currentTimeMillis() - start;
        int expected = numClients * opsPerClient;
        double successRate = (writes.get() * 100.0) / expected;
        
        log("  Escritas: " + writes.get() + "/" + expected + " (" + String.format("%.2f%%", successRate) + ")");
        log("  Erros de cliente: " + clientErrors.get());
        log("  Tempo: " + duration + "ms");
        log("  Throughput: " + (writes.get() * 1000.0 / duration) + " ops/s");
        
        // Limpar
        cleanupDay();
        
        return new TestResult("Massive Writes", writes.get() >= expected * 0.95, duration, writes.get(), expected);
    }

    // =====================================================================
    // TESTE 2: Multiplexagem Extrema
    // Objetivo: Verificar se 1 cliente aguenta N threads fazendo operações
    // =====================================================================
    private static TestResult testExtremeMultiplexing(int numThreads, int opsPerThread) throws Exception {
        long start = System.currentTimeMillis();
        ClientStub sharedClient = new ClientStub(HOST, PORT);
        sharedClient.authenticate("stress_user0", "pass");
        
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger errors = new AtomicInteger(0);
        AtomicInteger ops = new AtomicInteger(0);
        
        log("  Lançando " + numThreads + " threads na mesma conexão...");
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            pool.submit(() -> {
                try {
                    for (int j = 0; j < opsPerThread; j++) {
                        // Mix de operações
                        int op = ThreadLocalRandom.current().nextInt(5);
                        String product = PRODUCTS[ThreadLocalRandom.current().nextInt(PRODUCTS.length)];
                        
                        switch (op) {
                            case 0: // Write
                                sharedClient.addSale(product, 1, 10.0);
                                break;
                            case 1: // Quantity
                                sharedClient.getSalesQuantity(product, 7);
                                break;
                            case 2: // Volume
                                sharedClient.getSalesVolume(product, 7);
                                break;
                            case 3: // Average
                                sharedClient.getSalesAveragePrice(product, 7);
                                break;
                            case 4: // Max
                                sharedClient.getSalesMaxPrice(product, 7);
                                break;
                        }
                        ops.incrementAndGet();
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                    recordError("Multiplexing-Thread" + threadId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        boolean completed = latch.await(120, TimeUnit.SECONDS);
        sharedClient.close();
        pool.shutdown();
        
        long duration = System.currentTimeMillis() - start;
        int expected = numThreads * opsPerThread;
        
        log("  Operações: " + ops.get() + "/" + expected);
        log("  Erros: " + errors.get());
        log("  Completou: " + completed);
        
        cleanupDay();
        
        return new TestResult("Extreme Multiplexing", completed && errors.get() == 0, duration, ops.get(), expected);
    }

    // =====================================================================
    // TESTE 3: Caos Controlado
    // Objetivo: Tudo ao mesmo tempo - writes, reads, endDay
    // =====================================================================
    private static TestResult testControlledChaos(int numClients, int durationSeconds) throws Exception {
        long start = System.currentTimeMillis();
        ExecutorService pool = Executors.newFixedThreadPool(numClients + 1);
        AtomicInteger operations = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);
        CountDownLatch stopLatch = new CountDownLatch(1);
        
        log("  Iniciando caos por " + durationSeconds + " segundos...");
        
        // Thread que avança dias periodicamente
        Future<?> endDayTask = pool.submit(() -> {
            try (ClientStub admin = new ClientStub(HOST, PORT)) {
                admin.authenticate("stress_user0", "pass");
                while (stopLatch.getCount() > 0) {
                    Thread.sleep(2000); // EndDay a cada 2s
                    admin.endDay();
                    log("    [EndDay executado]");
                }
            } catch (Exception e) {
                recordError("EndDayThread", e.getMessage());
            }
        });
        
        // Clientes fazendo operações aleatórias
        List<Future<?>> clientTasks = new ArrayList<>();
        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            clientTasks.add(pool.submit(() -> {
                try (ClientStub client = new ClientStub(HOST, PORT)) {
                    client.authenticate("stress_user" + clientId, "pass");
                    
                    while (stopLatch.getCount() > 0) {
                        try {
                            int action = ThreadLocalRandom.current().nextInt(10);
                            String product = PRODUCTS[ThreadLocalRandom.current().nextInt(PRODUCTS.length)];
                            
                            if (action < 5) { // 50% writes
                                client.addSale(product, 
                                    ThreadLocalRandom.current().nextInt(1, 10),
                                    ThreadLocalRandom.current().nextDouble(10, 100));
                            } else if (action < 7) { // 20% quantity
                                client.getSalesQuantity(product, 3);
                            } else if (action < 9) { // 20% volume
                                client.getSalesVolume(product, 3);
                            } else { // 10% avg/max
                                if (ThreadLocalRandom.current().nextBoolean()) {
                                    client.getSalesAveragePrice(product, 3);
                                } else {
                                    client.getSalesMaxPrice(product, 3);
                                }
                            }
                            operations.incrementAndGet();
                            Thread.sleep(ThreadLocalRandom.current().nextInt(10, 50));
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    recordError("ChaosClient" + clientId, e.getMessage());
                }
            }));
        }
        
        // Deixar correr pelo tempo especificado
        Thread.sleep(durationSeconds * 1000L);
        stopLatch.countDown();
        
        // Aguardar conclusão
        endDayTask.get(10, TimeUnit.SECONDS);
        for (Future<?> task : clientTasks) {
            task.get(10, TimeUnit.SECONDS);
        }
        
        pool.shutdown();
        long duration = System.currentTimeMillis() - start;
        
        log("  Operações totais: " + operations.get());
        log("  Erros: " + errors.get());
        log("  Taxa de erro: " + String.format("%.2f%%", (errors.get() * 100.0) / operations.get()));
        
        cleanupDay();
        
        return new TestResult("Controlled Chaos", errors.get() < operations.get() * 0.05, duration, operations.get(), 0);
    }

    // =====================================================================
    // TESTE 4: Race Conditions em Notificações
    // Objetivo: Testar waitForSimultaneousSales e waitForConsecutiveSales
    // =====================================================================
    private static TestResult testNotificationRaceConditions(int numClients, int iterations) throws Exception {
        long start = System.currentTimeMillis();
        AtomicInteger notifications = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);
        
        log("  Testando notificações com " + numClients + " clientes...");
        
        ExecutorService pool = Executors.newFixedThreadPool(numClients * 2);
        List<Future<?>> tasks = new ArrayList<>();
        
        // Metade dos clientes espera por notificações
        for (int i = 0; i < numClients / 2; i++) {
            final int clientId = i;
            tasks.add(pool.submit(() -> {
                try (ClientStub client = new ClientStub(HOST, PORT)) {
                    client.authenticate("stress_user" + clientId, "pass");
                    
                    boolean result = client.waitForSimultaneousSales(PRODUCTS[0], PRODUCTS[1]);
                    if (result) notifications.incrementAndGet();
                } catch (Exception e) {
                    errors.incrementAndGet();
                    recordError("NotifWait" + clientId, e.getMessage());
                }
            }));
        }
        
        // Outra metade faz vendas para disparar notificações
        Thread.sleep(500); // Garantir que waiters estão prontos
        
        for (int i = 0; i < numClients / 2; i++) {
            final int clientId = i + numClients / 2;
            tasks.add(pool.submit(() -> {
                try (ClientStub client = new ClientStub(HOST, PORT)) {
                    client.authenticate("stress_user" + clientId, "pass");
                    
                    for (int j = 0; j < iterations; j++) {
                        client.addSale(PRODUCTS[0], 1, 10.0);
                        Thread.sleep(10);
                        client.addSale(PRODUCTS[1], 1, 10.0);
                        Thread.sleep(ThreadLocalRandom.current().nextInt(20, 50));
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                    recordError("NotifSale" + clientId, e.getMessage());
                }
            }));
        }
        
        // Aguardar
        for (Future<?> task : tasks) {
            task.get(60, TimeUnit.SECONDS);
        }
        
        pool.shutdown();
        long duration = System.currentTimeMillis() - start;
        
        log("  Notificações recebidas: " + notifications.get());
        log("  Erros: " + errors.get());
        
        cleanupDay();
        
        return new TestResult("Notification Race Conditions", errors.get() == 0, duration, notifications.get(), 0);
    }

    // =====================================================================
    // TESTE 5: Stress de Agregações
    // Objetivo: Múltiplas agregações simultâneas sobre mesmos dados
    // =====================================================================
    private static TestResult testAggregationStress(int numClients, int opsPerClient) throws Exception {
        long start = System.currentTimeMillis();
        
        // Primeiro, popular dados
        log("  Populando dados...");
        try (ClientStub setup = new ClientStub(HOST, PORT)) {
            setup.authenticate("stress_user0", "pass");
            for (int i = 0; i < 1000; i++) {
                setup.addSale(PRODUCTS[i % PRODUCTS.length], 
                    ThreadLocalRandom.current().nextInt(1, 20),
                    ThreadLocalRandom.current().nextDouble(10, 100));
            }
        }
        
        // Agora, stress de leitura
        log("  Iniciando stress de agregações...");
        ExecutorService pool = Executors.newFixedThreadPool(numClients);
        CountDownLatch latch = new CountDownLatch(numClients);
        AtomicInteger queries = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);
        
        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            pool.submit(() -> {
                try (ClientStub client = new ClientStub(HOST, PORT)) {
                    client.authenticate("stress_user" + clientId, "pass");
                    
                    for (int j = 0; j < opsPerClient; j++) {
                        String product = PRODUCTS[ThreadLocalRandom.current().nextInt(PRODUCTS.length)];
                        int days = ThreadLocalRandom.current().nextInt(1, 30);
                        
                        // Todas as agregações sobre o mesmo produto
                        client.getSalesQuantity(product, days);
                        client.getSalesVolume(product, days);
                        client.getSalesAveragePrice(product, days);
                        client.getSalesMaxPrice(product, days);
                        
                        queries.addAndGet(4);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                    recordError("AggStress" + clientId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        pool.shutdown();
        long duration = System.currentTimeMillis() - start;
        
        int expected = numClients * opsPerClient * 4;
        log("  Queries: " + queries.get() + "/" + expected);
        log("  Erros: " + errors.get());
        log("  Throughput: " + (queries.get() * 1000.0 / duration) + " queries/s");
        
        cleanupDay();
        
        return new TestResult("Aggregation Stress", errors.get() == 0, duration, queries.get(), expected);
    }

    // =====================================================================
    // TESTE 6: Avalanche de Conexões
    // Objetivo: Abrir/fechar muitas conexões simultaneamente
    // =====================================================================
    private static TestResult testConnectionAvalanche(int numConnections) throws Exception {
        long start = System.currentTimeMillis();
        ExecutorService pool = Executors.newFixedThreadPool(numConnections);
        CountDownLatch latch = new CountDownLatch(numConnections);
        AtomicInteger successful = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);
        
        log("  Abrindo " + numConnections + " conexões simultâneas...");
        
        for (int i = 0; i < numConnections; i++) {
            final int connId = i;
            pool.submit(() -> {
                try (ClientStub client = new ClientStub(HOST, PORT)) {
                    client.authenticate("stress_user" + (connId % 50), "pass");
                    client.addSale("test_product", 1, 1.0);
                    successful.incrementAndGet();
                } catch (Exception e) {
                    errors.incrementAndGet();
                    recordError("Avalanche" + connId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        pool.shutdown();
        long duration = System.currentTimeMillis() - start;
        
        log("  Sucessos: " + successful.get() + "/" + numConnections);
        log("  Erros: " + errors.get());
        log("  Completou: " + completed);
        
        cleanupDay();
        
        return new TestResult("Connection Avalanche", completed && errors.get() < numConnections * 0.1, 
            duration, successful.get(), numConnections);
    }

    // =====================================================================
    // TESTE 7: Estabilidade de Longo Prazo
    // Objetivo: Verificar se há memory leaks ou degradação
    // =====================================================================
    private static TestResult testLongRunningStability(int numClients, int durationSeconds) throws Exception {
        long start = System.currentTimeMillis();
        ExecutorService pool = Executors.newFixedThreadPool(numClients);
        AtomicInteger operations = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);
        CountDownLatch stopLatch = new CountDownLatch(1);
        
        log("  Teste de estabilidade por " + (durationSeconds / 60) + " minutos...");
        log("  (Monitorize uso de memória do servidor externamente)");
        
        List<Future<?>> tasks = new ArrayList<>();
        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            tasks.add(pool.submit(() -> {
                try (ClientStub client = new ClientStub(HOST, PORT)) {
                    client.authenticate("stress_user" + clientId, "pass");
                    
                    while (stopLatch.getCount() > 0) {
                        try {
                            String product = PRODUCTS[ThreadLocalRandom.current().nextInt(PRODUCTS.length)];
                            
                            if (ThreadLocalRandom.current().nextBoolean()) {
                                client.addSale(product, 
                                    ThreadLocalRandom.current().nextInt(1, 5),
                                    ThreadLocalRandom.current().nextDouble(10, 50));
                            } else {
                                client.getSalesQuantity(product, 7);
                            }
                            
                            operations.incrementAndGet();
                            Thread.sleep(ThreadLocalRandom.current().nextInt(100, 500));
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    recordError("LongRun" + clientId, e.getMessage());
                }
            }));
        }
        
        // Relatórios periódicos
        for (int i = 0; i < durationSeconds / 30; i++) {
            Thread.sleep(30000);
            log("    [" + ((i + 1) * 30) + "s] Ops: " + operations.get() + ", Erros: " + errors.get());
        }
        
        stopLatch.countDown();
        for (Future<?> task : tasks) {
            task.get(30, TimeUnit.SECONDS);
        }
        
        pool.shutdown();
        long duration = System.currentTimeMillis() - start;
        
        log("  Operações totais: " + operations.get());
        log("  Erros totais: " + errors.get());
        log("  Taxa de erro: " + String.format("%.3f%%", (errors.get() * 100.0) / operations.get()));
        
        cleanupDay();
        
        return new TestResult("Long-Running Stability", errors.get() < operations.get() * 0.01, 
            duration, operations.get(), 0);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static void setupTestEnvironment(int numUsers) {
        log("⚙️  A preparar ambiente de teste...");
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numUsers);
        
        for (int i = 0; i < numUsers; i++) {
            final int userId = i;
            pool.submit(() -> {
                try (ClientStub client = new ClientStub(HOST, PORT)) {
                    client.register("stress_user" + userId, "pass");
                } catch (Exception e) {
                    // Ignorar se já existe
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
            pool.shutdown();
            log("  ✓ " + numUsers + " utilizadores preparados");
        } catch (InterruptedException e) {
            log("  ✗ Erro ao preparar utilizadores");
        }
    }

    private static void cleanupDay() {
        try (ClientStub admin = new ClientStub(HOST, PORT)) {
            admin.authenticate("stress_user0", "pass");
            admin.endDay();
        } catch (Exception e) {
            // Ignorar
        }
    }

    private static TestResult runTest(String name, Callable<TestResult> test) {
        log("\n" + "=".repeat(70));
        log("🧪 " + name);
        log("=".repeat(70));
        
        try {
            TestResult result = test.call();
            
            if (result.passed) {
                log("\n✅ " + name + ": PASSOU");
            } else {
                log("\n❌ " + name + ": FALHOU");
            }
            
            return result;
        } catch (Exception e) {
            log("\n❌ " + name + ": ERRO - " + e.getMessage());
            e.printStackTrace();
            return new TestResult(name, false, 0, 0, 0);
        }
    }

    private static void printFinalReport(List<TestResult> results) {
        log("\n\n");
        log("╔" + "═".repeat(70) + "╗");
        log("║" + center("RELATÓRIO FINAL DE TESTES", 70) + "║");
        log("╚" + "═".repeat(70) + "╝");
        log("");
        
        int passed = 0;
        int failed = 0;
        
        for (TestResult r : results) {
            String status = r.passed ? "✅ PASSOU" : "❌ FALHOU";
            log(String.format("  %-45s %s", r.name, status));
            log(String.format("      Duração: %dms | Ops: %d/%d", 
                r.duration, r.actualOps, r.expectedOps));
            
            if (r.passed) passed++;
            else failed++;
        }
        
        log("");
        log("═".repeat(70));
        log("Estatísticas Globais:");
        log("  Total de Operações: " + totalOperations.get());
        log("  Operações Bem-Sucedidas: " + successfulOps.get());
        log("  Operações Falhadas: " + failedOps.get());
        log("  Taxa de Sucesso: " + String.format("%.2f%%", 
            (successfulOps.get() * 100.0) / totalOperations.get()));
        
        if (totalOperations.get() > 0) {
            log("  Latência Média: " + (totalLatency.get() / successfulOps.get()) + "ms");
        }
        
        log("");
        log("Resumo: " + passed + " passaram, " + failed + " falharam");
        log("Log completo: " + LOG_FILE);
        log("");
        
        if (!errorTypes.isEmpty()) {
            log("Tipos de Erro Encontrados:");
            errorTypes.forEach((type, count) -> 
                log("  - " + type + ": " + count.get() + " ocorrências"));
        }
        
        System.exit(failed > 0 ? 1 : 0);
    }

    private static void setupLogging() {
        try {
            logWriter = new PrintWriter(new FileWriter(LOG_FILE), true);
        } catch (IOException e) {
            System.err.println("Erro ao criar ficheiro de log: " + e.getMessage());
        }
    }

    private static void log(String message) {
        outputLock.lock();
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            String line = "[" + timestamp + "] " + message;
            System.out.println(line);
            if (logWriter != null) {
                logWriter.println(line);
            }
        } finally {
            outputLock.unlock();
        }
    }

    private static void recordError(String type, String message) {
        errorTypes.computeIfAbsent(type, k -> new AtomicInteger(0)).incrementAndGet();
        log("  ⚠️  [" + type + "] " + message);
    }

    private static String center(String text, int width) {
        int padding = (width - text.length()) / 2;
        return " ".repeat(Math.max(0, padding)) + text + " ".repeat(Math.max(0, width - text.length() - padding));
    }

    private static void printHeader() {
        log("╔" + "═".repeat(70) + "╗");
        log("║" + center("STRESS TEST SUITE - SISTEMA DE VENDAS", 70) + "║");
        log("║" + center("Versão 2.0 - Testes de Concorrência Extrema", 70) + "║");
        log("╚" + "═".repeat(70) + "╝");
        log("");
    }

    // Classe auxiliar para resultados
    static class TestResult {
        String name;
        boolean passed;
        long duration;
        int actualOps;
        int expectedOps;

        TestResult(String name, boolean passed, long duration, int actualOps, int expectedOps) {
            this.name = name;
            this.passed = passed;
            this.duration = duration;
            this.actualOps = actualOps;
            this.expectedOps = expectedOps;
        }
    }
}