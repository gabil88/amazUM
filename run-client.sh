#!/bin/bash
# Script para executar o cliente

echo "=== Iniciando Cliente ==="
mvn compile exec:java -Dexec.mainClass="org.Client.Client"
