package org.Client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.locks.ReentrantLock;
import org.Common.IAmazUM;

public class ClientUI {
    
    private static final ReentrantLock consoleLock = new ReentrantLock();
    private final IAmazUM client;

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
     * Handles user authentication or registration (synchronous).
     *
     * @param client The client library instance.
     * @param scanner The scanner for user input.
     * @param choice 1 for login, 2 for register.
     * @return true if authentication/registration was successful, false otherwise.
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
        printSafe("Enter product name:");
        String productName = scanner.nextLine();
        
        int quantity = 0;
        while (true) {
            printSafe("Enter quantity:");
            String quantityInput = scanner.nextLine();
            try {
                quantity = Integer.parseInt(quantityInput.trim());
                break;
            } catch (NumberFormatException e) {
                printSafe("Invalid quantity! Please enter a valid integer.");
            }
        }
        
        double price = 0.0;
        while (true) {
            printSafe("Enter price:");
            String priceInput = scanner.nextLine();
            try {
                price = Double.parseDouble(priceInput.trim().replace(",", "."));
                break;
            } catch (NumberFormatException e) {
                printSafe("Invalid price! Please enter a valid number (e.g., 2.45).");
            }
        }

        final int finalQuantity = quantity;
        final double finalPrice = price;
        
        Thread t = new Thread(() -> {
            try {
                boolean saleAdded = client.addSale(productName, finalQuantity, finalPrice);
                if (saleAdded) {
                    printSafe("[Response] Sale added successfully for " + productName + "!");
                } else {
                    printSafe("[Response] Failed to add sale for " + productName + ".");
                }
            } catch (IOException e) {
                printSafe("[Response] Error adding sale: " + e.getMessage());
            }
        });
        
        threads.add(t);
        t.start();
    }

    /**
     * Handles the sales average price query in a separate thread.
     */
    private static void handleSalesAverage(IAmazUM client, Scanner scanner, List<Thread> threads) {
        printSafe("Enter product name:");
        String productName = scanner.nextLine();
        
        int days = getDaysInput(scanner);
        
        Thread t = new Thread(() -> {
            try {
                double avgPrice = client.getSalesAveragePrice(productName, days);
                printSafe("[Response] Average price for '" + productName + "' in the last " + days + " days: " + avgPrice);
            } catch (IOException e) {
                printSafe("[Response] Error getting average price: " + e.getMessage());
            }
        });
        
        threads.add(t);
        t.start();
    }

    /**
     * Handles the sales quantity query in a separate thread.
     */
    private static void handleSalesQuantity(IAmazUM client, Scanner scanner, List<Thread> threads) {
        printSafe("Enter product name:");
        String productName = scanner.nextLine();
        
        int days = getDaysInput(scanner);
        
        Thread t = new Thread(() -> {
            try {
                int quantity = client.getSalesQuantity(productName, days);
                printSafe("[Response] Total quantity sold for '" + productName + "' in the last " + days + " days: " + quantity);
            } catch (IOException e) {
                printSafe("[Response] Error getting sales quantity: " + e.getMessage());
            }
        });
        
        threads.add(t);
        t.start();
    }

    /**
     * Handles the sales volume query in a separate thread.
     */
    private static void handleSalesVolume(IAmazUM client, Scanner scanner, List<Thread> threads) {
        printSafe("Enter product name:");
        String productName = scanner.nextLine();
        
        int days = getDaysInput(scanner);
        
        Thread t = new Thread(() -> {
            try {
                double volume = client.getSalesVolume(productName, days);
                printSafe("[Response] Total sales volume for '" + productName + "' in the last " + days + " days: " + volume);
            } catch (IOException e) {
                printSafe("[Response] Error getting sales volume: " + e.getMessage());
            }
        });
        
        threads.add(t);
        t.start();
    }

    /**
     * Handles the sales max price query in a separate thread.
     */
    private static void handleSalesMaxPrice(IAmazUM client, Scanner scanner, List<Thread> threads) {
        printSafe("Enter product name:");
        String productName = scanner.nextLine();
        
        int days = getDaysInput(scanner);
        
        Thread t = new Thread(() -> {
            try {
                double maxPrice = client.getSalesMaxPrice(productName, days);
                printSafe("[Response] Maximum price for '" + productName + "' in the last " + days + " days: " + maxPrice);
            } catch (IOException e) {
                printSafe("[Response] Error getting max price: " + e.getMessage());
            }
        });
        
        threads.add(t);
        t.start();
    }

    /**
     * Handles the end day operation in a separate thread.
     */
    private static void handleEndDay(IAmazUM client, List<Thread> threads) {
        Thread t = new Thread(() -> {
            try {
                String result = client.endDay();
                printSafe("[Response] " + result);
            } catch (IOException e) {
                printSafe("[Response] Error ending day: " + e.getMessage());
            }
        });
        
        threads.add(t);
        t.start();
    }

    /**
     * Handles the wait for simultaneous sales notification.
     */
    private static void handleWaitForSimultaneousSales(IAmazUM client, Scanner scanner, List<Thread> threads) {
        printSafe("Enter first product name:");
        String p1 = scanner.nextLine();
        printSafe("Enter second product name:");
        String p2 = scanner.nextLine();
        
        Thread t = new Thread(() -> {
            try {
                boolean result = client.waitForSimultaneousSales(p1, p2);
                if (result) {
                    printSafe("\n[Notification] Simultaneous sales detected for " + p1 + " and " + p2 + "!");
                } else {
                    printSafe("\n[Notification] Did not detect simultaneous sales for " + p1 + " and " + p2 + ".");
                }
            } catch (Exception e) {
                printSafe("\n[Notification] Error waiting for simultaneous sales: " + e.getMessage());
            }
        });
        
        threads.add(t);
        t.start();
        printSafe("Notification request submitted in background. You can continue using the menu.");
    }

    /**
     * Handles the wait for consecutive sales notification.
     */
    private static void handleWaitForConsecutiveSales(IAmazUM client, Scanner scanner, List<Thread> threads) {
        int n = 0;
        while (true) {
            printSafe("Enter number of consecutive sales:");
            String input = scanner.nextLine();
            try {
                n = Integer.parseInt(input.trim());
                if (n < 1) {
                    printSafe("Number must be at least 1.");
                    continue;
                }
                break;
            } catch (NumberFormatException e) {
                printSafe("Invalid number!");
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
            } catch (Exception e) {
                printSafe("\n[Notification] Error waiting for consecutive sales: " + e.getMessage());
            }
        });
        
        threads.add(t);
        t.start();
        printSafe("Notification request submitted in background. You can continue using the menu.");
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
     */
    public void start() {
        try (Scanner scanner = new Scanner(System.in)) {

            int choice = 0;
            boolean validChoice = false;

            // Display initial menu for user choice: register, login, or exit
            while (!validChoice) {
                printSafe("\n========== SALES MANAGEMENT SYSTEM ==========");
                printSafe("Choose an option: ");
                printSafe("1. Log in ");
                printSafe("2. Register new account");
                printSafe("3. Exit");
                printSafe("=============================================");
                try {
                    choice = scanner.nextInt();
                    scanner.nextLine();
                    if (choice == 1 || choice == 2 || choice == 3) {
                        validChoice = true;
                    } else {
                        printSafe("Invalid option! Please choose 1, 2, or 3.");
                    }
                } catch (InputMismatchException e) {
                    printSafe("Invalid input! Please enter a number (1, 2 or 3).");
                    scanner.nextLine();
                }
            }

            // If the user chooses to exit, close the client connection and exit
            if (choice == 3) {
                client.disconnect();
                return;
            }

            // Handle authentication/registration
            boolean authenticated = handleAuthentication(client, scanner, choice);

            // If authentication is successful, proceed with operations
            if (authenticated) {
                printSafe("\n✓ Authentication successful! Welcome to the system.");
                boolean running = true;
                List<Thread> threads = new ArrayList<>();

                while (running) {
                    int operation = 0;
                    validChoice = false;
                    
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
                            "10. Exit Application\n" +
                            "=============================================\n" +
                            "Select operation (1-10): ";
                            printSafe(menu);
                        
                        try {
                            operation = scanner.nextInt();
                            scanner.nextLine();
                            if (operation >= 1 && operation <= 10) {
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
            } else {
                printSafe("\n✗ Authentication failed! Please check your credentials.");
                client.disconnect();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}