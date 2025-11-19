package org;


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
        public final byte[] data;

        public Frame(int tag, byte[] data) {
            this.tag = tag;
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
            out.writeInt(frame.data.length);
            out.write(frame.data);
            out.flush();
        } finally {
            writeLock.unlock();
        }
    }

    public void send(int tag, byte[] data) throws IOException {
        send(new Frame(tag, data));
    }

    public Frame receive() throws IOException {
        readLock.lock();
        try {
            int tag = in.readInt();
            int length = in.readInt();
            byte[] data = new byte[length];
            in.readFully(data);
            return new Frame(tag, data);
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
