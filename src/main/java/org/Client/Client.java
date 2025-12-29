package org.Client;

import org.Common.IAmazUM;

/**
 * The Client class serves as the entry point for the client-side application.
 * Initializes the Client UI.
 */
public class Client {
    public static void main(String[] args) {
        try {
            IAmazUM client = new ClientStub("localhost", 12345);
            ClientUI ui = new ClientUI(client);
            ui.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}