package org.Common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents filtered sales events grouped by product.
 *
 * Format (binary):
 *  int numProducts
 *    UTF productName
 *    int numEvents
 *      int quantity
 *      double price
 */
public class FilteredEvents {

    /**
     * Map containing for each product its list of events object, containing its quantity and price
     */
    private final Map<String, List<Event>> eventsByProduct;

    public static class Event {
        public final int quantity;
        public final double price;

        public Event(int quantity, double price) {
            this.quantity = quantity;
            this.price = price;
        }
    }

    public FilteredEvents(Map<String, List<Event>> eventsByProduct) {
        this.eventsByProduct = eventsByProduct;
    }

    public Map<String, List<Event>> getEventsByProduct() {
        return eventsByProduct;
    }

    // ===================== SERIALIZATION =========================

    /**
     * Serializes the FilteredEvents instance to a DataOutputStream.
     */
    public void serialize(DataOutputStream out) throws IOException {
        out.writeInt(eventsByProduct.size());

        for (Map.Entry<String, List<Event>> entry : eventsByProduct.entrySet()) {
            String product = entry.getKey();
            List<Event> events = entry.getValue();

            out.writeUTF(product);
            out.writeInt(events.size());

            for (Event e : events) {
                out.writeInt(e.quantity);
                out.writeDouble(e.price);
            }
        }
    }

    /**
     * Deserializes the FilteredEvents instance from a DataInputStream.
     */
    public static FilteredEvents deserialize(DataInputStream in) throws IOException {
        int numProducts = in.readInt();
        Map<String, List<Event>> map = new HashMap<>();

        for (int i = 0; i < numProducts; i++) {
            String product = in.readUTF();
            int numEvents = in.readInt();

            List<Event> events = new ArrayList<>(numEvents);
            for (int j = 0; j < numEvents; j++) {
                int quantity = in.readInt();
                double price = in.readDouble();
                events.add(new Event(quantity, price));
            }

            map.put(product, events);
        }

        return new FilteredEvents(map);
    }
}