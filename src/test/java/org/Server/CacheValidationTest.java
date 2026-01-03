package org.Server;

import java.util.*;
import java.io.*;

public class CacheValidationTest {

    public static void main(String[] args) {
        System.out.println("=== Starting Cache and Handlers Verification ===");

        boolean allPassed = true;
        allPassed &= testCacheLRU();
        allPassed &= testHandlersAggregation();

        allPassed &= testUserSpecificScenario();

        if (allPassed) {
            System.out.println("\n✅ ALL TESTS PASSED");
        } else {
            System.out.println("\n❌ SOME TESTS FAILED");
            System.exit(1);
        }
    }

    /**
     * Test specific user scenario:
     * "when calculating the last 2 days if Im on day 3 it is day 2 and 1"
     */
    private static boolean testUserSpecificScenario() {
        System.out.println("\nTest: User Scenario (Day 3 -> Last 2 Days)");
        try {
            StubServerDatabase db = new StubServerDatabase();
            // Force current day to be 3
            db.forceCurrentDay(3);

            Cache cache = new Cache(10);
            Handlers handlers = new Handlers(db, cache);

            String product = "Banana";
            int prodId = db.getProductId(product);

            // Populate Day 2 (Yesterday)
            db.addMockData(2, prodId, new Venda(prodId, 10, 100.0));

            // Populate Day 1 (Day before Yesterday)
            db.addMockData(1, prodId, new Venda(prodId, 5, 50.0));

            // Populate Day 0 (Too old)
            db.addMockData(0, prodId, new Venda(prodId, 999, 9999.0));

            // Populate Day 3 (Current Day - should NOT be included)
            db.addMockData(3, prodId, new Venda(prodId, 1, 10.0));

            // Query: Last 2 days
            // Should aggregate Day 2 and Day 1.
            // Expected Quantity: 10 (Day 2) + 5 (Day 1) = 15.
            int totalQty = handlers.getTotalQuantitySold(product, 2);

            if (totalQty == 15) {
                System.out.println("✅ Correctly aggregated Day 2 and Day 1 (Total: 15). Ignored Day 0 and Day 3.");
                return true;
            } else {
                System.out.println("❌ FAILED: Expected 15, got " + totalQty);
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Test Cache LRU (Least Recently Used) Eviction Policy
     * Capacity: 3
     * Insert A, B, C. Cache: [C, B, A]
     * Access A. Cache: [A, C, B]
     * Insert D. Evict B (Tail). Cache: [D, A, C]
     */
    private static boolean testCacheLRU() {
        System.out.println("\nTest: Cache LRU Eviction");
        try {
            Cache cache = new Cache(3);

            // 1. Fill Cache
            cache.setQuantidade(1, "ProdA", 10);
            cache.setQuantidade(1, "ProdB", 20);
            cache.setQuantidade(1, "ProdC", 30);

            // 2. Access ProdA (moves to head)
            Integer valA = cache.getQuantidade(1, "ProdA");
            assert valA != null && valA == 10 : "Failed to retrieve ProdA";

            // 3. Insert ProdD (should evict ProdB which is now at tail)
            cache.setQuantidade(1, "ProdD", 40);

            // 4. Verify Content
            if (cache.getQuantidade(1, "ProdB") != null) {
                System.out.println("❌ FAILED: ProdB should have been evicted");
                return false;
            }

            if (cache.getQuantidade(1, "ProdA") == null) {
                System.out.println("❌ FAILED: ProdA should still be in cache (was accessed recently)");
                return false;
            }

            if (cache.getQuantidade(1, "ProdC") == null) {
                System.out.println("❌ FAILED: ProdC should still be in cache");
                return false;
            }

            if (cache.getQuantidade(1, "ProdD") == null) {
                System.out.println("❌ FAILED: ProdD should be in cache");
                return false;
            }

            System.out.println("✅ Passed");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Test Handlers Logic
     * Verifies that Handlers correctly aggregates data from multiple days
     * and uses the cache correctly.
     */
    private static boolean testHandlersAggregation() {
        System.out.println("\nTest: Handlers Aggregation & Caching");
        try {
            StubServerDatabase db = new StubServerDatabase();
            Cache cache = new Cache(10);
            Handlers handlers = new Handlers(db, cache);

            String product = "Apple";
            int prodId = db.getProductId(product);

            // Setup Mock Data
            // Current Day is 10.
            // Day 9: 2 sales of 5 units @ 10.0 each (Total Qty: 10, Total Vol: 20.0)
            // Day 8: 1 sale of 10 units @ 50.0 (Total Qty: 10, Total Vol: 50.0)

            db.addMockData(9, prodId, new Venda(prodId, 5, 10.0));
            db.addMockData(9, prodId, new Venda(prodId, 5, 10.0));

            db.addMockData(8, prodId, new Venda(prodId, 10, 50.0));

            // Test 1: Total Quantity Sold (Last 2 days: 9 and 8)
            // Expected: 10 (Day 9) + 10 (Day 8) = 20
            int totalQty = handlers.getTotalQuantitySold(product, 2);
            if (totalQty != 20) {
                System.out.println("❌ FAILED: Total Quantity. Expected 20, got " + totalQty);
                return false;
            }

            // Test 2: Check Cache was populated
            // Handlers should have populated cache for Day 9 and Day 8
            if (cache.getQuantidade(9, product) == null || cache.getQuantidade(8, product) == null) {
                System.out.println("❌ FAILED: Cache not populated after query");
                return false;
            }

            // Test 3: Total Sales Volume
            // Expected: 20.0 (Day 9) + 50.0 (Day 8) = 70.0
            double totalVol = handlers.getTotalSalesVolume(product, 2);
            if (Math.abs(totalVol - 70.0) > 0.001) {
                System.out.println("❌ FAILED: Total Volume. Expected 70.0, got " + totalVol);
                return false;
            }

            // Test 4: Verify Cache Hit (Manually modify cache to see if Handlers uses it)
            // We set Day 9 cached quantity to 999. Handlers should return 999 + 10 = 1009
            cache.setQuantidade(9, product, 999);
            int cachedQty = handlers.getTotalQuantitySold(product, 2);
            if (cachedQty != 1009) {
                System.out.println("❌ FAILED: Cache Hit verification. Expected 1009, got " + cachedQty);
                return false;
            }

            System.out.println("✅ Passed");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- Stub Helper Classes ---

    static class StubServerDatabase extends ServerDatabase {
        private Map<Integer, Map<Integer, List<Venda>>> mockData = new HashMap<>();

        public StubServerDatabase() {
            // Bypass normal loading - relies on PersistenceManager handling missing files
            // gracefully
            super();
        }

        @Override
        public Map<Integer, List<Venda>> getDayData(int day) {
            return mockData.getOrDefault(day, new HashMap<>());
        }

        public void addMockData(int day, int productId, Venda venda) {
            mockData.computeIfAbsent(day, k -> new HashMap<>())
                    .computeIfAbsent(productId, k -> new ArrayList<>())
                    .add(venda);
        }

        @Override
        public int getProductId(String productName) {
            return Math.abs(productName.hashCode());
        }

        @Override
        public int getCurrentDay() {
            return 10; // Fixed day for testing
        }
    }
}
