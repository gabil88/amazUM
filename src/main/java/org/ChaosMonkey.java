package org;

import org.Client.ClientStub;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ChaosMonkey {
    private static final String[] PRODUCTS = {
        // Produtos Gregos (8)
        "chaos_alpha", "chaos_beta", "chaos_gamma", "chaos_delta", 
        "chaos_epsilon", "chaos_zeta", "chaos_eta", "chaos_theta",
        "chaos_iota", "chaos_kappa", "chaos_lambda", "chaos_mu",
        "chaos_nu", "chaos_xi", "chaos_omicron", "chaos_pi",
        
        // Produtos Latinos (16)
        "chaos_primus", "chaos_secundus", "chaos_tertius", "chaos_quartus",
        "chaos_quintus", "chaos_sextus", "chaos_septimus", "chaos_octavus",
        "chaos_nonus", "chaos_decimus", "chaos_undecimus", "chaos_duodecimus",
        "chaos_tertius_decimus", "chaos_quartus_decimus", "chaos_quintus_decimus", "chaos_sextus_decimus",
        
        // Produtos Elementos (16)
        "chaos_hydrogen", "chaos_helium", "chaos_lithium", "chaos_beryllium",
        "chaos_carbon", "chaos_nitrogen", "chaos_oxygen", "chaos_fluorine",
        "chaos_neon", "chaos_sodium", "chaos_magnesium", "chaos_aluminum",
        "chaos_silicon", "chaos_phosphorus", "chaos_sulfur", "chaos_chlorine",
        
        // Produtos Cores (16)
        "chaos_crimson", "chaos_scarlet", "chaos_vermillion", "chaos_ruby",
        "chaos_azure", "chaos_sapphire", "chaos_cobalt", "chaos_navy",
        "chaos_emerald", "chaos_jade", "chaos_forest", "chaos_lime",
        "chaos_amber", "chaos_gold", "chaos_topaz", "chaos_citrine",
        
        // Produtos Metais (16)
        "chaos_iron", "chaos_copper", "chaos_silver", "chaos_gold_metal",
        "chaos_platinum", "chaos_titanium", "chaos_chromium", "chaos_nickel",
        "chaos_zinc", "chaos_tin", "chaos_lead", "chaos_mercury",
        "chaos_aluminum_metal", "chaos_bronze", "chaos_brass", "chaos_steel"
    };

    // Tipos de operação
    private static final String OP_ADD_SALE = "addSale";
    private static final String OP_QUANTITY = "getSalesQuantity";
    private static final String OP_VOLUME = "getSalesVolume";
    private static final String OP_AVERAGE = "getSalesAveragePrice";
    private static final String OP_MAX_PRICE = "getSalesMaxPrice";
    private static final String OP_END_DAY = "endDay";
    private static final String OP_FILTER_EVENTS = "filterEvents";

    // Contadores globais
    private static final AtomicLong totalOps = new AtomicLong(0);
    private static final AtomicLong errors = new AtomicLong(0);
    private static final AtomicLong networkErrors = new AtomicLong(0);
    private static final AtomicLong timeouts = new AtomicLong(0);

    // Métricas de tempo por tipo de operação
    private static final ConcurrentHashMap<String, OperationMetrics> metricsPerOperation = new ConcurrentHashMap<>();

    // Lista concorrente para armazenar todas as latências (para cálculo de percentis)
    private static final ConcurrentLinkedQueue<Long> allLatencies = new ConcurrentLinkedQueue<>();
    private static final AtomicLong totalResponseTime = new AtomicLong(0);

    // Classe para armazenar métricas por operação
    static class OperationMetrics {
        final String operationName;
        final AtomicLong count = new AtomicLong(0);
        final AtomicLong totalTime = new AtomicLong(0);
        final AtomicLong minTime = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong maxTime = new AtomicLong(0);
        final AtomicLong errors = new AtomicLong(0);
        final ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();

        OperationMetrics(String name) {
            this.operationName = name;
        }

        void recordSuccess(long timeNanos) {
            count.incrementAndGet();
            totalTime.addAndGet(timeNanos);
            latencies.add(timeNanos);
            
            // Update min
            long currentMin;
            do {
                currentMin = minTime.get();
                if (timeNanos >= currentMin) break;
            } while (!minTime.compareAndSet(currentMin, timeNanos));
            
            // Update max
            long currentMax;
            do {
                currentMax = maxTime.get();
                if (timeNanos <= currentMax) break;
            } while (!maxTime.compareAndSet(currentMax, timeNanos));
        }

        void recordError() {
            errors.incrementAndGet();
        }

        double getAverageMs() {
            long c = count.get();
            return c > 0 ? (totalTime.get() / (double) c) / 1_000_000.0 : 0;
        }

        double getMinMs() {
            long min = minTime.get();
            return min == Long.MAX_VALUE ? 0 : min / 1_000_000.0;
        }

        double getMaxMs() {
            return maxTime.get() / 1_000_000.0;
        }

        double[] getPercentiles() {
            List<Long> sorted = new ArrayList<>(latencies);
            if (sorted.isEmpty()) return new double[]{0, 0, 0};
            Collections.sort(sorted);
            int size = sorted.size();
            double p50 = sorted.get((int)(size * 0.50)) / 1_000_000.0;
            double p95 = sorted.get((int)(size * 0.95)) / 1_000_000.0;
            double p99 = sorted.get(Math.min((int)(size * 0.99), size - 1)) / 1_000_000.0;
            return new double[]{p50, p95, p99};
        }

        double getErrorRate() {
            long total = count.get() + errors.get();
            return total > 0 ? (errors.get() * 100.0 / total) : 0;
        }
    }

    // Inicializar métricas para cada operação
    static {
        metricsPerOperation.put(OP_ADD_SALE, new OperationMetrics(OP_ADD_SALE));
        metricsPerOperation.put(OP_QUANTITY, new OperationMetrics(OP_QUANTITY));
        metricsPerOperation.put(OP_VOLUME, new OperationMetrics(OP_VOLUME));
        metricsPerOperation.put(OP_AVERAGE, new OperationMetrics(OP_AVERAGE));
        metricsPerOperation.put(OP_MAX_PRICE, new OperationMetrics(OP_MAX_PRICE));
        metricsPerOperation.put(OP_END_DAY, new OperationMetrics(OP_END_DAY));
        metricsPerOperation.put(OP_FILTER_EVENTS, new OperationMetrics(OP_FILTER_EVENTS));
    }
    
    public static void main(String[] args) throws Exception {
        int duration = Integer.parseInt(args[0]);
        int numThreads = Integer.parseInt(args[1]);
        
        System.out.println("🐒 CHAOS MONKEY ATIVADO!");
        System.out.println("Duração: " + duration + "s");
        System.out.println("Threads: " + numThreads);
        System.out.println();
        
        long startTime = System.currentTimeMillis();
        
        // Registar usuários de caos
        for (int i = 0; i < numThreads; i++) {
            try (ClientStub c = new ClientStub("localhost", 12345)) {
                c.register("chaos" + i, "chaos");
            } catch (Exception e) {
                // Ignorar se já existe
            }
        }
        
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        long endTime = System.currentTimeMillis() + (duration * 1000L);
        
        // Thread de relatório em tempo real
        Thread reporter = new Thread(() -> {
            while (System.currentTimeMillis() < endTime) {
                try {
                    Thread.sleep(10000); // Report a cada 10s
                    long ops = totalOps.get();
                    long errs = errors.get();
                    double errorRate = ops > 0 ? (errs * 100.0 / ops) : 0;
                    
                    // Calcular tempo médio total
                    double avgTotalMs = calculateGlobalAverageMs();
                    
                    System.out.printf("[CHAOS] Ops: %d | Erros: %d (%.2f%%) | Network: %d | Timeouts: %d | Avg: %.2fms%n",
                        ops, errs, errorRate, networkErrors.get(), timeouts.get(), avgTotalMs);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        reporter.start();
        
        // Lançar threads de caos
        for (int i = 0; i < numThreads; i++) {
            final int id = i;
            pool.submit(() -> runChaos(id, endTime));
        }
        
        reporter.join();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        
        long actualDuration = System.currentTimeMillis() - startTime;
        
        printFinalReport(actualDuration);
    }

    private static double calculateGlobalAverageMs() {
        long totalTime = 0;
        long totalCount = 0;
        for (OperationMetrics m : metricsPerOperation.values()) {
            totalTime += m.totalTime.get();
            totalCount += m.count.get();
        }
        return totalCount > 0 ? (totalTime / (double) totalCount) / 1_000_000.0 : 0;
    }

    private static double[] calculateGlobalPercentiles() {
        List<Long> allLats = new ArrayList<>();
        for (OperationMetrics m : metricsPerOperation.values()) {
            allLats.addAll(m.latencies);
        }
        if (allLats.isEmpty()) return new double[]{0, 0, 0};
        Collections.sort(allLats);
        int size = allLats.size();
        double p50 = allLats.get((int)(size * 0.50)) / 1_000_000.0;
        double p95 = allLats.get((int)(size * 0.95)) / 1_000_000.0;
        double p99 = allLats.get(Math.min((int)(size * 0.99), size - 1)) / 1_000_000.0;
        return new double[]{p50, p95, p99};
    }

    private static void printFinalReport(long durationMs) {
        System.out.println();
        System.out.println("═".repeat(80));
        System.out.println("                    RELATÓRIO FINAL DE CAOS");
        System.out.println("═".repeat(80));
        
        // Métricas gerais
        double durationSec = durationMs / 1000.0;
        long ops = totalOps.get();
        double throughput = ops / durationSec;
        double avgMs = calculateGlobalAverageMs();
        double[] globalPercentiles = calculateGlobalPercentiles();
        
        System.out.println();
        System.out.println("📊 MÉTRICAS GERAIS");
        System.out.println("─".repeat(80));
        System.out.printf("  Duração Total:           %.2f segundos%n", durationSec);
        System.out.printf("  Total de Operações:      %d%n", ops);
        System.out.printf("  Throughput:              %.2f ops/s%n", throughput);
        System.out.printf("  Tempo Médio Resposta:    %.2f ms%n", avgMs);
        System.out.printf("  Latência P50:            %.2f ms%n", globalPercentiles[0]);
        System.out.printf("  Latência P95:            %.2f ms%n", globalPercentiles[1]);
        System.out.printf("  Latência P99:            %.2f ms%n", globalPercentiles[2]);
        System.out.println();
        
        // Erros
        System.out.println("❌ ERROS");
        System.out.println("─".repeat(80));
        System.out.printf("  Total de Erros:          %d (%.2f%%)%n", errors.get(), 
            ops > 0 ? (errors.get() * 100.0 / ops) : 0);
        System.out.printf("  Erros de Rede:           %d%n", networkErrors.get());
        System.out.printf("  Timeouts:                %d%n", timeouts.get());
        System.out.println();
        
        // Métricas por operação
        System.out.println("📈 MÉTRICAS POR TIPO DE OPERAÇÃO");
        System.out.println("─".repeat(80));
        System.out.printf("  %-20s %10s %10s %10s %10s %10s %10s%n", 
            "Operação", "Count", "Avg(ms)", "Min(ms)", "Max(ms)", "P95(ms)", "Erros(%)");
        System.out.println("  " + "─".repeat(78));
        
        String[] orderedOps = {OP_ADD_SALE, OP_QUANTITY, OP_VOLUME, OP_AVERAGE, OP_MAX_PRICE, OP_FILTER_EVENTS, OP_END_DAY};
        for (String opName : orderedOps) {
            OperationMetrics m = metricsPerOperation.get(opName);
            if (m != null && m.count.get() > 0) {
                double[] percentiles = m.getPercentiles();
                System.out.printf("  %-20s %10d %10.2f %10.2f %10.2f %10.2f %10.2f%n",
                    opName,
                    m.count.get(),
                    m.getAverageMs(),
                    m.getMinMs(),
                    m.getMaxMs(),
                    percentiles[1], // P95
                    m.getErrorRate());
            }
        }
        System.out.println();
        
        // Distribuição de operações
        System.out.println("📊 DISTRIBUIÇÃO DE OPERAÇÕES");
        System.out.println("─".repeat(80));
        long totalSuccessOps = 0;
        for (OperationMetrics m : metricsPerOperation.values()) {
            totalSuccessOps += m.count.get();
        }
        for (String opName : orderedOps) {
            OperationMetrics m = metricsPerOperation.get(opName);
            if (m != null) {
                long count = m.count.get();
                double percentage = totalSuccessOps > 0 ? (count * 100.0 / totalSuccessOps) : 0;
                int barLength = (int)(percentage / 2);
                String bar = "█".repeat(Math.max(0, barLength));
                System.out.printf("  %-20s %5.1f%% %s (%d)%n", opName, percentage, bar, count);
            }
        }
        System.out.println();
        
        System.out.println("═".repeat(80));
        if (errors.get() > ops * 0.1) {
            System.out.println("⚠️  ATENÇÃO: Taxa de erro muito alta (> 10%)!");
            System.exit(1);
        } else {
            System.out.println("✅ Servidor sobreviveu ao caos!");
            System.exit(0);
        }
    }
    
    private static void runChaos(int threadId, long endTime) {
        Random rand = new Random();
        
        try (ClientStub client = new ClientStub("localhost", 12345)) {
            client.authenticate("chaos" + threadId, "chaos");

            while (System.currentTimeMillis() < endTime) {
                String opType = null;
                long startNanos = 0;
                
                try {
                    int action = rand.nextInt(1000);
                    String product = PRODUCTS[rand.nextInt(PRODUCTS.length)];

                    startNanos = System.nanoTime();

                    if (action < 250) {
                        // 25% writes
                        opType = OP_ADD_SALE;
                        client.addSale(product, rand.nextInt(100) + 1, 
                            rand.nextDouble() * 1000 + 1);
                    } else if (action < 400) {
                        // 15% quantity
                        opType = OP_QUANTITY;
                        client.getSalesQuantity(product, rand.nextInt(30) + 1);
                    } else if (action < 550) {
                        // 15% volume
                        opType = OP_VOLUME;
                        client.getSalesVolume(product, rand.nextInt(30) + 1);
                    } else if (action < 700) {
                        // 15% average
                        opType = OP_AVERAGE;
                        client.getSalesAveragePrice(product, rand.nextInt(30) + 1);
                    } else if (action < 850) {
                        // 15% max
                        opType = OP_MAX_PRICE;
                        client.getSalesMaxPrice(product, rand.nextInt(30) + 1);
                    } else if (action < 990) {
                        // 14% filterEvents (nova operação)
                        opType = OP_FILTER_EVENTS;
                        int numProducts = rand.nextInt(5) + 1;
                        List<String> productsList = new ArrayList<>();
                        for (int i = 0; i < numProducts; i++) {
                            productsList.add(PRODUCTS[rand.nextInt(PRODUCTS.length)]);
                        }
                        client.filterEvents(productsList, rand.nextInt(30) + 1);
                    } else if (action < 991) {
                        // 0.1% endDay (muito raro)
                        opType = OP_END_DAY;
                        client.endDay();
                    } else {
                        // restante - skip
                        continue;
                    }

                    // Registar métricas de sucesso
                    long elapsed = System.nanoTime() - startNanos;
                    if (opType != null) {
                        OperationMetrics metrics = metricsPerOperation.get(opType);
                        if (metrics != null) {
                            metrics.recordSuccess(elapsed);
                        }
                        allLatencies.add(elapsed);
                        totalResponseTime.addAndGet(elapsed);
                    }

                    totalOps.incrementAndGet();

                    // Sleep aleatório para simular carga variável
                    if (rand.nextDouble() < 0.3) {
                        Thread.sleep(rand.nextInt(50));
                    }

                } catch (java.net.SocketTimeoutException e) {
                    timeouts.incrementAndGet();
                    errors.incrementAndGet();
                    if (opType != null) {
                        OperationMetrics metrics = metricsPerOperation.get(opType);
                        if (metrics != null) metrics.recordError();
                    }
                } catch (java.io.IOException e) {
                    networkErrors.incrementAndGet();
                    errors.incrementAndGet();
                    if (opType != null) {
                        OperationMetrics metrics = metricsPerOperation.get(opType);
                        if (metrics != null) metrics.recordError();
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                    if (opType != null) {
                        OperationMetrics metrics = metricsPerOperation.get(opType);
                        if (metrics != null) metrics.recordError();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Thread " + threadId + " crashed: " + e.getMessage());
        }
    }
}
