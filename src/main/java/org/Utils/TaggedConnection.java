package org.Utils;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TaggedConnection implements AutoCloseable {
    private final DataInputStream in;
    private final DataOutputStream out;
    private final Lock readLock = new ReentrantLock();
    private final Lock writeLock = new ReentrantLock();

    public static class Frame {
        public final int tag;
        public final short requestType;
        public final byte[] data;

        public Frame(int tag, short requestType, byte[] data) {
            this.tag = tag;
            this.requestType = requestType;
            this.data = data;
        }
    }

    public TaggedConnection(Socket socket) throws IOException {
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public void send(Frame frame) throws IOException {
        writeLock.lock();
        try {
            out.writeInt(frame.tag);
            out.writeShort(frame.requestType);
            out.writeInt(frame.data.length);
            out.write(frame.data);
            out.flush();
        } finally {
            writeLock.unlock();
        }
    }

    public void send(int tag, short requestType, byte[] data) throws IOException {
        send(new Frame(tag, requestType, data));
    }

    public Frame receive() throws IOException {
        readLock.lock();
        try {
            int tag = in.readInt();
            short requestType = in.readShort();
            int length = in.readInt();
            byte[] data = new byte[length];
            in.readFully(data);
            return new Frame(tag, requestType, data);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        in.close();
        out.close();
    }
}
