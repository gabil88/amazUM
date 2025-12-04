package org.Server;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


/**
 *  In any way the program should try to get a not existing id (integer)
 */

/**
 * Dictionary class that provides a bidirectional mapping between String names
 * and Integer IDs.
 */
public class Dictionary {
    private Map<String, Integer> nameToId;
    private Map<Integer, String> idToName;
    private int counter = 0;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    public Dictionary() {
        this.nameToId = new HashMap<>();
        this.idToName = new HashMap<>();
    }

    public int get(String key) {
        rwLock.writeLock().lock();
        try {
            Integer id = nameToId.get(key);
            if (id == null) {
                addEntry(key);
                id = nameToId.get(key);
                System.out.println("Added new entry to Dictionary: " + key + " with ID: " + id);
            }
            return id;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public String get(int id) {
        rwLock.readLock().lock();
        try {
            return idToName.getOrDefault(id, null);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private void addEntry(String name) {
        if (!nameToId.containsKey(name)) {
            nameToId.put(name, counter);
            idToName.put(counter, name);
            counter++;
        }
    }

    public void serialize(DataOutputStream dos) throws IOException {
        rwLock.readLock().lock();
        try {
            dos.writeInt(counter);
            dos.writeInt(nameToId.size());
            for (Map.Entry<String, Integer> entry : nameToId.entrySet()) {
                dos.writeUTF(entry.getKey());
                dos.writeInt(entry.getValue());
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public static Dictionary deserialize(DataInputStream dis) throws IOException {
        Dictionary dict = new Dictionary();
        dict.counter = dis.readInt();
        int size = dis.readInt();
        for (int i = 0; i < size; i++) {
            String name = dis.readUTF();
            int id = dis.readInt();
            dict.nameToId.put(name, id);
            dict.idToName.put(id, name);
        }
        return dict;
    }   
}
