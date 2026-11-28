package com.aiphoto.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class StorageRoots {
    public enum Kind { PHOTOS, THUMBNAILS, CRAWL, MODELS }

    private static final EnumMap<Kind, ReentrantReadWriteLock> LOCKS = new EnumMap<>(Kind.class);
    static {
        for (Kind kind : Kind.values()) LOCKS.put(kind, new ReentrantReadWriteLock(true));
    }

    private StorageRoots() {}

    static Path marker(String configured, Kind kind) {
        Path root = Path.of(configured).toAbsolutePath().normalize();
        Path anchor = kind == Kind.MODELS ? root : root.getParent();
        String control = System.getenv("STORAGE_CONTROL_DIRECTORY");
        Path directory = control == null || control.isBlank()
                ? anchor.resolve(".aiphoto-storage") : Path.of(control);
        return directory.resolve(kind.name().toLowerCase(Locale.ROOT) + ".active");
    }

    public static Path active(String configured, Kind kind) {
        String stored;
        try {
            stored = Files.readString(marker(configured, kind));
        } catch (NoSuchFileException missing) {
            return Path.of(configured).toAbsolutePath().normalize();
        } catch (IOException failure) {
            throw new UncheckedIOException("无法读取当前存储位置", failure);
        }
        try {
            Path target = Path.of(stored);
            if (!target.isAbsolute() || !Files.isDirectory(target)) {
                throw new IOException("当前存储目录不可用：" + target);
            }
            String pool = System.getenv("STORAGE_POOL_DIRECTORY");
            if (pool != null && !pool.isBlank()) {
                Path root = Path.of(pool).toRealPath();
                Path actual = target.toRealPath();
                if (root.equals(actual) || !actual.startsWith(root)) {
                    throw new IOException("当前存储目录不在存储池内：" + target);
                }
            }
            return target;
        } catch (IOException failure) {
            throw new UncheckedIOException("无法读取当前存储位置", failure);
        }
    }

    public static Lease writing(Kind kind) {
        var lock = LOCKS.get(kind).readLock();
        lock.lock();
        return lock::unlock;
    }

    static Lease migrating(Kind kind) {
        var lock = LOCKS.get(kind).writeLock();
        lock.lock();
        return lock::unlock;
    }

    public interface Lease extends AutoCloseable {
        @Override
        void close();
    }
}
