#!/bin/bash
# run_stress_test.sh
# Script principal para executar testes de stress no servidor

set -e

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuração padrão
NUM_CLIENTS=50
OPS_PER_CLIENT=100
DURATION=60
SERVER_HOST="localhost"
SERVER_PORT=12345

# Parse argumentos
while getopts "c:o:d:h:p:" opt; do
  case $opt in
    c) NUM_CLIENTS=$OPTARG ;;
    o) OPS_PER_CLIENT=$OPTARG ;;
    d) DURATION=$OPTARG ;;
    h) SERVER_HOST=$OPTARG ;;
    p) SERVER_PORT=$OPTARG ;;
    \?) echo "Opção inválida: -$OPTARG" >&2; exit 1 ;;
  esac
done

echo -e "${BLUE}╔════════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║${NC}          ${GREEN}STRESS TEST SUITE${NC} - Sistema de Vendas                ${BLUE}║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${YELLOW}Configuração:${NC}"
echo -e "  Servidor: ${SERVER_HOST}:${SERVER_PORT}"
echo -e "  Clientes: ${NUM_CLIENTS}"
echo -e "  Operações por cliente: ${OPS_PER_CLIENT}"
echo -e "  Duração: ${DURATION}s"
echo ""

# Verificar se o servidor está a correr
echo -ne "${YELLOW}Verificando servidor...${NC} "
if ! nc -z $SERVER_HOST $SERVER_PORT 2>/dev/null; then
    echo -e "${RED}FALHOU${NC}"
    echo -e "${RED}Erro: Servidor não está a responder em ${SERVER_HOST}:${SERVER_PORT}${NC}"
    echo "Inicie o servidor primeiro com: mvn compile exec:java -Dexec.mainClass=\"org.Server\""
    exit 1
fi
echo -e "${GREEN}OK${NC}"

# Compilar se necessário
echo -ne "${YELLOW}Compilando projeto...${NC} "
if mvn compile -q; then
    echo -e "${GREEN}OK${NC}"
else
    echo -e "${RED}FALHOU${NC}"
    exit 1
fi

# Criar diretório para logs
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
LOG_DIR="stress_test_logs/${TIMESTAMP}"
mkdir -p "$LOG_DIR"

echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}Iniciando Testes de Stress...${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════════════${NC}"
echo ""

# Executar teste principal
mvn exec:java \
    -Dexec.mainClass="org.StressTestSuite" \
    -Dexec.args="$NUM_CLIENTS $OPS_PER_CLIENT $DURATION" \
    2>&1 | tee "$LOG_DIR/main_test.log"

TEST_RESULT=${PIPESTATUS[0]}

# Mover log gerado pela aplicação
if [ -f stress_test_*.log ]; then
    mv stress_test_*.log "$LOG_DIR/"
fi

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}Teste Concluído${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo ""
echo "Logs guardados em: $LOG_DIR"

if [ $TEST_RESULT -eq 0 ]; then
    echo -e "${GREEN}✓ Todos os testes passaram!${NC}"
    exit 0
else
    echo -e "${RED}✗ Alguns testes falharam. Verifique os logs para detalhes.${NC}"
    exit 1
fi