package org.bookwormpi.clientsidetesting.client.combat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.bookwormpi.clientsidetesting.client.ClientSideTestingClient;
import org.bookwormpi.clientsidetesting.client.targeting.TargetingSystem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CombatHudFeature {
    // Mod identifier for HUD layer registration
    private static final String MOD_ID = "clientsidetesting";
    private static final Identifier HUD_LAYER_ID = Identifier.of(MOD_ID, "combat-hud");
    
    // Aim lock system variables
    private static boolean aimLockEnabled = false;

    // Visual settings
    private static final float BASE_SQUARE_SIZE = 0.5f;
    private static final float BASE_CIRCLE_SIZE = 0.3f;
    private static final float DISTANCE_SCALE_FACTOR = 0.08f; // Controls how quickly the size scales with distance
    private static final float MIN_SCALE = 0.3f; // Minimum scale to prevent tiny indicators
    private static final float MAX_SCALE = 2.0f; // Maximum scale to prevent huge indicators

    // Range settings
    private static final double BASE_TARGET_RANGE = 128.0;

    // Targeting system instance
    private static final TargetingSystem targetingSystem = new TargetingSystem();
    
    // Camera smoothing fields
    private static float currentYaw = 0.0f;
    private static float currentPitch = 0.0f;
    private static float targetYaw = 0.0f;
    private static float targetPitch = 0.0f;
    private static boolean smoothingInitialized = false;
    private static final float SMOOTHING_FACTOR = 0.60f; // Adjust for desired smoothing speed (0.1-0.3 recommended)

    public static void register() {
        // Register the new HUD layer for 2D overlay elements (aim lock indicators)
        HudLayerRegistrationCallback.EVENT.register(layeredDrawer -> 
            layeredDrawer.attachLayerBefore(IdentifiedLayer.CROSSHAIR, HUD_LAYER_ID, CombatHudFeature::renderHudOverlay));
        
        // Keep world rendering for 3D elements (entity squares, trajectory lines, aim circles)
        WorldRenderEvents.AFTER_ENTITIES.register(CombatHudFeature::onWorldRender);

        // Register a tick event to check for hits on entities
        ClientTickEvents.END_CLIENT_TICK.register(CombatHudFeature::onClientTick);
    }

    /**
     * New HUD overlay rendering method for Fabric 1.21.4
     * Renders 2D overlay elements that should appear over the UI
     */
    private static void renderHudOverlay(DrawContext context, RenderTickCounter tickCounter) {
        if (!ClientSideTestingClient.showCombatHud) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        // Only show HUD when holding bow or crossbow
        ItemStack heldItem = client.player.getMainHandStack();
        if (!(heldItem.getItem() instanceof BowItem || heldItem.getItem() instanceof CrossbowItem)) {
            return;
        }

        // For now, we'll keep most rendering in world space since that works better for 3D indicators
        // This method can be used for 2D crosshair overlays or UI elements in the future
        
        // Get screen dimensions and center position
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        
        // Create time variable for AIM LOCK fade animation
        double time = client.world.getTime() * 0.1;
        
        // Enhanced status indicator when aim lock is enabled with consistent fade in/out animation
        if (aimLockEnabled && targetingSystem.isTargetValid(targetingSystem.getCurrentTarget())) {
            // Calculate consistent fade in/out alpha using absolute sine for smooth transitions
            float fadeAlpha = (float) (0.3 + 0.7 * Math.abs(Math.sin(time * 1.5))); // 30%-100% opacity range
            int alpha = (int) (fadeAlpha * 255);
            int color = (alpha << 24) | 0x00FF0000; // Red with fading alpha
            
            // Draw the "AIM LOCK" text above the crosshair
            String text = "AIM LOCK";
            int textWidth = client.textRenderer.getWidth(text);
            int textY = centerY - 25; // Position above crosshair
            context.drawText(client.textRenderer, text, centerX - textWidth/2, textY, color, true);
        }
        
        // Target information panel - show when we have a valid target
        if (targetingSystem.isTargetValid(targetingSystem.getCurrentTarget())) {
            LivingEntity target = targetingSystem.getCurrentTarget();
            Vec3d playerPos = client.player.getEyePos();
            Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
            
            // Calculate distance to target
            double distance = playerPos.distanceTo(targetPos);
            
            // Calculate time to impact using ballistic calculations
            double timeToImpact = calculateTimeToImpact(target, heldItem);
            
            // Static white color for target info (no pulsing)
            int infoColor = 0xFFFFFFFF; // Solid white
            
            // Target name - above crosshair
            String targetName = getTargetDisplayName(target);
            int nameWidth = client.textRenderer.getWidth(targetName);
            context.drawText(client.textRenderer, targetName, centerX - nameWidth/2, centerY - 40, infoColor, true);
            
            // Distance - below crosshair
            String distanceText = String.format("%.1fm", distance);
            int distanceWidth = client.textRenderer.getWidth(distanceText);
            context.drawText(client.textRenderer, distanceText, centerX - distanceWidth/2, centerY + 15, infoColor, true);
            
            // Time to impact - below distance
            if (timeToImpact > 0) {
                String timeText = String.format("%.1fs", timeToImpact);
                int timeWidth = client.textRenderer.getWidth(timeText);
                context.drawText(client.textRenderer, timeText, centerX - timeWidth/2, centerY + 27, infoColor, true);
            }
            
            // Health bar - left of crosshair (static, no pulsing)
            renderTargetHealthBar(context, target, centerX - 60, centerY - 5);
        }
    }

    /**
     * Main render method, called after entities are rendered
     */
    private static void onWorldRender(WorldRenderContext context) {
        if (!ClientSideTestingClient.showCombatHud) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        // Only show HUD when holding bow or crossbow
        ItemStack heldItem = client.player.getMainHandStack();
        if (!(heldItem.getItem() instanceof BowItem || heldItem.getItem() instanceof CrossbowItem)) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        Vec3d cameraPos = context.camera().getPos();
        VertexConsumerProvider.Immediate vertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();

        // Render squares around all targetable entities
        renderEntitySquares(matrices, vertexConsumers, cameraPos);

        // Draw aim prediction circle if we have a valid target
        if (targetingSystem.isTargetValid(targetingSystem.getCurrentTarget())) {
            renderAimAssistCircle(matrices, vertexConsumers, cameraPos, heldItem);
        }

        vertexConsumers.draw(); // Important: draw after all rendering is done
    }

    /**
     * Client tick handler to detect when player hits a mob
     */
    private static void onClientTick(MinecraftClient client) {
        if (!ClientSideTestingClient.showCombatHud) return;
        if (client.player == null || client.world == null) return;

        // Only monitor hits when holding bow or crossbow
        ItemStack heldItem = client.player.getMainHandStack();
        if (!(heldItem.getItem() instanceof BowItem || heldItem.getItem() instanceof CrossbowItem)) {
            return;
        }

        // Check for entity hit with raycast
        Entity hitEntity = getPlayerAttackTarget();
        if (hitEntity instanceof LivingEntity &&
            !hitEntity.equals(targetingSystem.getCurrentTarget()) && hitEntity.isAlive()) {
            // Player has hit a different living entity - switch target to it
            // Use targetingSystem logic if needed
        }

        // Handle aim lock if enabled
        if (aimLockEnabled && targetingSystem.isTargetValid(targetingSystem.getCurrentTarget())) {
            updatePlayerViewToTarget(client, heldItem);
        }
    }

    /**
     * Gets the entity the player is currently attacking
     */
    private static Entity getPlayerAttackTarget() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return null;

        // Check if player is currently attacking
        if (client.options.attackKey.isPressed()) {
            // Perform a raycast in the direction player is looking
            Vec3d eyePos = client.player.getEyePos();
            Vec3d lookVec = client.player.getRotationVec(1.0f);
            double reach = 5.0; // Use standard reach distance
            Vec3d targetPos = eyePos.add(lookVec.multiply(reach));

            // Check for entity hit
            EntityHitResult hitResult = ProjectileUtil.getEntityCollision(
                client.world,
                client.player,
                eyePos,
                targetPos,
                new Box(eyePos, targetPos).expand(1.0),
                entity -> entity instanceof LivingEntity && // Target any living entity 
                        entity != client.player && // Don't target self
                        entity.isAlive() && // Only living entities
                        !entity.isSpectator() // Exclude spectators
            );

            if (hitResult != null) {
                return hitResult.getEntity();
            }
        }

        return null;
    }

    /**
     * Renders a small square around all targetable entities
     */
    private static void renderEntitySquares(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                  Vec3d cameraPos) {
        List<LivingEntity> entities = targetingSystem.getEligibleMobsInRange();
        for (LivingEntity entity : entities) {
            Vec3d entityPos = entity.getPos();
            double x = entityPos.x - cameraPos.x;
            double y = entityPos.y + entity.getHeight() + 0.5 - cameraPos.y;
            double z = entityPos.z - cameraPos.z;

            boolean isTarget = entity.equals(targetingSystem.getCurrentTarget());
            float r = isTarget ? 1.0f : 0.0f;
            float g = isTarget ? 0.0f : 1.0f;
            float b = 0.0f;
            float a = 0.8f;

            // Calculate distance to entity
            double distance = Math.sqrt(x*x + y*y + z*z);

            // Scale size based on distance
            float scale = calculateDistanceScale(distance);
            float squareSize = BASE_SQUARE_SIZE * scale;

            // Draw a square always facing the player
            drawSquare(matrices, vertexConsumers, x, y, z, squareSize, r, g, b, a);
        }
    }

    /**
     * Renders a square at the given position that always faces the camera
     */
    private static void drawSquare(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                  double x, double y, double z, float size, float r, float g, float b, float a) {
        matrices.push();
        matrices.translate(x, y, z);

        // Make the square face the camera
        matrices.multiply(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());

        VertexConsumer lines = vertexConsumers.getBuffer(RenderLayer.getLines());

        float half = size / 2;

        // Draw the square outline
        // Top edge
        lines.vertex(matrices.peek().getPositionMatrix(), -half, -half, 0)
            .color(r, g, b, a)
            .normal(0.0f, 1.0f, 0.0f);
        lines.vertex(matrices.peek().getPositionMatrix(), half, -half, 0)
            .color(r, g, b, a)
            .normal(0.0f, 1.0f, 0.0f);

        // Right edge
        lines.vertex(matrices.peek().getPositionMatrix(), half, -half, 0)
            .color(r, g, b, a)
            .normal(0.0f, 1.0f, 0.0f);
        lines.vertex(matrices.peek().getPositionMatrix(), half, half, 0)
            .color(r, g, b, a)
            .normal(0.0f, 1.0f, 0.0f);

        // Bottom edge
        lines.vertex(matrices.peek().getPositionMatrix(), half, half, 0)
            .color(r, g, b, a)
            .normal(0.0f, 1.0f, 0.0f);
        lines.vertex(matrices.peek().getPositionMatrix(), -half, half, 0)
            .color(r, g, b, a)
            .normal(0.0f, 1.0f, 0.0f);

        // Left edge
        lines.vertex(matrices.peek().getPositionMatrix(), -half, half, 0)
            .color(r, g, b, a)
            .normal(0.0f, 1.0f, 0.0f);
        lines.vertex(matrices.peek().getPositionMatrix(), -half, -half, 0)
            .color(r, g, b, a)
            .normal(0.0f, 1.0f, 0.0f);

        matrices.pop();
    }

    /**
     * Renders the aim assist circle at the predicted position
     */
    private static void renderAimAssistCircle(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                             Vec3d cameraPos, ItemStack weapon) {
        // Use ideal aim prediction for moving targets
        List<Vec3d> dummyPath = new ArrayList<>();
        Vec3d aimPos = targetingSystem.calculateIdealAimPosition(targetingSystem.getCurrentTarget(), weapon, dummyPath);
        if (aimPos == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        double x = aimPos.x - cameraPos.x;
        double y = aimPos.y - cameraPos.y;
        double z = aimPos.z - cameraPos.z;
        double distance = Math.sqrt(x*x + y*y + z*z);
        float scale = calculateDistanceScale(distance);
        float circleRadius = BASE_CIRCLE_SIZE * scale;
        float red = aimLockEnabled ? 1.0f : 0.0f;
        float green = 1.0f;
        float blue = aimLockEnabled ? 1.0f : 0.0f;
        float alpha = 0.8f;
        drawCircle(matrices, vertexConsumers, x, y, z, circleRadius, red, green, blue, alpha);
    }

    /**
     * Draws a circle that always faces the camera
     */
    private static void drawCircle(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                  double x, double y, double z, float radius, float r, float g, float b, float a) {
        matrices.push();
        matrices.translate(x, y, z);

        // Make the circle face the camera
        matrices.multiply(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());

        VertexConsumer lines = vertexConsumers.getBuffer(RenderLayer.getLines());

        // Draw the circle with line segments
        int segments = 20;
        double angleStep = 2 * Math.PI / segments;

        for (int i = 0; i < segments; i++) {
            double angle1 = i * angleStep;
            double angle2 = (i + 1) * angleStep;

            float x1 = (float) (Math.cos(angle1) * radius);
            float y1 = (float) (Math.sin(angle1) * radius);
            float x2 = (float) (Math.cos(angle2) * radius);
            float y2 = (float) (Math.sin(angle2) * radius);

            lines.vertex(matrices.peek().getPositionMatrix(), x1, y1, 0)
                .color(r, g, b, a)
                .normal(0.0f, 1.0f, 0.0f);
            lines.vertex(matrices.peek().getPositionMatrix(), x2, y2, 0)
                .color(r, g, b, a)
                .normal(0.0f, 1.0f, 0.0f);
        }

        matrices.pop();
    }

    /**
     * Calculates appropriate scale based on distance to maintain consistent visual size
     */
    private static float calculateDistanceScale(double distance) {
        // Linear scaling with distance
        float scale = (float)(distance * DISTANCE_SCALE_FACTOR);

        // Constrain scale to reasonable limits
        return Math.max(MIN_SCALE, Math.min(scale, MAX_SCALE));
    }

    /**
     * Gets all living entities in range (including players)
     */
    public static List<LivingEntity> getAllLivingEntitiesInRange() {
        MinecraftClient client = MinecraftClient.getInstance();
        List<LivingEntity> entities = new ArrayList<>();

        if (client.player == null || client.world == null) return entities;

        Vec3d playerPos = client.player.getPos();
        Box searchBox = new Box(
                playerPos.x - BASE_TARGET_RANGE, playerPos.y - BASE_TARGET_RANGE, playerPos.z - BASE_TARGET_RANGE,
                playerPos.x + BASE_TARGET_RANGE, playerPos.y + BASE_TARGET_RANGE, playerPos.z + BASE_TARGET_RANGE);

        // Get all living entities (mobs and players)
        for (Entity entity : client.world.getEntitiesByClass(LivingEntity.class, searchBox, e -> 
                e != client.player && // Exclude the client player
                e.isAlive() && // Only include living entities
                !e.isSpectator())) { // Exclude spectators
            entities.add((LivingEntity) entity);
        }

        // Sort by distance to player
        entities.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(client.player)));
        return entities;
    }

    /**
     * Handles the target cycle key press (now selects the entity closest to the player's crosshair within 5 degrees, including mobs except bats and other players)
     */
    public static void handleTargetCycleKeyPress() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        // Only allow selection if holding a bow or crossbow
        ItemStack heldItem = client.player.getMainHandStack();
        if (!(heldItem.getItem() instanceof BowItem || heldItem.getItem() instanceof CrossbowItem)) {
            return;
        }

        // Get all eligible entities (mobs excluding bats, and other players)
        List<LivingEntity> mobs = targetingSystem.getEligibleMobsInRange();
        if (mobs.isEmpty()) return;

        // Player's orientation
        float playerYaw = client.player.getYaw();
        float playerPitch = client.player.getPitch();
        LivingEntity best = null;
        double bestAngle = 6.0; // Only consider mobs within 5 degrees, so start with 6
        for (LivingEntity mob : mobs) {
            // Vector from player eye to mob center
            Vec3d playerEye = client.player.getEyePos();
            Vec3d mobCenter = mob.getPos().add(0, mob.getHeight() * 0.5, 0);
            Vec3d toMob = mobCenter.subtract(playerEye).normalize();
            // Convert to yaw/pitch
            double dx = toMob.x;
            double dy = toMob.y;
            double dz = toMob.z;
            double horizDist = Math.sqrt(dx*dx + dz*dz);
            float mobYaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
            float mobPitch = (float)-Math.toDegrees(Math.atan2(dy, horizDist));
            // Angle difference
            float yawDiff = Math.abs(MathHelper.wrapDegrees(mobYaw - playerYaw));
            float pitchDiff = Math.abs(mobPitch - playerPitch);
            if (yawDiff <= 5.0f && pitchDiff <= 5.0f) {
                double totalAngle = Math.hypot(yawDiff, pitchDiff);
                if (totalAngle < bestAngle) {
                    best = mob;
                    bestAngle = totalAngle;
                }
            }
        }
        if (best != null) {
            targetingSystem.setCurrentTarget(best);
        }
    }

    /**
     * Handles the aim lock key press
     */
    public static void handleAimLockKeyPress() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        // Check if player is holding a bow or crossbow
        ItemStack heldItem = client.player.getMainHandStack();
        if (!(heldItem.getItem() instanceof BowItem || heldItem.getItem() instanceof CrossbowItem)) {
            aimLockEnabled = false;
            smoothingInitialized = false; // Reset smoothing when disabled
            return;
        }

        // Toggle aim lock status
        aimLockEnabled = !aimLockEnabled;
        
        // Reset smoothing when toggling aim lock
        if (!aimLockEnabled) {
            smoothingInitialized = false;
        }
    }

    /**
     * Updates player's view to look at the aim target with smooth camera movement
     */
    private static void updatePlayerViewToTarget(MinecraftClient client, ItemStack weapon) {
        if (!targetingSystem.isTargetValid(targetingSystem.getCurrentTarget())) return;

        List<Vec3d> dummyPath = new ArrayList<>();
        Vec3d aimPos = targetingSystem.calculateIdealAimPosition(targetingSystem.getCurrentTarget(), weapon, dummyPath);
        if (aimPos == null) return;
        
        Vec3d playerPos = client.player.getEyePos();
        Vec3d direction = aimPos.subtract(playerPos).normalize();
        double horizontalDistance = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        
        // Calculate target angles
        targetYaw = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0F;
        targetPitch = (float) -Math.toDegrees(Math.atan2(direction.y, horizontalDistance));
        
        // Initialize smoothing on first use
        if (!smoothingInitialized) {
            currentYaw = client.player.getYaw();
            currentPitch = client.player.getPitch();
            smoothingInitialized = true;
        }
        
        // Handle yaw wrapping (shortest path rotation)
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        currentYaw = MathHelper.wrapDegrees(currentYaw + yawDiff * SMOOTHING_FACTOR);
        
        // Smooth pitch interpolation
        currentPitch = MathHelper.lerp(SMOOTHING_FACTOR, currentPitch, targetPitch);
        
        // Apply smoothed camera angles
        client.player.setYaw(currentYaw);
        client.player.setPitch(currentPitch);
    }

    /**
     * Calculates the time it takes for a projectile to reach the target
     */
    private static double calculateTimeToImpact(LivingEntity target, ItemStack weapon) {
        if (target == null) return 0.0;
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return 0.0;
        
        Vec3d shooterPos = client.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
        
        // Calculate projectile speed based on weapon type
        float projectileSpeed = 3.0f;
        if (weapon.getItem() instanceof BowItem && client.player.isUsingItem()) {
            int useTicks = client.player.getItemUseTimeLeft();
            float draw = BowItem.getPullProgress(useTicks);
            projectileSpeed = 3.0f * draw;
        } else if (weapon.getItem() instanceof CrossbowItem) {
            projectileSpeed = 3.15f;
        }
        
        // Simple time calculation (ignoring arc for display purposes)
        double distance = shooterPos.distanceTo(targetPos);
        return distance / projectileSpeed;
    }
    
    /**
     * Gets a display-friendly name for the target entity
     */
    private static String getTargetDisplayName(LivingEntity target) {
        if (target == null) return "Unknown";
        
        // Try to get custom name first
        if (target.hasCustomName()) {
            return target.getCustomName().getString();
        }
        
        // Get entity type name
        String entityName = target.getType().getTranslationKey();
        
        // Clean up the translation key for display
        if (entityName.startsWith("entity.minecraft.")) {
            entityName = entityName.substring(17); // Remove "entity.minecraft."
        }
        
        // Capitalize first letter and replace underscores with spaces
        if (!entityName.isEmpty()) {
            entityName = entityName.substring(0, 1).toUpperCase() + 
                        entityName.substring(1).replace("_", " ");
        }
        
        return entityName;
    }
    
    /**
     * Renders a health bar for the target entity with static appearance
     */
    private static void renderTargetHealthBar(DrawContext context, LivingEntity target, 
                                            int x, int y) {
        if (target == null) return;
        
        float currentHealth = target.getHealth();
        float maxHealth = target.getMaxHealth();
        float healthRatio = Math.max(0.0f, Math.min(1.0f, currentHealth / maxHealth));
        
        // Health bar dimensions
        int barWidth = 40;
        int barHeight = 4;
        
        // Background (dark red) - static with 50% opacity
        int bgColor = (128 << 24) | 0x00440000; // Static dark red background
        context.fill(x, y, x + barWidth, y + barHeight, bgColor);
        
        // Health bar (green to red gradient based on health) - static appearance
        int healthWidth = (int) (barWidth * healthRatio);
        if (healthWidth > 0) {
            // Color transitions from green (high health) to red (low health)
            int red = (int) (255 * (1.0f - healthRatio));
            int green = (int) (255 * healthRatio);
            int healthColor = (255 << 24) | (red << 16) | (green << 8); // Full opacity, no pulsing
            context.fill(x, y, x + healthWidth, y + barHeight, healthColor);
        }
        
        // Health text - static white appearance
        String healthText = String.format("%.0f/%.0f", currentHealth, maxHealth);
        int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(healthText);
        int textColor = 0xFFFFFFFF; // Static white text, full opacity
        context.drawText(MinecraftClient.getInstance().textRenderer, healthText, 
                        x + (barWidth - textWidth) / 2, y - 10, textColor, true);
    }
}