package org.Common;

import java.io.IOException;

/**
 * Exceção que representa erros de comunicação de rede.
 * Facilita a distinção entre erros de rede e outros tipos de erros.
 */
public class NetworkException extends IOException {
    
    public NetworkException(String message) {
        super(message);
    }
    
    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
