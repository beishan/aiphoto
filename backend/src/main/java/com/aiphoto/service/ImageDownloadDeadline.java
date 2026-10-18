package com.aiphoto.service;

import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/** One image shares a deadline across HTTP retries, redirects and proxy changes. */
final class ImageDownloadDeadline implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private final IntSupplier setting;
    private int timeoutSeconds;
    private long startedNanos;
    private boolean expired;
    private boolean closed;
    private boolean timeoutInterrupted;
    private Thread monitor;

    ImageDownloadDeadline(IntSupplier setting) {
        this.setting = setting;
        timeoutSeconds = Math.max(1, setting.getAsInt());
    }

    synchronized void start() {
        if (startedNanos != 0 || closed) return;
        // Do not charge the site's initial request-spacing wait to the image.
        startedNanos = System.nanoTime();
        monitor = Thread.ofVirtual().name("crawler-image-deadline").start(this::watch);
    }

    private void watch() {
        try {
            while (true) {
                Thread.sleep(100);
                int configured;
                try { configured = Math.max(1, setting.getAsInt()); }
                catch (RuntimeException unavailable) {
                    synchronized (this) { configured = timeoutSeconds; }
                }
                synchronized (this) {
                    if (closed || expired) return;
                    timeoutSeconds = configured;
                    if (elapsed()) {
                        expired = true;
                        timeoutInterrupted = true;
                        owner.interrupt();
                        return;
                    }
                }
            }
        } catch (InterruptedException stopped) {
            // The image completed or failed; its watchdog is no longer needed.
        }
    }

    private boolean elapsed() {
        return startedNanos != 0
                && System.nanoTime() - startedNanos >= TimeUnit.SECONDS.toNanos(timeoutSeconds);
    }

    synchronized void check() throws HttpTimeoutException {
        if (expired || !closed && elapsed()) {
            expired = true;
            throw new HttpTimeoutException("图片下载超时（总时限 " + timeoutSeconds
                    + " 秒，包含重试、重定向和代理切换）");
        }
    }

    synchronized void clearTimeoutInterrupt() {
        if (timeoutInterrupted) {
            Thread.interrupted();
            timeoutInterrupted = false;
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (monitor != null) monitor.interrupt();
        clearTimeoutInterrupt();
    }
}
