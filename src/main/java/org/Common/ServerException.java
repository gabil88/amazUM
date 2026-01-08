package org.Common;

import java.io.IOException;

/**
 * Exceção que representa erros do lado do servidor durante o processamento de operações.
 * Encapsula erros internos do servidor que devem ser comunicados ao cliente de forma clara.
 */
public class ServerException extends IOException {
    
    public ServerException(String message) {
        super(message);
    }
    
    public ServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
