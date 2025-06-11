package org.bookwormpi.clientsidetesting.client.utils;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance monitoring and metrics collection
 */
public class PerformanceMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger("clientsidetesting-perf");
    private static final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private static final Map<String, Long> timers = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> durations = new ConcurrentHashMap<>();
    
    /**
     * Start timing an operation
     */
    public static void startTimer(String operation) {
        timers.put(operation, System.nanoTime());
    }
    
    /**
     * End timing an operation and record the duration
     */
    public static long endTimer(String operation) {
        Long startTime = timers.remove(operation);
        if (startTime != null) {
            long duration = System.nanoTime() - startTime;
            durations.computeIfAbsent(operation, k -> new AtomicLong(0)).addAndGet(duration);
            return duration;
        }
        return 0;
    }
    
    /**
     * Increment a counter
     */
    public static void incrementCounter(String counter) {
        counters.computeIfAbsent(counter, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    /**
     * Get average duration for an operation in milliseconds
     */
    public static double getAverageDuration(String operation) {
        AtomicLong totalDuration = durations.get(operation);
        AtomicLong count = counters.get(operation + "_count");
        
        if (totalDuration != null && count != null && count.get() > 0) {
            return (totalDuration.get() / (double) count.get()) / 1_000_000.0; // Convert to ms
        }
        return 0.0;
    }
    
    /**
     * Log performance metrics
     */
    public static void logMetrics() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("=== Performance Metrics ===");
            
            // Log counters
            counters.forEach((name, value) -> 
                LOGGER.debug("Counter {}: {}", name, value.get()));
            
            // Log average durations
            durations.forEach((operation, totalDuration) -> {
                AtomicLong count = counters.get(operation + "_count");
                if (count != null && count.get() > 0) {
                    double avgMs = (totalDuration.get() / (double) count.get()) / 1_000_000.0;
                    LOGGER.debug("Operation {} average: {:.2f}ms", operation, avgMs);
                }
            });
            
            // Log memory usage
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory() / 1024 / 1024;
            long freeMemory = runtime.freeMemory() / 1024 / 1024;
            long usedMemory = totalMemory - freeMemory;
            LOGGER.debug("Memory usage: {}MB used, {}MB free, {}MB total", 
                        usedMemory, freeMemory, totalMemory);
            
            // Log FPS if available
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.fpsDebugString != null) {
                LOGGER.debug("FPS: {}", client.fpsDebugString);
            }
        }
    }
    
    /**
     * Reset all metrics
     */
    public static void reset() {
        counters.clear();
        timers.clear();
        durations.clear();
    }
    
    /**
     * Get a metric value
     */
    public static long getCounter(String counter) {
        AtomicLong value = counters.get(counter);
        return value != null ? value.get() : 0;
    }
}
