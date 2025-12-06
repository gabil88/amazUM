package org;

import org.Client.ClientLibrary;


import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Suite de testes de concorrência robusta para o servidor amazUM.
 * * Executar com o servidor já ligado:
 * mvn compile exec:java -Dexec.mainClass="org.ConcurrencyTests"
 */
public class ConcurrencyTests {

    private static final String HOST = "localhost";
    private static final int PORT = 12345;
    
    // Configurações de Carga
    private static final int NUM_THREADS = 20;
    private static final int OPS_PER_THREAD = 200;
    private static final String[] PRODUCTS = {"banana", "apple", "orange", "milk", "bread"};

    public static void main(String[] args) {
        printHeader();

        try {
            setupUsers();

            // 1. Teste de Stress de Escrita (Múltiplos Clientes)
            runTest("Escrita Concorrente (Multi-Client)", () -> testConcurrentWrites());

            // 2. Teste de Multiplexagem (Um Cliente, Múltiplas Threads) - CRÍTICO para o requisito do PDF
            runTest("Multiplexagem (1 Cliente, N Threads)", () -> testClientMultiplexing());

            // 3. Teste de Consistência com EndDay (Mudança de dia sob carga)
            runTest("Transição de Dia sob Stress (EndDay)", () -> testEndDayUnderLoad());

            // 4. Teste de Leitura vs Escrita (Agregações sob carga)
            runTest("Leituras e Escritas Mistas", () -> testMixedOperations());

        } catch (Exception e) {
            System.err.println("❌ Erro fatal nos testes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ===========================================================================================
    // TESTE 1: Escrita Concorrente Simples
    // Objetivo: Garantir que N clientes a escrever não corrompem o estado da DB.
    // ===========================================================================================
    private static boolean testConcurrentWrites() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);
        CountDownLatch latch = new CountDownLatch(NUM_THREADS);

        long start = System.currentTimeMillis();

        for (int i = 0; i < NUM_THREADS; i++) {
            final int id = i;
            pool.submit(() -> {
                try (ClientLibrary client = new ClientLibrary(HOST, PORT)) {
                    client.authenticate("user" + id, "pass" + id);
                    for (int j = 0; j < OPS_PER_THREAD; j++) {
                        String prod = PRODUCTS[j % PRODUCTS.length];
                        if (client.addSale(prod, 1, 10.0)) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Client " + id + " falhou: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();
        
        long time = System.currentTimeMillis() - start;
        int expected = NUM_THREADS * OPS_PER_THREAD;
        
        System.out.println("   -> Operações: " + successCount.get() + "/" + expected);
        System.out.println("   -> Tempo: " + time + "ms");
        
        // Limpar o dia para não afetar próximos testes
        try (ClientLibrary admin = new ClientLibrary(HOST, PORT)) {
            admin.authenticate("user0", "pass0");
            admin.endDay(); 
        }

        return successCount.get() == expected;
    }

    // ===========================================================================================
    // TESTE 2: Multiplexagem (Requisito PDF: Suporte a clientes multi-threaded)
    // Objetivo: Uma única instância de ClientLibrary usada por várias threads.
    // Verifica se os IDs das tags não se misturam.
    // ===========================================================================================
    private static boolean testClientMultiplexing() throws Exception {
        ClientLibrary sharedClient = new ClientLibrary(HOST, PORT);
        sharedClient.authenticate("user0", "pass0"); // Autentica uma vez

        AtomicInteger errors = new AtomicInteger(0);
        AtomicInteger success = new AtomicInteger(0);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);

        System.out.println("   -> Iniciando 10 threads na mesma conexão socket...");

        for (int i = 0; i < 10; i++) {
            final int id = i; // final para lambda
            pool.submit(() -> {
                try {
                    // Cada thread faz operações diferentes simultaneamente
                    for (int j = 0; j < 50; j++) {
                        // Thread par adiciona vendas, ímpar pede agregações (que retornam 0 rápido)
                        if (id % 2 == 0) {
                            sharedClient.addSale("banana", 1, 5.0);
                        } else {
                            // Pede max price de 1 dia atrás (deve ser rápido)
                            sharedClient.getSalesMaxPrice("banana", 1);
                        }
                        success.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("Erro na thread " + id + ": " + e.getMessage());
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        sharedClient.close();
        pool.shutdown();

        if (errors.get() > 0) {
            System.out.println("   -> ❌ Erros de multiplexagem detectados: " + errors.get());
            return false;
        }
        System.out.println("   -> Sucesso: " + success.get() + " operações multiplexadas sem erro.");
        return true;
    }

    // ===========================================================================================
    // TESTE 3: Transição de Dia (EndDay) sob Stress
    // Objetivo: Mudar de dia enquanto centenas de vendas entram. Nada deve ser perdido.
    // ===========================================================================================
    private static boolean testEndDayUnderLoad() throws Exception {
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger salesSent = new AtomicInteger(0);
        AtomicInteger daysEnded = new AtomicInteger(0);

        // Thread que avança dias
        Thread endDayThread = new Thread(() -> {
            try (ClientLibrary admin = new ClientLibrary(HOST, PORT)) {
                admin.authenticate("user0", "pass0");
                while (running.get()) {
                    Thread.sleep(200); // Avança dia a cada 200ms
                    admin.endDay();
                    daysEnded.incrementAndGet();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Threads que inserem carga
        ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);
        for (int i = 0; i < NUM_THREADS; i++) {
            pool.submit(() -> {
                try (ClientLibrary client = new ClientLibrary(HOST, PORT)) {
                    client.authenticate("user0", "pass0");
                    while (running.get()) {
                        client.addSale("apple", 1, 20.0);
                        salesSent.incrementAndGet();
                        if (salesSent.get() >= 2000) break; // Parar após 2000 vendas globais
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        endDayThread.start();
        
        // Espera as vendas acabarem
        while (salesSent.get() < 2000) {
            Thread.sleep(50);
        }
        running.set(false);
        endDayThread.join();
        pool.shutdown();

        // Verificação de consistência
        // Vamos avançar mais um dia para garantir que tudo foi para o disco
        try (ClientLibrary verifier = new ClientLibrary(HOST, PORT)) {
            verifier.authenticate("user0", "pass0");
            verifier.endDay(); 
            
            // Verifica volume total. Como apple custa 20.0, volume = totalSales * 20
            // Nota: getSalesVolume vê os dias ANTERIORES. Como avançamos dias várias vezes,
            // e demos um endDay final, a soma de todos os dias passados deve bater certo.
            // Vamos pedir volume de 100 dias atrás para apanhar tudo.
            
            double volume = verifier.getSalesVolume("apple", 100);
            double expectedVolume = salesSent.get() * 20.0;
            
            System.out.println("   -> Dias avançados: " + (daysEnded.get() + 1));
            System.out.println("   -> Vendas enviadas: " + salesSent.get());
            System.out.println("   -> Volume esperado (aprox): " + expectedVolume);
            System.out.println("   -> Volume retornado pelo servidor: " + volume);

            // Permitimos uma pequena margem se o teste anterior deixou lixo, 
            // mas num clean run deve ser exato ou maior.
            return volume >= expectedVolume;
        }
    }

    // ===========================================================================================
    // TESTE 4: Operações Mistas
    // Objetivo: Detetar Deadlocks entre leituras e escritas
    // ===========================================================================================
    private static boolean testMixedOperations() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);
        CountDownLatch latch = new CountDownLatch(NUM_THREADS);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < NUM_THREADS; i++) {
            pool.submit(() -> {
                try (ClientLibrary client = new ClientLibrary(HOST, PORT)) {
                    client.authenticate("user0", "pass0");
                    for (int j = 0; j < 50; j++) {
                        double r = Math.random();
                        if (r < 0.3) {
                            client.addSale("milk", 2, 2.5);
                        } else if (r < 0.6) {
                            client.getSalesAveragePrice("milk", 5);
                        } else {
                            client.getSalesQuantity("milk", 5);
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS); // Timeout de segurança
        pool.shutdown();

        if (!finished) {
            System.out.println("   -> ❌ TIMEOUT! Possível Deadlock detetado no servidor.");
            return false;
        }

        return errors.get() == 0;
    }

    // ===========================================================================================
    // Helpers
    // ===========================================================================================

    private static void setupUsers() {
        System.out.print("⚙️  A registar utilizadores de teste... ");
        try (ClientLibrary client = new ClientLibrary(HOST, PORT)) {
            for (int i = 0; i < NUM_THREADS; i++) {
                client.register("user" + i, "pass" + i);
            }
            System.out.println("Feito.");
        } catch (Exception e) {
            System.out.println("(Ignorado se já existirem)");
        }
    }

    private static void runTest(String name, Callable<Boolean> test) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🧪 A executar: " + name);
        System.out.println("=".repeat(60));
        
        try {
            boolean result = test.call();
            if (result) {
                System.out.println("\n✅ " + name + ": PASSOU");
            } else {
                System.out.println("\n❌ " + name + ": FALHOU");
                System.exit(1);
            }
        } catch (Exception e) {
            System.out.println("\n❌ " + name + ": ERRO - " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printHeader() {
        System.out.println("\n");
        System.out.println("   _|_|_|                                                                                                    ");
        System.out.println(" _|          _|_|    _|_|_|      _|_|_|  _|    _|  _|  _|_|  _|  _|_|    _|_|    _|_|_|      _|_|_|  _|    _|");
        System.out.println(" _|        _|    _|  _|    _|  _|        _|    _|  _|_|      _|_|      _|_|_|_|  _|    _|  _|        _|    _|");
        System.out.println(" _|        _|    _|  _|    _|  _|        _|    _|  _|        _|        _|        _|    _|  _|        _|    _|");
        System.out.println("   _|_|_|    _|_|    _|    _|    _|_|_|    _|_|_|  _|        _|          _|_|_|  _|    _|    _|_|_|    _|_|_|");
        System.out.println("                                                                                                           _|    ");
        System.out.println("                                                                                                         _|_|    ");
        System.out.println("TEST SUITE 2026 - Versão Robusta\n");
    }
}