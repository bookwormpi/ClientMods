package org.bookwormpi.clientsidetesting.client.combat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.bookwormpi.clientsidetesting.client.ClientsidetestingClient;
import org.bookwormpi.clientsidetesting.client.targeting.TargetingSystem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CombatHudFeature {
    // Aim lock system variables
    private static boolean aimLockEnabled = false;

    // Visual settings
    private static final float BASE_SQUARE_SIZE = 0.5f;
    private static final float BASE_CIRCLE_SIZE = 0.3f;
    private static final float DISTANCE_SCALE_FACTOR = 0.08f; // Controls how quickly the size scales with distance
    private static final float MIN_SCALE = 0.3f; // Minimum scale to prevent tiny indicators
    private static final float MAX_SCALE = 2.0f; // Maximum scale to prevent huge indicators

    // Range settings
    private static final double BASE_TARGET_RANGE = 64.0;

    // Targeting system instance
    private static final TargetingSystem targetingSystem = new TargetingSystem();

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
        if (targetingSystem.isTargetValid()) {
            renderAimAssistCircle(matrices, vertexConsumers, cameraPos, heldItem);
            renderPredictedPath(matrices, vertexConsumers, cameraPos, heldItem);
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
            !hitEntity.equals(targetingSystem.getCurrentTarget()) && hitEntity.isAlive()) {
            // Player has hit a different living entity - switch target to it
            // Use targetingSystem logic if needed
        }

        // Handle aim lock if enabled
        if (aimLockEnabled && targetingSystem.isTargetValid()) {
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
     * Renders the predicted projectile path in the world using line segments.
     */
    private static void renderPredictedPath(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Vec3d cameraPos, ItemStack weapon) {
        List<Vec3d> path = targetingSystem.getPredictedPath(targetingSystem.getCurrentTarget(), weapon);
        if (path.size() < 2) return;
        VertexConsumer lines = vertexConsumers.getBuffer(RenderLayer.getLines());
        matrices.push();
        for (int i = 1; i < path.size(); i++) {
            Vec3d p0 = path.get(i - 1);
            Vec3d p1 = path.get(i);
            double x0 = p0.x - cameraPos.x;
            double y0 = p0.y - cameraPos.y;
            double z0 = p0.z - cameraPos.z;
            double x1 = p1.x - cameraPos.x;
            double y1 = p1.y - cameraPos.y;
            double z1 = p1.z - cameraPos.z;
            lines.vertex(matrices.peek().getPositionMatrix(), (float)x0, (float)y0, (float)z0)
                .color(1.0f, 1.0f, 0.0f, 0.8f)
                .normal(0.0f, 1.0f, 0.0f);
            lines.vertex(matrices.peek().getPositionMatrix(), (float)x1, (float)y1, (float)z1)
                .color(1.0f, 1.0f, 0.0f, 0.8f)
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
     * Handles the target cycle key press (now selects the mob closest to the player's crosshair within 5 degrees, not including bats)
     */
    public static void handleTargetCycleKeyPress() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        // Only allow selection if holding a bow or crossbow
        ItemStack heldItem = client.player.getMainHandStack();
        if (!(heldItem.getItem() instanceof BowItem || heldItem.getItem() instanceof CrossbowItem)) {
            return;
        }

        // Get all eligible mobs (excluding bats)
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
     * Cycles to the next target in the list
     */
    private static void cycleToNextTarget() {
        List<LivingEntity> entities = getAllLivingEntitiesInRange();
        if (entities.isEmpty()) {
            return;
        }

        LivingEntity currentTarget = targetingSystem.getCurrentTarget();
        if (currentTarget == null || !entities.contains(currentTarget)) {
            // If no current target or it's not in range anymore, choose the closest
            targetingSystem.setCurrentTarget(entities.get(0));
        } else {
            // Find current target's index and move to next
            int currentIndex = entities.indexOf(currentTarget);
            int nextIndex = (currentIndex + 1) % entities.size();
            targetingSystem.setCurrentTarget(entities.get(nextIndex));
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
            return;
        }

        // Toggle aim lock status
        aimLockEnabled = !aimLockEnabled;
    }

    /**
     * Updates player's view to look at the aim target
     */
    private static void updatePlayerViewToTarget(MinecraftClient client, ItemStack weapon) {
        if (!targetingSystem.isTargetValid()) return;
        List<Vec3d> dummyPath = new ArrayList<>();
        Vec3d aimPos = targetingSystem.calculateIdealAimPosition(targetingSystem.getCurrentTarget(), weapon, dummyPath);
        if (aimPos == null) return;
        Vec3d playerPos = client.player.getEyePos();
        Vec3d direction = aimPos.subtract(playerPos).normalize();
        double horizontalDistance = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float yaw = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, horizontalDistance));
        client.player.setYaw(yaw);
        client.player.setPitch(pitch);
    }
}