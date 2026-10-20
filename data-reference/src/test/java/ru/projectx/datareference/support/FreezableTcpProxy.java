package ru.projectx.datareference.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class FreezableTcpProxy implements AutoCloseable {

    private static final int BUFFER = 8192;

    private final ServerSocket listening;
    private final String targetHost;
    private final int targetPort;
    private final List<Socket> sockets = new CopyOnWriteArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition thawed = lock.newCondition();
    private boolean frozen;

    private FreezableTcpProxy(String targetHost, int targetPort) throws IOException {
        this.listening = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        Thread.ofVirtual().start(this::acceptConnections);
    }

    public static FreezableTcpProxy to(String targetHost, int targetPort) {
        try {
            return new FreezableTcpProxy(targetHost, targetPort);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public int port() {
        return listening.getLocalPort();
    }

    public void freeze() {
        lock.lock();
        try {
            frozen = true;
        } finally {
            lock.unlock();
        }
    }

    public void thaw() {
        lock.lock();
        try {
            frozen = false;
            thawed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        thaw();
        listening.close();
        for (Socket socket : sockets) {
            socket.close();
        }
    }

    private void acceptConnections() {
        try {
            while (!listening.isClosed()) {
                Socket client = listening.accept();
                Socket server = new Socket(targetHost, targetPort);
                sockets.add(client);
                sockets.add(server);
                Thread.ofVirtual().start(() -> pump(client, server));
                Thread.ofVirtual().start(() -> pump(server, client));
            }
        } catch (IOException closed) {
            sockets.clear();
        }
    }

    private void pump(Socket from, Socket to) {
        byte[] buffer = new byte[BUFFER];
        try (Socket source = from; Socket target = to) {
            InputStream in = source.getInputStream();
            OutputStream out = target.getOutputStream();
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                awaitThawed();
                out.write(buffer, 0, read);
                out.flush();
            }
        } catch (IOException connectionEnded) {
            sockets.remove(from);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitThawed() throws InterruptedException {
        lock.lock();
        try {
            while (frozen) {
                thawed.await();
            }
        } finally {
            lock.unlock();
        }
    }
}
