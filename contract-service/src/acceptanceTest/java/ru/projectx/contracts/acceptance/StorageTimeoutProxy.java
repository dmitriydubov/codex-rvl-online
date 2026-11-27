package ru.projectx.contracts.acceptance;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

final class StorageTimeoutProxy implements AutoCloseable {

    private final ServerSocket listener;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Socket> clients = new HashSet<>();
    private final AtomicLong activity = new AtomicLong();
    private final Supplier<String> upstream;
    private boolean paused;

    StorageTimeoutProxy(Supplier<String> upstream) throws IOException {
        this.upstream = upstream;
        listener = new ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"));
        workers.submit(this::listen);
    }

    String endpoint() {
        return "http://127.0.0.1:" + listener.getLocalPort();
    }

    long activity() {
        return activity.get();
    }

    synchronized void pause() {
        paused = true;
        disconnect();
    }

    synchronized void resume() {
        disconnect();
        paused = false;
    }

    @Override
    public void close() throws IOException {
        listener.close();
        synchronized (this) {
            disconnect();
        }
        workers.shutdownNow();
    }

    private void listen() {
        while (!listener.isClosed()) {
            try {
                accept(listener.accept());
            } catch (IOException unavailable) {
                if (!listener.isClosed()) {
                    throw new UncheckedIOException(unavailable);
                }
            }
        }
    }

    private synchronized void accept(Socket client) {
        activity.incrementAndGet();
        clients.add(client);
        if (!paused) {
            workers.submit(() -> connect(client));
        }
    }

    private void connect(Socket client) {
        URI address = URI.create(upstream.get());
        try (client; Socket server = new Socket(address.getHost(), address.getPort())) {
            workers.submit(() -> relay(server, client));
            relayRequest(client, server);
        } catch (IOException disconnected) {
            closeSocket(client);
        } finally {
            forget(client);
        }
    }

    private void relayRequest(Socket client, Socket server) throws IOException {
        try (var counted = new FilterOutputStream(server.getOutputStream()) {
            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                activity.incrementAndGet();
                out.write(bytes, offset, length);
            }
        }) {
            client.getInputStream().transferTo(counted);
        }
    }

    private static void relay(Socket input, Socket output) {
        try (output) {
            input.getInputStream().transferTo(output.getOutputStream());
        } catch (IOException disconnected) {
            closeSocket(output);
        }
    }

    private synchronized void forget(Socket client) {
        clients.remove(client);
    }

    private void disconnect() {
        clients.forEach(StorageTimeoutProxy::closeSocket);
        clients.clear();
    }

    private static void closeSocket(Socket socket) {
        try {
            socket.close();
        } catch (IOException disconnected) {
            throw new UncheckedIOException(disconnected);
        }
    }
}
