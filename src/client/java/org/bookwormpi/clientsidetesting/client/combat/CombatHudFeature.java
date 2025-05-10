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
    
    // Aim lock system variables
    private static boolean aimLockEnabled = false;
    
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

        // Render squares around all targetable entities
        renderEntitySquares(matrices, vertexConsumers, cameraPos);
        
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
        if (hitEntity instanceof LivingEntity && 
            !hitEntity.equals(currentTarget) && hitEntity.isAlive()) {
            // Player has hit a different living entity - switch target to it
            currentTarget = (LivingEntity) hitEntity;
            lastTargetSwitchTime = System.currentTimeMillis();
        }
        
        // Handle aim lock if enabled
        if (aimLockEnabled && isTargetValid()) {
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
        List<LivingEntity> entities = getAllLivingEntitiesInRange();
        for (LivingEntity entity : entities) {
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
        
        // Use different color when aim lock is enabled
        float red = aimLockEnabled ? 1.0f : 0.0f;
        float green = 1.0f;
        float blue = aimLockEnabled ? 1.0f : 0.0f;
        float alpha = 0.8f;
        
        // Draw a circle at the aim position
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
     * Gets all living entities in range (including players)
     */
    public static List<LivingEntity> getAllLivingEntitiesInRange() {
        MinecraftClient client = MinecraftClient.getInstance();
        List<LivingEntity> entities = new ArrayList<>();
        
        if (client.player == null || client.world == null) return entities;

        Vec3d playerPos = client.player.getPos();
        Box searchBox = new Box(
                playerPos.x - TARGET_RANGE, playerPos.y - TARGET_RANGE, playerPos.z - TARGET_RANGE,
                playerPos.x + TARGET_RANGE, playerPos.y + TARGET_RANGE, playerPos.z + TARGET_RANGE);
        
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
        List<LivingEntity> entities = getAllLivingEntitiesInRange();
        if (entities.isEmpty()) {
            currentTarget = null;
            return;
        }
        
        if (currentTarget == null || !entities.contains(currentTarget)) {
            // If no current target or it's not in range anymore, choose the closest
            currentTarget = entities.get(0);
        } else {
            // Find current target's index and move to next
            int currentIndex = entities.indexOf(currentTarget);
            int nextIndex = (currentIndex + 1) % entities.size();
            currentTarget = entities.get(nextIndex);
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
                entity -> entity instanceof LivingEntity && // Target any living entity
                          entity != client.player && // Don't target self
                          entity.isAlive() && // Only living entities
                          !entity.isSpectator() // Exclude spectators
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
        
        // Get target position (center of hitbox)
        Vec3d targetPos = target.getPos().add(0, target.getHeight() / 2, 0);
        Vec3d targetVelocity = new Vec3d(target.getVelocity().x, target.getVelocity().y, target.getVelocity().z);
        
        // Get player position and velocity
        Vec3d playerPos = client.player.getEyePos();
        Vec3d playerVelocity = client.player.getVelocity();
        
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
        
        // Estimate time to hit based on distance and projectile speed
        double timeToHit = distance / projectileSpeed;
        
        // Calculate relative velocity (how the target is moving relative to the player)
        Vec3d relativeVelocity = targetVelocity.subtract(playerVelocity);
        
        // Calculate predicted position based on relative movement
        Vec3d predictedPos = targetPos.add(relativeVelocity.multiply(timeToHit));
        
        // Improved gravity compensation
        // The longer the shot, the more drop needs to be compensated
        double gravity = 0.05;
        double verticalAdjustment = 0;
        
        if (weapon.getItem() instanceof BowItem) {
            // More accurate bow trajectory calculation
            // As draw percentage increases, gravity effect decreases
            double gravityFactor = (1.0 - drawPercentage) * 0.5 + 0.5; // 0.5 to 1.0
            verticalAdjustment = gravity * gravityFactor * timeToHit * timeToHit / 2;
        } else if (weapon.getItem() instanceof CrossbowItem) {
            // Crossbows are more accurate with less drop
            verticalAdjustment = gravity * 0.6 * timeToHit * timeToHit / 2;
        } else {
            // Default calculation
            verticalAdjustment = gravity * timeToHit * timeToHit / 2;
        }
        
        // Apply vertical adjustment (aim higher to compensate for gravity)
        predictedPos = predictedPos.add(0, verticalAdjustment, 0);
        
        // Check if the predicted position is obstructed
        if (isPositionObstructed(playerPos, predictedPos)) {
            // Try alternate positions on the target's hitbox
            Vec3d alternatePos = findAlternateAimPosition(target, playerPos, timeToHit, relativeVelocity, gravity);
            if (alternatePos != null) {
                return alternatePos;
            }
        }
        
        return predictedPos;
    }
    
    /**
     * Checks if there's an obstruction between two points
     */
    private static boolean isPositionObstructed(Vec3d from, Vec3d to) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return true;
        
        // Calculate direction vector
        Vec3d direction = to.subtract(from);
        double length = direction.length();
        if (length < 0.1) return false; // Too close, no obstruction
        
        Vec3d normalizedDir = direction.normalize();
        
        // Perform a manual raycast check
        for (double d = 0.1; d < length; d += 0.5) {
            Vec3d pos = from.add(normalizedDir.multiply(d));
            if (!client.world.isAir(new net.minecraft.util.math.BlockPos((int)pos.x, (int)pos.y, (int)pos.z))) {
                return true; // Found a non-air block, there's an obstruction
            }
        }
        
        return false; // No obstruction found
    }
    
    /**
     * Finds an alternate position on the hitbox that isn't obstructed
     */
    private static Vec3d findAlternateAimPosition(LivingEntity target, Vec3d playerPos, 
                                               double timeToHit, Vec3d relativeVelocity, double gravity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return null;
        
        // Get the target's hitbox dimensions
        float width = target.getWidth();
        float height = target.getHeight();
        
        // Create a list of points to check on the hitbox, starting from important areas
        List<Vec3d> hitboxPoints = new ArrayList<>();
        
        // Base position with future predicted movement
        Vec3d basePos = target.getPos().add(relativeVelocity.multiply(timeToHit));
        
        // Add center points at different heights (head, torso, legs)
        hitboxPoints.add(basePos.add(0, height * 0.8, 0)); // Head
        hitboxPoints.add(basePos.add(0, height * 0.5, 0)); // Torso (center priority)
        hitboxPoints.add(basePos.add(0, height * 0.2, 0)); // Legs
        
        // Add corner points and sides with more granularity
        float halfWidth = width / 2;
        for (double y = 0.1; y <= 0.9; y += 0.2) { // More granular height sampling
            double yPos = height * y;
            // Sides - cardinal directions
            hitboxPoints.add(basePos.add(halfWidth, yPos, 0));
            hitboxPoints.add(basePos.add(-halfWidth, yPos, 0));
            hitboxPoints.add(basePos.add(0, yPos, halfWidth));
            hitboxPoints.add(basePos.add(0, yPos, -halfWidth));
            
            // Corners
            hitboxPoints.add(basePos.add(halfWidth, yPos, halfWidth));
            hitboxPoints.add(basePos.add(halfWidth, yPos, -halfWidth));
            hitboxPoints.add(basePos.add(-halfWidth, yPos, halfWidth));
            hitboxPoints.add(basePos.add(-halfWidth, yPos, -halfWidth));
            
            // Add some intermediate points between center and edges (25% and 75% distance)
            float quarterWidth = halfWidth * 0.5f;
            hitboxPoints.add(basePos.add(quarterWidth, yPos, quarterWidth));
            hitboxPoints.add(basePos.add(quarterWidth, yPos, -quarterWidth));
            hitboxPoints.add(basePos.add(-quarterWidth, yPos, quarterWidth));
            hitboxPoints.add(basePos.add(-quarterWidth, yPos, -quarterWidth));
        }
        
        // Check each point
        for (Vec3d point : hitboxPoints) {
            // Calculate predicted position for this point
            Vec3d predictedPos = point;
            
            // Account for gravity
            double verticalAdjustment = gravity * timeToHit * timeToHit / 2;
            predictedPos = predictedPos.add(0, verticalAdjustment, 0);
            
            // Check if this position is unobstructed
            if (!isPositionObstructed(playerPos, predictedPos)) {
                return predictedPos;
            }
        }
        
        // If all points are obstructed, try to find a position player can see with raycast
        Vec3d visiblePoint = findVisiblePointWithRaycast(target, playerPos);
        if (visiblePoint != null) {
            return visiblePoint;
        }
        
        // As a last resort, try to calculate an arcing trajectory
        return calculateArcingTrajectory(target, playerPos, timeToHit, relativeVelocity, gravity);
    }
    
    /**
     * Finds any visible point on target using raycast
     */
    private static Vec3d findVisiblePointWithRaycast(LivingEntity target, Vec3d playerPos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return null;
        
        // Get target's hitbox dimensions
        float width = target.getWidth();
        float height = target.getHeight();
        Vec3d targetCenter = target.getPos().add(0, height / 2, 0);
        
        // Try multiple points around the entity in a spiral pattern
        // We'll create a sampling of points in concentric rings
        List<Vec3d> samplePoints = new ArrayList<>();
        
        // Center first - highest priority
        samplePoints.add(targetCenter);
        
        // Then try points around the center in a 3D spiral
        for (int ring = 1; ring <= 3; ring++) {
            double ringRadius = width * 0.3 * ring;
            int pointsInRing = 4 + (ring * 2); // More points in outer rings
            
            for (int i = 0; i < pointsInRing; i++) {
                double angle = (2 * Math.PI * i) / pointsInRing;
                
                // Create points at different heights
                for (double h = 0.2; h <= 0.8; h += 0.3) {
                    double y = target.getPos().y + (height * h);
                    double x = target.getPos().x + (Math.cos(angle) * ringRadius);
                    double z = target.getPos().z + (Math.sin(angle) * ringRadius);
                    
                    samplePoints.add(new Vec3d(x, y, z));
                }
            }
        }
        
        // Check each point for visibility
        for (Vec3d point : samplePoints) {
            if (!isPositionObstructed(playerPos, point)) {
                return point; // Found a visible point
            }
        }
        
        // If no point is directly visible, return the center position
        return targetCenter;
    }
    
    /**
     * Calculates an arcing trajectory when direct line of sight is blocked
     */
    private static Vec3d calculateArcingTrajectory(LivingEntity target, Vec3d playerPos, 
                                               double timeToHit, Vec3d relativeVelocity, double gravity) {
        // Get target position (center of hitbox)
        Vec3d targetPos = target.getPos().add(0, target.getHeight() / 2, 0)
                              .add(relativeVelocity.multiply(timeToHit));
        
        // Calculate horizontal distance
        double horizontalDist = Math.sqrt(
            Math.pow(targetPos.x - playerPos.x, 2) + 
            Math.pow(targetPos.z - playerPos.z, 2)
        );
        
        // Increase arc height based on distance
        double arcHeight = Math.min(5.0, horizontalDist * 0.3); // Higher arc for longer shots
        
        // Create a point above the target for arcing
        Vec3d arcedPos = targetPos.add(0, arcHeight, 0);
        
        // Return the arced position
        return arcedPos;
    }
    
    /**
     * Check if current target is valid
     */
    private static boolean isTargetValid() {
        return currentTarget != null && currentTarget.isAlive();
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
            return;
        }
        
        // Toggle aim lock status
        aimLockEnabled = !aimLockEnabled;
    }
    
    /**
     * Updates player's view to look at the aim target
     */
    private static void updatePlayerViewToTarget(MinecraftClient client, ItemStack weapon) {
        if (!isTargetValid()) return;
        
        // Calculate where to aim
        Vec3d aimPos = calculateAimPosition(currentTarget, weapon);
        if (aimPos == null) return;
        
        // Get player's position (eye level)
        Vec3d playerPos = client.player.getEyePos();
        
        // Calculate the direction vector from player to aim position
        Vec3d direction = aimPos.subtract(playerPos).normalize();
        
        // Convert direction vector to yaw and pitch
        double horizontalDistance = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float yaw = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, horizontalDistance));
        
        // Set player's rotation
        client.player.setYaw(yaw);
        client.player.setPitch(pitch);
    }
}