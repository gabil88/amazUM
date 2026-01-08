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
 * if true:
 *    int dictSize
 *    repeat dictSize:
 *       int productId
 *       UTF productName
 *
 * int numProducts
 * repeat numProducts:
 *     int productId
 *     int numEvents
 *     repeat numEvents:
 *         int quantity
 *         double price
 */
public class FilteredEvents {

    /* Dictionary used to map a product id to its product name */
    private final Map<Integer, String> dictionaryUpdate;
    /** Map containing for each product its list of events object, containing its quantity and price */
    private final Map<Integer, List<Event>> eventsByProduct;

    public static class Event {
        public final int quantity;
        public final double price;

        public Event(int quantity, double price) {
            this.quantity = quantity;
            this.price = price;
        }
    }

    public FilteredEvents(Map<Integer, String> dictionaryUpdate, Map<Integer, List<Event>> eventsByProduct) {
        this.eventsByProduct = eventsByProduct;
        this.dictionaryUpdate = dictionaryUpdate;
    }

    public Map<Integer, String> getDictionaryUpdate() {
        return dictionaryUpdate;
    }

    public Map<Integer, List<Event>> getEventsByProduct() {
        return eventsByProduct;
    }

    // ===================== SERIALIZATION =========================

    /**
     * Serializes the FilteredEvents instance to a DataOutputStream.
     */
    public void serialize(DataOutputStream out) throws IOException {
        
        // Serialize Dictionary
        if(dictionaryUpdate != null && !dictionaryUpdate.isEmpty()){
            out.writeBoolean(true);
            out.writeInt(dictionaryUpdate.size());
            for(Map.Entry<Integer, String> dict: dictionaryUpdate.entrySet()){
                out.writeInt(dict.getKey());
                out.writeUTF(dict.getValue());
            }
        } else {
            out.writeBoolean(false);
        }

        // Serialize Events
        out.writeInt(eventsByProduct.size());
        for (Map.Entry<Integer, List<Event>> entry : eventsByProduct.entrySet()) {
            Integer product = entry.getKey();
            List<Event> events = entry.getValue();

            out.writeInt(product);
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
        
        Map<Integer, String> dict = null;

        boolean hasDict = in.readBoolean();
        if (hasDict) {
            int size = in.readInt();
            dict = new HashMap<>(size);
            for (int i = 0; i < size; i++) {
                int id = in.readInt();
                String name = in.readUTF();
                dict.put(id, name);
            }
        }
        
        int numProducts = in.readInt();
        Map<Integer, List<Event>> events = new HashMap<>();

        for (int i = 0; i < numProducts; i++) {
            Integer product = in.readInt();
            int numEvents = in.readInt();

            List<Event> list = new ArrayList<>(numEvents);
            for (int j = 0; j < numEvents; j++) {
                int quantity = in.readInt();
                double price = in.readDouble();
                list.add(new Event(quantity, price));
            }

            events.put(product, list);
        }

        return new FilteredEvents(dict, events);
    }
}