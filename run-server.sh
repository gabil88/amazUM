#!/bin/bash
# Script para executar o servidor

echo "=== Iniciando Servidor ==="
mvn compile exec:java -Dexec.mainClass="org.Server.Server"
