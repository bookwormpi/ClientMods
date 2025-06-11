package org.bookwormpi.clientsidetesting.client.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Checks for mod updates from GitHub releases
 */
public class UpdateChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger("clientsidetesting-updater");
    private static final String GITHUB_API_URL = "https://api.github.com/repos/bookwormpi/ClientMods/releases/latest";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    public static class UpdateInfo {
        public final boolean updateAvailable;
        public final String currentVersion;
        public final String latestVersion;
        public final String downloadUrl;
        public final String changelog;
        
        public UpdateInfo(boolean updateAvailable, String currentVersion, String latestVersion, 
                         String downloadUrl, String changelog) {
            this.updateAvailable = updateAvailable;
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
            this.downloadUrl = downloadUrl;
            this.changelog = changelog;
        }
    }
    
    /**
     * Asynchronously check for updates
     */
    public static CompletableFuture<UpdateInfo> checkForUpdates() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String currentVersion = FabricLoader.getInstance()
                    .getModContainer("clientsidetesting")
                    .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_URL))
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "ClientSideTesting-Mod")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
                
                HttpResponse<String> response = HTTP_CLIENT.send(request, 
                    HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String latestVersion = json.get("tag_name").getAsString();
                    String downloadUrl = json.getAsJsonArray("assets")
                        .get(0).getAsJsonObject()
                        .get("browser_download_url").getAsString();
                    String changelog = json.get("body").getAsString();
                    
                    boolean updateAvailable = !currentVersion.equals(latestVersion) && 
                                            !currentVersion.equals("unknown");
                    
                    if (updateAvailable) {
                        LOGGER.info("Update available: {} -> {}", currentVersion, latestVersion);
                    } else {
                        LOGGER.debug("Mod is up to date: {}", currentVersion);
                    }
                    
                    return new UpdateInfo(updateAvailable, currentVersion, latestVersion, 
                                        downloadUrl, changelog);
                } else {
                    LOGGER.warn("Failed to check for updates: HTTP {}", response.statusCode());
                }
            } catch (IOException | InterruptedException e) {
                LOGGER.debug("Update check failed: {}", e.getMessage());
            } catch (Exception e) {
                LOGGER.warn("Unexpected error during update check", e);
            }
            
            String currentVersion = FabricLoader.getInstance()
                .getModContainer("clientsidetesting")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
            
            return new UpdateInfo(false, currentVersion, currentVersion, "", "");
        });
    }
}
