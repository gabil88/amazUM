package org.Common;

/**
 * Exceção que representa operações inválidas ou inputs incorretos.
 * Usada para validação de dados antes de enviar ao servidor.
 */
public class InvalidOperationException extends RuntimeException {
    
    public InvalidOperationException(String message) {
        super(message);
    }
    
    public InvalidOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
