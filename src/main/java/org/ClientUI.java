package org;

import java.io.IOException;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;

public class ClientUI {
    /**
     * The main entry point for the ClientUI application.
     * Sets up the interface responsible to interact with the server.
     * Allows operations such as user authentication, registration of events, listing sales, etc
     */
     public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            // Create a client library instance that connects to the server
            ClientLibrary client = new ClientLibrary("localhost", 12345);

            int choice = 0;
            boolean validChoice = false;
            // Display initial menu for user choice: register, login, or exit
            while (!validChoice) {
                System.out.println("Choose an option: ");
                System.out.println("1. Log in ");
                System.out.println("2. Register ");
                System.out.println("3. Exit");

                try {
                    choice = scanner.nextInt();
                    scanner.nextLine();
                    if (choice == 1 || choice == 2 || choice == 3) {
                        validChoice = true;
                    } else {
                        System.out.println("Invalid option! Please choose 1 to Register, 2 to Log in, or 3 to Exit.");
                    }
                } catch (InputMismatchException e) {
                    System.out.println("Invalid input! Please enter a number (1, 2 or 3).");
                    scanner.nextLine();
                }
            }

            // If the user chooses to exit, close the client connection and exit
            if (choice == 3) {
                client.close();
                return;
            }

            // fields for register/login
            System.out.println("Enter your username: ");
            String username = scanner.nextLine();
            System.out.println("Enter your password: ");
            String password = scanner.nextLine();

            final boolean[] authenticated = new boolean[1];
            final int finalChoice = choice;
            // Create a new thread to handle authentication or registration
            Thread authThread = new Thread(() -> {
                if (finalChoice == 1) {
                    try {
                        authenticated[0] = client.authenticate(username, password);
                    } catch (IOException e) {
                        System.out.println("Error during authentication: " + e.getMessage());
                        authenticated[0] = false;
                    }
                } else {
                    try {
                        authenticated[0] = client.register(username, password);
                    } catch (IOException e) {
                        System.out.println("Error during registration: " + e.getMessage());
                        authenticated[0] = false;
                    }
                }
            });

            authThread.start();

            try {
                authThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Thread interrupted during authentication.");
                return;
            }

            // If authentication is successful, proceed with operations
            if (authenticated[0]) {
                System.out.println("You have successfully registered!");
                boolean running = true;
                List<Thread> threads = new ArrayList<>();

                while (running) {
                    int operation = 0;
                    validChoice = false;
                    while (!validChoice) {
                        System.out.println("Choose an operation: ");
                        System.out.println("1. Register an Event");
                        System.out.println("2. Sales Quantity");
                        System.out.println("3. Sales Volume");
                        System.out.println("4. Sale Average Price");
                        System.out.println("5. Sale Max Price");
                        System.out.println("6. Exit");

                        try {
                            operation = scanner.nextInt();
                            scanner.nextLine();
                            if (operation >= 1 && operation <= 6) {
                                validChoice = true;
                            } else {
                                System.out.println("Invalid operation! Please choose a valid operation.");
                            }
                        } catch (InputMismatchException e) {
                            System.out.println("Invalid input! Please enter a valid number between 1 and 5.");
                            scanner.nextLine();
                        }
                    }

                    switch(operation){
                        case 1:
                            System.out.println("Enter product name:");
                            String productName = scanner.nextLine();

                            int quantity = 0;
                            while (true) {
                                System.out.println("Enter quantity:");
                                String quantityInput = scanner.nextLine();
                                try {
                                    quantity = Integer.parseInt(quantityInput.trim());
                                    break;
                                } catch (NumberFormatException e) {
                                    System.out.println("Invalid quantity! Please enter a valid integer.");
                                }
                            }

                            double price = 0.0;
                            while (true) {
                                System.out.println("Enter price:");
                                String priceInput = scanner.nextLine();
                                try {
                                    price = Double.parseDouble(priceInput.trim().replace(",", "."));
                                    break;
                                } catch (NumberFormatException e) {
                                    System.out.println("Invalid price! Please enter a valid number (e.g., 2.45).");
                                }
                            }

                            boolean saleAdded = false;
                            try {
                                saleAdded = client.addSale(productName, quantity, price);
                            } catch (IOException e) {
                                System.out.println("Error adding sale: " + e.getMessage());
                            }
                            if (saleAdded) {
                                System.out.println("Sale added successfully!");
                            } else {
                                System.out.println("Failed to add sale.");
                            }
                            break;

                        case 2: 
                        // to do
                        break;

                        case 3: 
                        // to do
                        break;

                        case 4: 
                        // to do
                        break;

                        case 5: 
                        // to do
                        break;

                        case 6:
                            running = false;
                            for (Thread t : threads) {
                                try {
                                    t.join();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    System.out.println("Thread interrupted.");
                                }
                            }
                            try {
                                client.sendDisconnectMessage();
                            } catch (IOException e) {
                                System.out.println("Error during disconnect: " + e.getMessage());
                            }
                            client.close(); // <- Ainda dá catch a um EOF Exception
                            break;

                        default:
                            System.out.println("Invalid operation!");
                            break;
                    }
                }

            } else {
                System.out.println("Authentication failed!");
                client.close();
            }
        
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}