package org.Client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;

public class ClientUI {

    /**
     * Hand                    switch(operation){
                        case 1:
                            handleAddSale(client, scanner);
                            break;

                        case 2: 
                            handleSalesAverage(client, scanner);
                            break; authentication or registration.
     * 
     * @param client The client library instance.
     * @param scanner The scanner for user input.
     * @param choice 1 for login, 2 for register.
     * @return true if authentication/registration was successful, false otherwise.
     */
    private static boolean handleAuthentication(ClientLibrary client, Scanner scanner, int choice) {
        System.out.println("Enter your username: ");
        String username = scanner.nextLine();
        System.out.println("Enter your password: ");
        String password = scanner.nextLine();

        final boolean[] authenticated = new boolean[1];
        
        Thread authThread = new Thread(() -> {
            if (choice == 1) {
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
            return false;
        }

        return authenticated[0];
    }

    /**
     * Handles the add sale operation by prompting user for product details.
     * 
     * @param client The client library instance.
     * @param scanner The scanner for user input.
     */
    private static void handleAddSale(ClientLibrary client, Scanner scanner) {
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
    }

    private static void handleSalesAverage(ClientLibrary client, Scanner scanner){
        System.out.println("Enter product name:");
        String productName = scanner.nextLine();

        int days = 0;
        while (true) {
            System.out.println("Enter number of days to aggregate:");
            String daysInput = scanner.nextLine();
            try {
                days = Integer.parseInt(daysInput.trim());
                if (days < 1) {
                    System.out.println("Number of days must be at least 1.");
                    continue;
                }
                break;
            } catch (NumberFormatException e) {
                System.out.println("Invalid input! Please enter a valid integer.");
            }
        }

        try {
            double avgPrice = client.getSalesAveragePrice(productName, days);
            System.out.println("Average price for '" + productName + "' in the last " + days + " days: " + avgPrice);
        } catch (IOException e) {
            System.out.println("Error getting average price: " + e.getMessage());
        }
    }

    /**
     * Handles the sales quantity query.
     * 
     * @param client The client library instance.
     * @param scanner The scanner for user input.
     */
    private static void handleSalesQuantity(ClientLibrary client, Scanner scanner) {
        System.out.println("Enter product name:");
        String productName = scanner.nextLine();

        int days = 0;
        while (true) {
            System.out.println("Enter number of days to aggregate:");
            String daysInput = scanner.nextLine();
            try {
                days = Integer.parseInt(daysInput.trim());
                if (days < 1) {
                    System.out.println("Number of days must be at least 1.");
                    continue;
                }
                break;
            } catch (NumberFormatException e) {
                System.out.println("Invalid input! Please enter a valid integer.");
            }
        }

        try {
            int quantity = client.getSalesQuantity(productName, days);
            System.out.println("Total quantity sold for '" + productName + "' in the last " + days + " days: " + quantity);
        } catch (IOException e) {
            System.out.println("Error getting sales quantity: " + e.getMessage());
        }
    }

    /**
     * Handles the sales volume query.
     * 
     * @param client The client library instance.
     * @param scanner The scanner for user input.
     */
    private static void handleSalesVolume(ClientLibrary client, Scanner scanner) {
        System.out.println("Enter product name:");
        String productName = scanner.nextLine();

        int days = 0;
        while (true) {
            System.out.println("Enter number of days to aggregate:");
            String daysInput = scanner.nextLine();
            try {
                days = Integer.parseInt(daysInput.trim());
                if (days < 1) {
                    System.out.println("Number of days must be at least 1.");
                    continue;
                }
                break;
            } catch (NumberFormatException e) {
                System.out.println("Invalid input! Please enter a valid integer.");
            }
        }

        try {
            double volume = client.getSalesVolume(productName, days);
            System.out.println("Total sales volume for '" + productName + "' in the last " + days + " days: " + volume);
        } catch (IOException e) {
            System.out.println("Error getting sales volume: " + e.getMessage());
        }
    }

    /**
     * Handles the sales max price query.
     * 
     * @param client The client library instance.
     * @param scanner The scanner for user input.
     */
    private static void handleSalesMaxPrice(ClientLibrary client, Scanner scanner) {
        System.out.println("Enter product name:");
        String productName = scanner.nextLine();

        int days = 0;
        while (true) {
            System.out.println("Enter number of days to aggregate:");
            String daysInput = scanner.nextLine();
            try {
                days = Integer.parseInt(daysInput.trim());
                if (days < 1) {
                    System.out.println("Number of days must be at least 1.");
                    continue;
                }
                break;
            } catch (NumberFormatException e) {
                System.out.println("Invalid input! Please enter a valid integer.");
            }
        }

        try {
            double maxPrice = client.getSalesMaxPrice(productName, days);
            System.out.println("Maximum price for '" + productName + "' in the last " + days + " days: " + maxPrice);
        } catch (IOException e) {
            System.out.println("Error getting max price: " + e.getMessage());
        }
    }

    /**
     * The main entry point for the ClientUI application.
     * Sets up the interface responsible to interact with the server.
     * Allows operations such as user authentication, registration of events, listing sales, etc
     */
     public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            ClientLibrary client;
            try {
                client = new ClientLibrary("localhost", 12345);
            } catch (IOException e) {
                System.out.println(e.getMessage());
                return; // Termina o programa sem stack trace
            }

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

            // Handle authentication/registration
            boolean authenticated = handleAuthentication(client, scanner, choice);

            // If authentication is successful, proceed with operations
            if (authenticated) {
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
                        System.out.println("6. End Day");
                        System.out.println("7. Shutdown Server");
                        System.out.println("8. Exit");

                        try {
                            operation = scanner.nextInt();
                            scanner.nextLine();
                            if (operation >= 1 && operation <= 8) {
                                validChoice = true;
                            } else {
                                System.out.println("Invalid operation! Please choose a valid operation.");
                            }
                        } catch (InputMismatchException e) {
                            System.out.println("Invalid input! Please enter a valid number between 1 and 8.");
                            scanner.nextLine();
                        }
                    }

                    switch(operation){
                        case 1:
                            handleAddSale(client, scanner);
                            break;

                        case 2: 
                            handleSalesQuantity(client, scanner);
                            break;

                        case 3: 
                            handleSalesVolume(client, scanner);
                            break;

                        case 4: 
                            handleSalesAverage(client, scanner);
                            break;

                        case 5: 
                            handleSalesMaxPrice(client, scanner);
                            break;

                        case 6: // End Day operation
                            try {
                                String result = client.endDay();
                                System.out.println(result);
                            } catch (IOException e) {
                                System.out.println("Error ending day: " + e.getMessage());
                            }
                            break;

                        case 7: // Shutdown Server
                            System.out.println("Are you sure you want to shutdown the server? (y/n)");
                            String confirm = scanner.nextLine().trim().toLowerCase();
                            if (confirm.equals("y") || confirm.equals("yes")) {
                                try {
                                    String result = client.shutdown();
                                    System.out.println("Server response: " + result);
                                    running = false;
                                } catch (IOException e) {
                                    System.out.println("Server shutdown initiated.");
                                    running = false;
                                }
                            } else {
                                System.out.println("Shutdown cancelled.");
                            }
                            break;

                        case 8: // Exit
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
                                client.close();
                            } catch (IOException e) {
                                System.out.println("Error during disconnect: " + e.getMessage());
                            }
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