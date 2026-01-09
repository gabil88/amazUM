package org.Client;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.locks.ReentrantLock;

import org.Common.FilteredEvents;
import org.Common.NetworkException;
import org.Common.IAmazUM;

public class ClientUI {
    
    private static final ReentrantLock consoleLock = new ReentrantLock();
    private final IAmazUM client;
    private static final String ERROR_PREFIX = "[ERRO] ";
    private static final String INFO_PREFIX = "[INFO] ";
    private static final String RESPONSE_PREFIX = "[Response] ";

    public ClientUI(IAmazUM client) {
        this.client = client;
    }
    
    /**
     * Imprime mensagem no console de forma thread-safe.
     */
    private static void printSafe(String message) {
        consoleLock.lock();
        try {
            System.out.println(message);
        } finally {
            consoleLock.unlock();
        }
    }
    
    /**
     * Imprime mensagem de erro de forma consistente.
     */
    private static void printError(String message) {
        printSafe(ERROR_PREFIX + message);
    }
    
    /**
     * Imprime mensagem informativa de forma consistente.
     */
    private static void printInfo(String message) {
        printSafe(INFO_PREFIX + message);
    }

    /**
     * Trata exceções de rede com mensagens apropriadas.
     */
    private static void handleNetworkError(IOException e) {
        if (e instanceof SocketTimeoutException) {
            printError("Timeout: O servidor demorou muito a responder. Tente novamente.");
        } else if (e instanceof NetworkException) {
            printError("Erro de rede: " + e.getMessage());
        } else if (e.getMessage() != null && e.getMessage().contains("Connection reset")) {
            printError("Conexão perdida com o servidor. Por favor, reinicie a aplicação.");
        } else if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
            printError("Não foi possível contactar o servidor. Verifique se está a correr.");
        } else {
            printError("Erro de comunicação: " + (e.getMessage() != null ? e.getMessage() : "Conexão interrompida"));
        }
    }

    /**
     * Handles user authentication or registration (synchronous).
     *
     * @param client The client library instance.
     * @param scanner The scanner for user input.
     * @param choice 1 for login, 2 for register.
     * @return the username when authentication/registration succeeded, or null on failure.
     */
    private static boolean handleAuthentication(IAmazUM client, Scanner scanner, int choice) {
        printSafe("Enter your username: ");
        String username = scanner.nextLine();
        printSafe("Enter your password: ");
        String password = scanner.nextLine();

        try {
            if (choice == 1) {
                return client.authenticate(username, password);
            } else {
                return client.register(username, password);
            }
        } catch (IOException e) {
            printSafe("Error during " + (choice == 1 ? "authentication" : "registration") + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Handles the add sale operation in a separate thread.
     *
     * @param client The client library instance.
     * @param scanner The scanner for user input.
     * @param threads The list of active threads to track.
     */
    private static void handleAddSale(IAmazUM client, Scanner scanner, List<Thread> threads) {
        try {
            printSafe("Enter product name:");
            String productName = scanner.nextLine().trim();
            
            if (productName.isEmpty()) {
                printError("Product name cannot be empty.");
                return;
            }
            
            int quantity = 0;
            while (true) {
                printSafe("Enter quantity:");
                String quantityInput = scanner.nextLine();
                try {
                    quantity = Integer.parseInt(quantityInput.trim());
                    if (quantity <= 0) {
                        printError("Quantity must be greater than 0.");
                        continue;
                    }
                    break;
                } catch (NumberFormatException e) {
                    printError("Invalid quantity! Please enter a valid positive integer.");
                }
            }
            
            double price = 0.0;
            while (true) {
                printSafe("Enter price:");
                String priceInput = scanner.nextLine();
                try {
                    price = Double.parseDouble(priceInput.trim().replace(",", "."));
                    if (price <= 0) {
                        printError("Price must be greater than 0.");
                        continue;
                    }
                    break;
                } catch (NumberFormatException e) {
                    printError("Invalid price! Please enter a valid number (e.g., 2.45).");
                }
            }

            final int finalQuantity = quantity;
            final double finalPrice = price;
            final String finalProductName = productName;
            
            Thread t = new Thread(() -> {
                try {
                    boolean saleAdded = client.addSale(finalProductName, finalQuantity, finalPrice);
                    if (saleAdded) {
                        printSafe(RESPONSE_PREFIX + "Sale added successfully for " + finalProductName + "!");
                    } else {
                        printSafe(RESPONSE_PREFIX + "Failed to add sale for " + finalProductName + ".");
                    }
                } catch (IOException e) {
                    handleNetworkError(e);
                } catch (Exception e) {
                    printError("Unexpected error adding sale: " + e.getMessage());
                }
            });
            
            threads.add(t);
            t.start();
        } catch (Exception e) {
            printError("Error preparing sale operation: " + e.getMessage());
        }
    }

    /**
     * Handles the sales average price query in a separate thread.
     */
    private static void handleSalesAverage(IAmazUM client, Scanner scanner, List<Thread> threads) {
        try {
            printSafe("Enter product name:");
            String productName = scanner.nextLine().trim();
            
            if (productName.isEmpty()) {
                printError("Product name cannot be empty.");
                return;
            }
            
            int days = getDaysInput(scanner);
            
            Thread t = new Thread(() -> {
                try {
                    double avgPrice = client.getSalesAveragePrice(productName, days);
                    printSafe(RESPONSE_PREFIX + "Average price for '" + productName + "' in the last " + days + " days: " + String.format("%.2f", avgPrice));
                } catch (IOException e) {
                    handleNetworkError(e);
                } catch (Exception e) {
                    printError("Unexpected error getting average price: " + e.getMessage());
                }
            });
            
            threads.add(t);
            t.start();
        } catch (Exception e) {
            printError("Error preparing average price query: " + e.getMessage());
        }
    }

    /**
     * Handles the sales quantity query in a separate thread.
     */
    private static void handleSalesQuantity(IAmazUM client, Scanner scanner, List<Thread> threads) {
        try {
            printSafe("Enter product name:");
            String productName = scanner.nextLine().trim();
            
            if (productName.isEmpty()) {
                printError("Product name cannot be empty.");
                return;
            }
            
            int days = getDaysInput(scanner);
            
            Thread t = new Thread(() -> {
                try {
                    int quantity = client.getSalesQuantity(productName, days);
                    printSafe(RESPONSE_PREFIX + "Total quantity sold for '" + productName + "' in the last " + days + " days: " + quantity);
                } catch (IOException e) {
                    handleNetworkError(e);
                } catch (Exception e) {
                    printError("Unexpected error getting sales quantity: " + e.getMessage());
                }
            });
            
            threads.add(t);
            t.start();
        } catch (Exception e) {
            printError("Error preparing quantity query: " + e.getMessage());
        }
    }

    /**
     * Handles the sales volume query in a separate thread.
     */
    private static void handleSalesVolume(IAmazUM client, Scanner scanner, List<Thread> threads) {
        try {
            printSafe("Enter product name:");
            String productName = scanner.nextLine().trim();
            
            if (productName.isEmpty()) {
                printError("Product name cannot be empty.");
                return;
            }
            
            int days = getDaysInput(scanner);
            
            Thread t = new Thread(() -> {
                try {
                    double volume = client.getSalesVolume(productName, days);
                    printSafe(RESPONSE_PREFIX + "Total sales volume for '" + productName + "' in the last " + days + " days: " + String.format("%.2f", volume));
                } catch (IOException e) {
                    handleNetworkError(e);
                } catch (Exception e) {
                    printError("Unexpected error getting sales volume: " + e.getMessage());
                }
            });
            
            threads.add(t);
            t.start();
        } catch (Exception e) {
            printError("Error preparing volume query: " + e.getMessage());
        }
    }

    /**
     * Handles the sales max price query in a separate thread.
     */
    private static void handleSalesMaxPrice(IAmazUM client, Scanner scanner, List<Thread> threads) {
        try {
            printSafe("Enter product name:");
            String productName = scanner.nextLine().trim();
            
            if (productName.isEmpty()) {
                printError("Product name cannot be empty.");
                return;
            }
            
            int days = getDaysInput(scanner);
            
            Thread t = new Thread(() -> {
                try {
                    double maxPrice = client.getSalesMaxPrice(productName, days);
                    printSafe(RESPONSE_PREFIX + "Maximum price for '" + productName + "' in the last " + days + " days: " + String.format("%.2f", maxPrice));
                } catch (IOException e) {
                    handleNetworkError(e);
                } catch (Exception e) {
                    printError("Unexpected error getting max price: " + e.getMessage());
                }
            });
            
            threads.add(t);
            t.start();
        } catch (Exception e) {
            printError("Error preparing max price query: " + e.getMessage());
        }
    }

    /**
     * Handles the end day operation in a separate thread.
     */
    private static void handleEndDay(IAmazUM client, List<Thread> threads) {
        Thread t = new Thread(() -> {
            try {
                String result = client.endDay();
                printSafe(RESPONSE_PREFIX + result);
            } catch (IOException e) {
                handleNetworkError(e);
            } catch (Exception e) {
                printError("Unexpected error ending day: " + e.getMessage());
            }
        });
        
        threads.add(t);
        t.start();
    }

    /**
     * Handles the wait for simultaneous sales notification.
     */
    private static void handleWaitForSimultaneousSales(IAmazUM client, Scanner scanner, List<Thread> threads) {
        try {
            printSafe("Enter first product name:");
            String p1 = scanner.nextLine().trim();
            printSafe("Enter second product name:");
            String p2 = scanner.nextLine().trim();
            
            if (p1.isEmpty() || p2.isEmpty()) {
                printError("Product names cannot be empty.");
                return;
            }
            
            Thread t = new Thread(() -> {
                try {
                    boolean result = client.waitForSimultaneousSales(p1, p2);
                    if (result) {
                        printSafe("\n[Notification] Simultaneous sales detected for " + p1 + " and " + p2 + "!");
                    } else {
                        printSafe("\n[Notification] Did not detect simultaneous sales for " + p1 + " and " + p2 + ".");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    printInfo("Notification monitoring interrupted.");
                } catch (IOException e) {
                    handleNetworkError(e);
                } catch (Exception e) {
                    printError("Unexpected error waiting for simultaneous sales: " + e.getMessage());
                }
            });
            
            threads.add(t);
            t.start();
            printInfo("Notification request submitted in background. You can continue using the menu.");
        } catch (Exception e) {
            printError("Error preparing notification request: " + e.getMessage());
        }
    }

    /**
     * Handles the wait for consecutive sales notification.
     */
    private static void handleWaitForConsecutiveSales(IAmazUM client, Scanner scanner, List<Thread> threads) {
        try {
            int n = 0;
            while (true) {
                printSafe("Enter number of consecutive sales:");
                String input = scanner.nextLine();
                try {
                    n = Integer.parseInt(input.trim());
                    if (n < 1) {
                        printError("Number must be at least 1.");
                        continue;
                    }
                    break;
                } catch (NumberFormatException e) {
                    printError("Invalid number! Please enter a positive integer.");
                }
            }
            
            final int count = n;
            Thread t = new Thread(() -> {
                try {
                    String product = client.waitForConsecutiveSales(count);
                    if (product != null) {
                        printSafe("\n[Notification] " + count + " consecutive sales detected for product: " + product + "!");
                    } else {
                        printSafe("\n[Notification] Did not detect " + count + " consecutive sales.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    printInfo("Notification monitoring interrupted.");
                } catch (IOException e) {
                    handleNetworkError(e);
                } catch (Exception e) {
                    printError("Unexpected error waiting for consecutive sales: " + e.getMessage());
                }
            });
            
            threads.add(t);
            t.start();
            printInfo("Notification request submitted in background. You can continue using the menu.");
        } catch (Exception e) {
            printError("Error preparing notification request: " + e.getMessage());
        }
    }

    /**
     * Handles the filter events in a separated thread.
     */
    private static void handleFilterEvents(IAmazUM client, Scanner scanner, List<Thread> threads) {
        try {
            List<String> productsInput;

            while (true) {
                printSafe("Enter product names (comma-separated):");
                String line = scanner.nextLine();

                productsInput = Arrays.stream(line.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();

                if (!productsInput.isEmpty())
                    break;
                
                printError("Products list cannot be empty. Please enter at least one product.");
            }

            final List<String> products = productsInput;

            int days = getDaysInput(scanner);

        Thread t = new Thread(() -> {
            try {
                FilteredEvents fe = client.filterEvents(products, days);

                    if (fe.getEventsByProduct().isEmpty()) {
                        printSafe(RESPONSE_PREFIX + "No events found.");
                        return;
                    }

                Map<Integer, String> dict = fe.getDictionaryUpdate();

                if (!dict.isEmpty()) {
                    printSafe("[Info] Received dictionary update with " + dict.size() + " entries.");
                } else {
                    printSafe("[Info] No dictionary update received from server.");
                }

                for (var entry : fe.getEventsByProduct().entrySet()) {
                    Integer productId = entry.getKey();
                    List<FilteredEvents.Event> events = entry.getValue();

                    if (events.isEmpty()) continue;

                    String productName = null;
                    if (dict != null) {
                        productName = dict.get(productId);
                        if (productName != null) {
                            //printSafe("[Info] Product id " + productId + " name obtained from dictionary: " + productName);
                        }
                    }
                    if (productName == null) {
                        productName = client.getProductName(productId);
                        //printSafe("[Info] Product id " + productId + " name fetched from server: " + productName);
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("Product: ").append(productName).append(" | ");

                    for (FilteredEvents.Event e : events) {
                        sb.append("Qty: ")
                        .append(e.quantity)
                        .append(", Price: ")
                        .append(String.format("%.2f", e.price))
                        .append(" | ");
                    }

                        printSafe(sb.toString());
                    }

                } catch (IOException e) {
                    handleNetworkError(e);
                } catch (Exception e) {
                    printError("Unexpected error filtering events: " + e.getMessage());
                }
            });

            threads.add(t);
            t.start();
        } catch (Exception e) {
            printError("Error preparing filter events operation: " + e.getMessage());
        }
    }
    
    /**
     * Helper method to get days input with validation.
     */
    private static int getDaysInput(Scanner scanner) {
        int days = 0;
        while (true) {
            printSafe("Enter number of days to aggregate:");
            String daysInput = scanner.nextLine();
            try {
                days = Integer.parseInt(daysInput.trim());
                if (days < 1) {
                    printSafe("Number of days must be at least 1.");
                    continue;
                }
                break;
            } catch (NumberFormatException e) {
                printSafe("Invalid input! Please enter a valid integer.");
            }
        }
        return days;
    }

    /**
     * The main entry point for the ClientUI application.
     * This method is crash-proof and handles all exceptions gracefully.
     */
    public void start() {
        Scanner scanner = null;
        try {
            scanner = new Scanner(System.in);

            int choice = 0;
            boolean validChoice = false;

            // Display initial menu for user choice: register, login, or exit
            while (!validChoice) {
                try {
                    printSafe("\n========== SALES MANAGEMENT SYSTEM ==========");
                    printSafe("Choose an option: ");
                    printSafe("1. Log in ");
                    printSafe("2. Register new account");
                    printSafe("3. Exit");
                    printSafe("=============================================");
                    
                    choice = scanner.nextInt();
                    scanner.nextLine(); // Consume newline
                    
                    if (choice == 1 || choice == 2 || choice == 3) {
                        validChoice = true;
                    } else {
                        printError("Invalid option! Please choose 1, 2, or 3.");
                    }
                } catch (InputMismatchException e) {
                    printError("Invalid input! Please enter a number (1, 2 or 3).");
                    scanner.nextLine(); // Clear invalid input
                } catch (Exception e) {
                    printError("Unexpected error reading input: " + e.getMessage());
                    scanner.nextLine(); // Clear input buffer
                }
            }

            // If the user chooses to exit, close the client connection and exit
            if (choice == 3) {
                try {
                    client.disconnect();
                    printInfo("Disconnected successfully. Goodbye!");
                } catch (IOException e) {
                    printError("Error during disconnect: " + e.getMessage());
                }
                return;
            }

            // Handle authentication/registration
            boolean authenticated = handleAuthentication(client, scanner, choice);

            // If authentication is successful, proceed with operations
            if (authenticated) {
                printSafe("\n✓ Authentication successful! Welcome to the system.");
                runMainMenu(scanner);
            } else {
                printError("Authentication failed! Please check your credentials.");
                try {
                    client.disconnect();
                } catch (IOException e) {
                    printError("Error during disconnect: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            printError("Critical error in application: " + e.getMessage());
            e.printStackTrace(System.err); // Log stack trace for debugging
        } finally {
            if (scanner != null) {
                try {
                    scanner.close();
                } catch (Exception e) {
                    // Ignore scanner close errors
                }
            }
        }
    }

    /**
     * Runs the main operations menu. Separated for better error handling.
     */
    private void runMainMenu(Scanner scanner) {
        boolean running = true;
        List<Thread> threads = new ArrayList<>();

        try {
            while (running) {
                    int operation = 0;
                    boolean validChoice = false;
                    
                    while (!validChoice) {
                        String menu = 
                            "\n============== OPERATIONS MENU ==============\n" +
                            "1.  Register a Sale - Add a new sale record\n" +
                            "2.  Total Units Sold - Query quantity sold for a product\n" +
                            "3.  Total Revenue - Calculate sales volume (quantity × price)\n" +
                            "4.  Average Sale Price - Get mean price for a product\n" +
                            "5.  Maximum Sale Price - Find highest price for a product\n" +
                            "6.  End Current Day - Close day and process notifications\n" +
                            "7.  Shutdown Server - Terminate server (admin only)\n" +
                            "8.  Monitor Simultaneous Sales - Alert when 2 products sell together\n" +
                            "9.  Monitor Consecutive Sales - Alert when N sales happen in a row\n" +
                            "10. Filter Events (Time Series)\n" +
                            "11. Exit Application\n" +
                            "=============================================\n" +
                            "Select operation (1-11): ";
                            printSafe(menu);
                        
                        try {
                            operation = scanner.nextInt();
                            scanner.nextLine();
                            if (operation >= 1 && operation <= 11) {
                                validChoice = true;
                            } else {
                                printSafe("Invalid operation! Please choose between 1 and 10.");
                            }
                        } catch (InputMismatchException e) {
                            printSafe("Invalid input! Please enter a number between 1 and 10.");
                            scanner.nextLine();
                        }
                    }

                    switch(operation) {
                        case 1:
                            handleAddSale(client, scanner, threads);
                            break;
                        case 2:
                            handleSalesQuantity(client, scanner, threads);
                            break;
                        case 3:
                            handleSalesVolume(client, scanner, threads);
                            break;
                        case 4:
                            handleSalesAverage(client, scanner, threads);
                            break;
                        case 5:
                            handleSalesMaxPrice(client, scanner, threads);
                            break;
                        case 6:
                            handleEndDay(client, threads);
                            break;
                        case 7:
                            printSafe("Are you sure you want to shutdown the server? (y/n)");
                            String confirm = scanner.nextLine().trim().toLowerCase();
                            if (confirm.equals("y") || confirm.equals("yes")) {
                                Thread shutdownThread = new Thread(() -> {
                                    try {
                                        String result = client.shutdown();
                                        printSafe("[Response] Server response: " + result);
                                    } catch (IOException e) {
                                        printSafe("[Response] Server shutdown initiated.");
                                    }
                                });
                                threads.add(shutdownThread);
                                shutdownThread.start();
                                printSafe("Shutdown request sent. Exiting application...");
                                running = false;
                            } else {
                                printSafe("Shutdown cancelled.");
                            }
                            break;
                        case 8:
                            handleWaitForSimultaneousSales(client, scanner, threads);
                            break;
                        case 9:
                            handleWaitForConsecutiveSales(client, scanner, threads);
                            break;
                        case 10:
                            handleFilterEvents(client, scanner, threads);
                            break;
                        case 11:
                            running = false;
                            printSafe("\nWaiting for pending operations to complete...");
                            for (Thread t : threads) {
                                try {
                                    t.join();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    printSafe("Thread interrupted.");
                                }
                            }
                            try {
                                client.disconnect();
                                printSafe("Disconnected successfully. Goodbye!");
                            } catch (IOException e) {
                                printSafe("Error during disconnect: " + e.getMessage());
                            }
                            break;
                        default:
                            printSafe("Invalid operation!");
                            break;
                    }
                }
        } catch (Exception e) {
            printError("Error executing operation: " + e.getMessage());
            // Continue running, don't crash
        }
    }
}