package org.Server;

import java.util.HashMap;
import java.util.Map;
import org.ProductCatalog;
import org.Venda;


class ServerDatabase {
    private ProductCatalog productCatalog;
    private Map<Integer, Map<Integer, Venda>> ordersByDay;

    public ServerDatabase() {
        this.productCatalog = new ProductCatalog();
        this.ordersByDay = new HashMap<>();
    }

    public ProductCatalog getProductCatalog() {
        return productCatalog;
    }

    public Map<Integer, Map<Integer, Venda>> getOrdersByDay() {
        return ordersByDay;
    }

    public void addOrder(int day, int orderId, Venda order) {
        ordersByDay.putIfAbsent(day, new HashMap<>());
        ordersByDay.get(day).put(orderId, order);
    }
}
