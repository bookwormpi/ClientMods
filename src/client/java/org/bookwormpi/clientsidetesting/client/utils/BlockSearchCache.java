package org.bookwormpi.clientsidetesting.client.utils;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caching system for block search results to improve performance
 */
public class BlockSearchCache {
    private static final long CACHE_EXPIRY_MS = 30000; // 30 seconds
    private static final Map<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();
    
    public static class CacheKey {
        private final ChunkPos chunkPos;
        private final Block block;
        private final int hashCode;
        
        public CacheKey(ChunkPos chunkPos, Block block) {
            this.chunkPos = chunkPos;
            this.block = block;
            this.hashCode = Objects.hash(chunkPos, block);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof CacheKey other)) return false;
            return Objects.equals(chunkPos, other.chunkPos) && 
                   Objects.equals(block, other.block);
        }
        
        @Override
        public int hashCode() {
            return hashCode;
        }
    }
    
    private static class CacheEntry {
        private final List<BlockPos> positions;
        private final long timestamp;
        
        public CacheEntry(List<BlockPos> positions) {
            this.positions = new ArrayList<>(positions);
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
        
        public List<BlockPos> getPositions() {
            return new ArrayList<>(positions);
        }
    }
    
    public static Optional<List<BlockPos>> getCachedResults(ChunkPos chunkPos, Block block) {
        CacheKey key = new CacheKey(chunkPos, block);
        CacheEntry entry = cache.get(key);
        
        if (entry != null) {
            if (entry.isExpired()) {
                cache.remove(key);
                return Optional.empty();
            }
            return Optional.of(entry.getPositions());
        }
        
        return Optional.empty();
    }
    
    public static void cacheResults(ChunkPos chunkPos, Block block, List<BlockPos> positions) {
        CacheKey key = new CacheKey(chunkPos, block);
        cache.put(key, new CacheEntry(positions));
    }
    
    public static void clearCache() {
        cache.clear();
    }
    
    public static void cleanExpiredEntries() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    public static int getCacheSize() {
        return cache.size();
    }
}
