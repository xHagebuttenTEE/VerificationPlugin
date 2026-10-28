package de.hage.verification.util;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.IllegalPluginAccessException;

public final class AsyncExecutor {

    private final Plugin plugin;

    public AsyncExecutor(Plugin plugin) {
        this.plugin = plugin;
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (!plugin.isEnabled()) {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
            return future;
        }
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    future.complete(supplier.get());
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (IllegalPluginAccessException e) {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }
        return future;
    }

    public CompletableFuture<Void> runAsync(Runnable runnable) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (!plugin.isEnabled()) {
            try {
                runnable.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
            return future;
        }
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    runnable.run();
                    future.complete(null);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (IllegalPluginAccessException e) {
            try {
                runnable.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }
        return future;
    }

    public void runSync(Runnable runnable) {
        if (!plugin.isEnabled()) {
            runnable.run();
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, runnable);
        } catch (IllegalPluginAccessException e) {
            runnable.run();
        }
    }
}