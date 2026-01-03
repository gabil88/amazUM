#!/bin/bash

# Cores para melhor visualização
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Função para limpar a tela
clear_screen() {
    clear
}

# Função para compilar o projeto
compile_project() {
    echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${YELLOW}🔨 Compilando o projeto...${NC}"
    echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
    
    if mvn clean compile > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Projeto compilado com sucesso!${NC}"
        return 0
    else
        echo -e "${RED}✗ Erro ao compilar o projeto!${NC}"
        echo -e "${YELLOW}Executando novamente com output visível...${NC}"
        mvn clean compile
        return 1
    fi
}

# Função para limpar a base de dados
clean_database() {
    echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${YELLOW}🗑️  Limpando a base de dados...${NC}"
    echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
    
    if [ -d "storage" ]; then
        rm -rf storage/*
        echo -e "${GREEN}✓ Base de dados limpa!${NC}"
    else
        echo -e "${YELLOW}⚠ Diretório 'storage' não existe. Nada para limpar.${NC}"
    fi
}

# Função para iniciar o servidor
start_server() {
    echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${YELLOW}🚀 Iniciando o servidor...${NC}"
    echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
    
    java -cp target/classes org.Server.Server
}

# Função para iniciar o cliente
start_client() {
    echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${YELLOW}👤 Iniciando o cliente...${NC}"
    echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
    
    java -cp target/classes org.Client.Client
}

# Função para executar o Chaos Monkey
run_chaos_monkey() {
    echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${YELLOW}🐒 Configurar Chaos Monkey${NC}"
    echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
    
    echo -e "${CYAN}Duração em segundos [padrão: 60]:${NC}"
    read -r duration
    duration=${duration:-60}
    
    echo -e "${CYAN}Número de threads [padrão: 10]:${NC}"
    read -r threads
    threads=${threads:-10}
    
    echo -e "${GREEN}Iniciando Chaos Monkey (${duration}s, ${threads} threads)...${NC}"
    
    # Criar diretório de logs se não existir
    mkdir -p chaos_logs
    TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
    LOG_DIR="chaos_logs/${TIMESTAMP}"
    mkdir -p "$LOG_DIR"
    
    java -cp target/classes org.ChaosMonkey "$duration" "$threads" 2>&1 | tee "$LOG_DIR/chaos.log"
    
    echo -e "${GREEN}✓ Log salvo em: $LOG_DIR/chaos.log${NC}"
}

# Função para executar testes de stress
run_stress_tests() {
    echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${YELLOW}🔥 Executando Stress Tests...${NC}"
    echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
    
    java -cp target/classes org.StressTestSuite
}

# Função para compilar e criar JAR
build_jar() {
    echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${YELLOW}📦 Compilando e criando JAR...${NC}"
    echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
    
    mvn clean package
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ JAR criado com sucesso em target/amazUM-1.0-SNAPSHOT.jar${NC}"
    else
        echo -e "${RED}✗ Erro ao criar JAR${NC}"
    fi
}

# Menu principal
show_menu() {
    clear_screen
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${MAGENTA}         🎯 MENU PRINCIPAL - AmazUM System${NC}"
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "${CYAN}1)${NC} 🚀 Iniciar Servidor"
    echo -e "${CYAN}2)${NC} 👤 Iniciar Cliente"
    echo -e "${CYAN}3)${NC} 🐒 Executar Chaos Monkey"
    echo -e "${CYAN}4)${NC} 🔥 Executar Stress Tests"
    echo -e "${CYAN}5)${NC} 📦 Compilar e Criar JAR"
    echo -e "${CYAN}6)${NC} 🗑️  Limpar Base de Dados"
    echo -e "${CYAN}7)${NC} ❌ Sair"
    echo ""
    echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
    echo -ne "${GREEN}Escolha uma opção [1-7]: ${NC}"
}

# Loop principal
main() {
    # Compilar na primeira execução
    echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}🎯 Bem-vindo ao AmazUM System Manager${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
    echo ""
    
    compile_project
    if [ $? -ne 0 ]; then
        echo -e "${RED}Erro na compilação inicial. Pressione Enter para continuar...${NC}"
        read -r
    fi
    
    while true; do
        show_menu
        read -r choice
        
        case $choice in
            1)
                clear_screen
                echo ""
                start_server
                echo -e "\n${YELLOW}Pressione Enter para voltar ao menu...${NC}"
                read -r
                ;;
            2)
                clear_screen
                echo ""
                start_client
                echo -e "\n${YELLOW}Pressione Enter para voltar ao menu...${NC}"
                read -r
                ;;
            3)
                clear_screen
                echo ""
                run_chaos_monkey
                echo -e "\n${YELLOW}Pressione Enter para voltar ao menu...${NC}"
                read -r
                ;;
            4)
                clear_screen
                echo ""
                run_stress_tests
                echo -e "\n${YELLOW}Pressione Enter para voltar ao menu...${NC}"
                read -r
                ;;
            5)
                clear_screen
                build_jar
                echo -e "\n${YELLOW}Pressione Enter para voltar ao menu...${NC}"
                read -r
                ;;
            6)
                clear_screen
                clean_database
                echo -e "\n${YELLOW}Pressione Enter para voltar ao menu...${NC}"
                read -r
                ;;
            7)
                clear_screen
                echo -e "${GREEN}👋 Até logo!${NC}"
                exit 0
                ;;
            *)
                echo -e "${RED}Opção inválida! Pressione Enter para tentar novamente...${NC}"
                read -r
                ;;
        esac
    done
}

# Verificar se estamos no diretório correto
if [ ! -f "pom.xml" ]; then
    echo -e "${RED}Erro: Execute este script no diretório raiz do projeto (onde está o pom.xml)${NC}"
    exit 1
fi

# Executar o menu principal
main
