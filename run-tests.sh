#!/bin/bash
# Script para executar os testes de concorrência

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║              RUNNING CONCURRENCY TESTS                       ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "⚠️  Make sure the server is running first!"
echo "   Run in another terminal: ./run-server.sh"
echo ""
echo "Press Enter to continue or Ctrl+C to cancel..."
read

echo "Compiling and running tests..."
mvn compile exec:java -Dexec.mainClass="org.ConcurrencyTests" -q
