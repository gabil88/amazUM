#!/bin/bash
# chaos_monkey.sh
# Teste de "Chaos Engineering" - operações aleatórias contínuas para encontrar bugs

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'

# Configuração
DURATION=${1:-300}  # Duração em segundos (padrão: 5 min)
NUM_CHAOS_THREADS=${2:-15}
SERVER_HOST="localhost"
SERVER_PORT=12345

echo -e "${MAGENTA}"
cat << "EOF"
    _____ _                     __  __             _             
   / ____| |                   |  \/  |           | |            
  | |    | |__   __ _  ___  ___| \  / | ___  _ __ | | _____ _   _ 
  | |    | '_ \ / _` |/ _ \/ __| |\/| |/ _ \| '_ \| |/ / _ \ | | |
  | |____| | | | (_| | (_) \__ \ |  | | (_) | | | |   <  __/ |_| |
   \_____|_| |_|\__,_|\___/|___/_|  |_|\___/|_| |_|_|\_\___|\__, |
                                                              __/ |
   Sistema de Vendas - Teste de Caos                        |___/ 
EOF
echo -e "${NC}"

echo ""
echo -e "${YELLOW}Configuração do Caos:${NC}"
echo -e "  Duração: ${DURATION}s ($(($DURATION / 60)) minutos)"
echo -e "  Threads de Caos: ${NUM_CHAOS_THREADS}"
echo -e "  Servidor: ${SERVER_HOST}:${SERVER_PORT}"
echo ""
echo -e "${RED}ATENÇÃO: Este teste vai bombardear o servidor com operações aleatórias!${NC}"
echo -e "${RED}         Monitorize CPU, memória e logs do servidor durante a execução.${NC}"
echo ""

read -p "Pressione Enter para iniciar o caos... " -r

# Verificar servidor
echo -ne "${YELLOW}Verificando servidor...${NC} "
if ! nc -z $SERVER_HOST $SERVER_PORT 2>/dev/null; then
    echo -e "${RED}FALHOU${NC}"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

# Compilar
echo -ne "${YELLOW}Compilando...${NC} "
mvn compile -q && echo -e "${GREEN}OK${NC}" || { echo -e "${RED}FALHOU${NC}"; exit 1; }

# Criar diretório de logs
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
LOG_DIR="chaos_logs/${TIMESTAMP}"
mkdir -p "$LOG_DIR"

# Criar script Java de Chaos Monkey
cat > src/main/java/org/ChaosMonkey.java << 'JAVAEOF'
package org;

import org.Client.ClientStub;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ChaosMonkey {
    private static final String[] PRODUCTS = {
        "chaos_alpha", "chaos_beta", "chaos_gamma", "chaos_delta", 
        "chaos_epsilon", "chaos_zeta", "chaos_eta", "chaos_theta"
    };
    
    private static final AtomicLong totalOps = new AtomicLong(0);
    private static final AtomicLong errors = new AtomicLong(0);
    private static final AtomicLong networkErrors = new AtomicLong(0);
    private static final AtomicLong timeouts = new AtomicLong(0);
    
    public static void main(String[] args) throws Exception {
        int duration = Integer.parseInt(args[0]);
        int numThreads = Integer.parseInt(args[1]);
        
        System.out.println("🐒 CHAOS MONKEY ATIVADO!");
        System.out.println("Duração: " + duration + "s");
        System.out.println("Threads: " + numThreads);
        System.out.println();
        
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
                    
                    System.out.printf("[CHAOS] Ops: %d | Erros: %d (%.2f%%) | Network: %d | Timeouts: %d%n",
                        ops, errs, errorRate, networkErrors.get(), timeouts.get());
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
        
        System.out.println();
        System.out.println("═".repeat(60));
        System.out.println("RELATÓRIO FINAL DE CAOS");
        System.out.println("═".repeat(60));
        System.out.println("Total de Operações: " + totalOps.get());
        System.out.println("Erros: " + errors.get() + " (" + 
            String.format("%.2f%%", (errors.get() * 100.0) / totalOps.get()) + ")");
        System.out.println("  - Erros de Rede: " + networkErrors.get());
        System.out.println("  - Timeouts: " + timeouts.get());
        System.out.println();
        
        if (errors.get() > totalOps.get() * 0.1) {
            System.out.println("⚠️  ATENÇÃO: Taxa de erro muito alta!");
            System.exit(1);
        } else {
            System.out.println("✓ Servidor sobreviveu ao caos!");
            System.exit(0);
        }
    }
    
    private static void runChaos(int threadId, long endTime) {
        Random rand = new Random();
        
        try (ClientStub client = new ClientStub("localhost", 12345)) {
            client.authenticate("chaos" + threadId, "chaos");

            while (System.currentTimeMillis() < endTime) {
                try {
                    int action = rand.nextInt(1000); // aumenta o range para tornar endDay mais raro
                    String product = PRODUCTS[rand.nextInt(PRODUCTS.length)];

                    if (action < 300) {
                        // 30% writes
                        client.addSale(product, rand.nextInt(100) + 1, 
                            rand.nextDouble() * 1000 + 1);
                    } else if (action < 500) {
                        // 20% quantity
                        client.getSalesQuantity(product, rand.nextInt(30) + 1);
                    } else if (action < 700) {
                        // 20% volume
                        client.getSalesVolume(product, rand.nextInt(30) + 1);
                    } else if (action < 850) {
                        // 15% average
                        client.getSalesAveragePrice(product, rand.nextInt(30) + 1);
                    } else if (action < 950) {
                        // 10% max
                        client.getSalesMaxPrice(product, rand.nextInt(30) + 1);
                    } else if (action < 951) {
                        // 0.5% endDay (muito raro)
                        client.endDay();
                    }

                    totalOps.incrementAndGet();

                    // Sleep aleatório para simular carga variável
                    if (rand.nextDouble() < 0.3) {
                        Thread.sleep(rand.nextInt(50));
                    }

                } catch (java.net.SocketTimeoutException e) {
                    timeouts.incrementAndGet();
                    errors.incrementAndGet();
                } catch (java.io.IOException e) {
                    networkErrors.incrementAndGet();
                    errors.incrementAndGet();
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            }
        } catch (Exception e) {
            System.err.println("Thread " + threadId + " crashed: " + e.getMessage());
        }
    }
}
JAVAEOF

# Compilar Chaos Monkey
echo -ne "${YELLOW}Compilando Chaos Monkey...${NC} "
mvn compile -q && echo -e "${GREEN}OK${NC}" || { echo -e "${RED}FALHOU${NC}"; exit 1; }

echo ""
echo -e "${RED}╔════════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${RED}║${NC}                    ${YELLOW}🐒 LIBERANDO O CAOS! 🐒${NC}                        ${RED}║${NC}"
echo -e "${RED}╚════════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Executar
mvn exec:java \
    -Dexec.mainClass="org.ChaosMonkey" \
    -Dexec.args="$DURATION $NUM_CHAOS_THREADS" \
    2>&1 | tee "$LOG_DIR/chaos.log"

EXIT_CODE=${PIPESTATUS[0]}

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo "Logs guardados em: $LOG_DIR"
echo ""

if [ $EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✓ Servidor sobreviveu ao teste de caos!${NC}"
    exit 0
else
    echo -e "${RED}✗ Servidor teve problemas durante o caos. Verifique os logs.${NC}"
    exit 1
fi