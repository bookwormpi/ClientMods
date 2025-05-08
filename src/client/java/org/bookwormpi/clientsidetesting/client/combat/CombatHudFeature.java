package org.bookwormpi.clientsidetesting.client.combat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.bookwormpi.clientsidetesting.client.ClientsidetestingClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CombatHudFeature {
    // Targeting system variables
    private static final double TARGET_RANGE = 64.0;
    private static LivingEntity currentTarget;
    private static long lastTargetSwitchTime;
    private static final long TARGET_SWITCH_COOLDOWN = 2000; // 2 seconds in milliseconds
    
    // Visual settings
    private static final float BASE_SQUARE_SIZE = 0.5f;
    private static final float BASE_CIRCLE_SIZE = 0.3f;
    private static final float DISTANCE_SCALE_FACTOR = 0.08f; // Controls how quickly the size scales with distance
    private static final float MIN_SCALE = 0.3f; // Minimum scale to prevent tiny indicators
    private static final float MAX_SCALE = 2.0f; // Maximum scale to prevent huge indicators

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(CombatHudFeature::onWorldRender);
        
        // Register a tick event to check for hits on entities
        ClientTickEvents.END_CLIENT_TICK.register(CombatHudFeature::onClientTick);
    }

    /**
     * Main render method, called after entities are rendered
     */
    private static void onWorldRender(WorldRenderContext context) {
        if (!ClientsidetestingClient.showCombatHud) return;
        
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

        // Render squares around hostile entities
        renderHostileEntitySquares(matrices, vertexConsumers, cameraPos);
        
        // Draw aim prediction circle if we have a valid target
        if (isTargetValid()) {
            renderAimAssistCircle(matrices, vertexConsumers, cameraPos, heldItem);
        }
        
        vertexConsumers.draw(); // Important: draw after all rendering is done
    }
    
    /**
     * Client tick handler to detect when player hits a mob
     */
    private static void onClientTick(MinecraftClient client) {
        if (!ClientsidetestingClient.showCombatHud) return;
        if (client.player == null || client.world == null) return;
        
        // Only monitor hits when holding bow or crossbow
        ItemStack heldItem = client.player.getMainHandStack();
        if (!(heldItem.getItem() instanceof BowItem || heldItem.getItem() instanceof CrossbowItem)) {
            return;
        }
        
        // Check for entity hit with raycast
        Entity hitEntity = getPlayerAttackTarget();
        if (hitEntity instanceof LivingEntity && hitEntity instanceof HostileEntity && 
            !hitEntity.equals(currentTarget) && hitEntity.isAlive()) {
            // Player has hit a different hostile entity - switch target to it
            currentTarget = (LivingEntity) hitEntity;
            lastTargetSwitchTime = System.currentTimeMillis();
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
                Entity::isAttackable
            );
            
            if (hitResult != null) {
                return hitResult.getEntity();
            }
        }
        
        return null;
    }
    
    /**
     * Renders a small square around all hostile entities
     */
    private static void renderHostileEntitySquares(MatrixStack matrices, VertexConsumerProvider vertexConsumers, 
                                                  Vec3d cameraPos) {
        List<LivingEntity> hostiles = getHostileEntitiesInRange();
        for (LivingEntity entity : hostiles) {
            Vec3d entityPos = entity.getPos();
            double x = entityPos.x - cameraPos.x;
            double y = entityPos.y + entity.getHeight() + 0.5 - cameraPos.y;
            double z = entityPos.z - cameraPos.z;
            
            boolean isTarget = entity.equals(currentTarget);
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
        // Calculate where to aim based on target velocity, bow power, etc.
        Vec3d aimPos = calculateAimPosition(currentTarget, weapon);
        if (aimPos == null) return;
        
        double x = aimPos.x - cameraPos.x;
        double y = aimPos.y - cameraPos.y;
        double z = aimPos.z - cameraPos.z;
        
        // Calculate distance to aim point
        double distance = Math.sqrt(x*x + y*y + z*z);
        
        // Scale radius based on distance
        float scale = calculateDistanceScale(distance);
        float circleRadius = BASE_CIRCLE_SIZE * scale;
        
        // Draw a green circle at the aim position
        drawCircle(matrices, vertexConsumers, x, y, z, circleRadius, 0.0f, 1.0f, 0.0f, 0.8f);
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
     * Gets all hostile entities in range
     */
    public static List<LivingEntity> getHostileEntitiesInRange() {
        MinecraftClient client = MinecraftClient.getInstance();
        List<LivingEntity> hostiles = new ArrayList<>();
        
        if (client.player == null || client.world == null) return hostiles;

        Vec3d playerPos = client.player.getPos();
        Box searchBox = new Box(
                playerPos.x - TARGET_RANGE, playerPos.y - TARGET_RANGE, playerPos.z - TARGET_RANGE,
                playerPos.x + TARGET_RANGE, playerPos.y + TARGET_RANGE, playerPos.z + TARGET_RANGE);
        
        for (Entity entity : client.world.getEntitiesByClass(LivingEntity.class, searchBox, e -> e instanceof HostileEntity)) {
            hostiles.add((LivingEntity) entity);
        }
        
        // Sort by distance to player
        hostiles.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(client.player)));
        return hostiles;
    }
    
    /**
     * Handles the target cycle key press
     */
    public static void handleTargetCycleKeyPress() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        
        long currentTime = System.currentTimeMillis();
        boolean cooldownExpired = (currentTime - lastTargetSwitchTime) > TARGET_SWITCH_COOLDOWN;
        
        // Check if player is holding a bow or crossbow
        ItemStack heldItem = client.player.getMainHandStack();
        if (!(heldItem.getItem() instanceof BowItem || heldItem.getItem() instanceof CrossbowItem)) {
            currentTarget = null;
            return;
        }
        
        // If cooldown has expired, check if player is looking at a mob
        if (cooldownExpired) {
            LivingEntity lookTarget = getLookTargetEntity();
            if (lookTarget != null) {
                currentTarget = lookTarget;
                lastTargetSwitchTime = currentTime;
                return;
            }
        }
        
        // Otherwise cycle to next target
        cycleToNextTarget();
        lastTargetSwitchTime = currentTime;
    }
    
    /**
     * Cycles to the next target in the list
     */
    private static void cycleToNextTarget() {
        List<LivingEntity> hostiles = getHostileEntitiesInRange();
        if (hostiles.isEmpty()) {
            currentTarget = null;
            return;
        }
        
        if (currentTarget == null || !hostiles.contains(currentTarget)) {
            // If no current target or it's not in range anymore, choose the closest
            currentTarget = hostiles.get(0);
        } else {
            // Find current target's index and move to next
            int currentIndex = hostiles.indexOf(currentTarget);
            int nextIndex = (currentIndex + 1) % hostiles.size();
            currentTarget = hostiles.get(nextIndex);
        }
    }
    
    /**
     * Checks if the player is looking at a hostile entity
     */
    private static LivingEntity getLookTargetEntity() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return null;
        
        Vec3d playerPos = client.player.getEyePos();
        Vec3d lookVec = client.player.getRotationVec(1.0f);
        Vec3d targetVec = playerPos.add(lookVec.multiply(TARGET_RANGE));
        
        // Perform entity raycast
        EntityHitResult result = ProjectileUtil.getEntityCollision(
                client.world,
                client.player,
                playerPos,
                targetVec,
                new Box(playerPos, targetVec).expand(1.0),
                entity -> entity instanceof HostileEntity && entity.isAlive()
        );
        
        if (result != null && result.getEntity() instanceof LivingEntity) {
            return (LivingEntity) result.getEntity();
        }
        
        return null;
    }
    
    /**
     * Calculate where to aim to hit the target
     */
    private static Vec3d calculateAimPosition(LivingEntity target, ItemStack weapon) {
        if (target == null) return null;
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return null;
        
        Vec3d targetPos = target.getPos().add(0, target.getHeight() / 2, 0);
        Vec3d targetVelocity = new Vec3d(target.getVelocity().x, target.getVelocity().y, target.getVelocity().z);
        
        // Base projectile speed
        float projectileSpeed = 1.0f;
        
        // Calculate draw percentage for bow
        float drawPercentage = 1.0f;
        if (weapon.getItem() instanceof BowItem && client.player.isUsingItem()) {
            int useTicks = client.player.getItemUseTimeLeft();
            drawPercentage = BowItem.getPullProgress(useTicks);
        }
        
        // Apply power and other enchantments effects
        // This is a simplification - real implementation would check enchantments
        int powerLevel = 0; // Would be determined from enchantments
        float speedMultiplier = 1.0f + (0.5f * powerLevel / 5);
        
        // Adjust projectile speed based on weapon type and draw percentage
        if (weapon.getItem() instanceof BowItem) {
            projectileSpeed = 3.0f * drawPercentage * speedMultiplier;
        } else if (weapon.getItem() instanceof CrossbowItem) {
            projectileSpeed = 3.15f * speedMultiplier;
        }
        
        // Calculate distance to target
        double distance = client.player.getPos().distanceTo(targetPos);
        
        // Estimate time to hit
        double timeToHit = distance / projectileSpeed;
        
        // Calculate predicted position
        Vec3d predictedPos = targetPos.add(targetVelocity.multiply(timeToHit));
        
        // Account for gravity
        double gravity = 0.05;
        double verticalAdjustment = gravity * timeToHit * timeToHit / 2;
        predictedPos = predictedPos.add(0, verticalAdjustment, 0);
        
        return predictedPos;
    }
    
    /**
     * Check if current target is valid
     */
    private static boolean isTargetValid() {
        return currentTarget != null && currentTarget.isAlive();
    }
}