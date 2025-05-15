package org.bookwormpi.clientsidetesting.client.targeting;

import net.minecraft.client.MinecraftClient;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TargetingSystem {
    private static final double TARGET_RANGE = 64.0;
    private LivingEntity currentTarget;
    private long lastTargetSwitchTime;
    private static final long TARGET_SWITCH_COOLDOWN = 5000; // 5 seconds in milliseconds

    public TargetingSystem() {
        this.currentTarget = null;
        this.lastTargetSwitchTime = 0;
    }

    /**
     * Handles when the player presses the targeting key
     */
    public void handleTargetCycleKeyPress() {
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
     * Gets all hostile entities in range
     */
    public List<LivingEntity> getHostileEntitiesInRange() {
        MinecraftClient client = MinecraftClient.getInstance();
        List<LivingEntity> hostiles = new ArrayList<>();
        if (client.player == null || client.world == null) return hostiles;
        Vec3d playerPos = client.player.getPos();
        Box searchBox = new Box(
                playerPos.x - TARGET_RANGE, playerPos.y - TARGET_RANGE, playerPos.z - TARGET_RANGE,
                playerPos.x + TARGET_RANGE, playerPos.y + TARGET_RANGE, playerPos.z + TARGET_RANGE);
        for (Entity entity : client.world.getEntitiesByClass(LivingEntity.class, searchBox, e -> e instanceof HostileEntity)) {
            if (entity instanceof LivingEntity livingEntity) {
                hostiles.add(livingEntity);
            }
        }
        // Sort by distance to player
        hostiles.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(client.player)));
        return hostiles;
    }

    /**
     * Cycles to the next target in the list
     */
    private void cycleToNextTarget() {
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
    private LivingEntity getLookTargetEntity() {
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
     * Calculates the aim position accounting for target velocity, shooter velocity, and bow/crossbow properties.
     * Now samples the hitbox center, then random points, but prioritizes points visible from the player's eye (line of sight).
     * Attempts up to maxTries times. If all fail, returns the best found (or center).
     */
    public Vec3d calculateAimPosition(LivingEntity target, ItemStack weapon) {
        if (target == null) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return null;
        Vec3d shooterPos = client.player.getEyePos();
        Vec3d shooterVel = client.player.getVelocity();
        Box hitbox = target.getBoundingBox();
        int maxTries = 7;
        Vec3d bestPoint = null;
        for (int attempt = 0; attempt < maxTries; attempt++) {
            Vec3d targetPoint;
            Vec3d targetVelocity = new Vec3d(target.getVelocity().x, target.getVelocity().y, target.getVelocity().z);
            float projectileSpeed = 1.0f;
            float drawPercentage = 1.0f;
            if (weapon.getItem() instanceof BowItem && client.player.isUsingItem()) {
                int useTicks = client.player.getItemUseTimeLeft();
                drawPercentage = BowItem.getPullProgress(useTicks);
            }
            int powerLevel = 0; // Would be determined from enchantments
            float speedMultiplier = 1.0f + (0.5f * powerLevel / 5);
            if (weapon.getItem() instanceof BowItem) {
                projectileSpeed = 3.0f * drawPercentage * speedMultiplier;
            } else if (weapon.getItem() instanceof CrossbowItem) {
                projectileSpeed = 3.15f * speedMultiplier;
            }
            double gravity = 0.05;
            double timeToHit;
            if (attempt == 0) {
                // Predict target's future position for the first attempt
                timeToHit = solveProjectileTimeWithShooterVelocity(
                    shooterPos, shooterVel, hitbox.getCenter(), targetVelocity, projectileSpeed, gravity
                );
                targetPoint = hitbox.getCenter().add(targetVelocity.multiply(timeToHit));
            } else {
                double rx = hitbox.minX + Math.random() * (hitbox.maxX - hitbox.minX);
                double ry = hitbox.minY + Math.random() * (hitbox.maxY - hitbox.minY);
                double rz = hitbox.minZ + Math.random() * (hitbox.maxZ - hitbox.minZ);
                targetPoint = new Vec3d(rx, ry, rz);
                // For random points, recalculate timeToHit for that point
                timeToHit = solveProjectileTimeWithShooterVelocity(
                    shooterPos, shooterVel, targetPoint, targetVelocity, projectileSpeed, gravity
                );
            }
            // Check line of sight from shooter's eye to this point
            if (!hasLineOfSight(client, shooterPos, targetPoint)) continue;
            Vec3d predictedTarget = targetPoint.add(targetVelocity.multiply(timeToHit));
            Vec3d delta = predictedTarget.subtract(shooterPos);
            Vec3d effectiveDelta = delta.subtract(shooterVel.multiply(timeToHit));
            double dxz = Math.sqrt(effectiveDelta.x * effectiveDelta.x + effectiveDelta.z * effectiveDelta.z);
            double dy = effectiveDelta.y;
            double v = projectileSpeed;
            double g = gravity;
            double v2 = v * v;
            double root = v2 * v2 - g * (g * dxz * dxz + 2 * dy * v2);
            if (root < 0) continue; // No valid solution, try another point
            double sqrt = Math.sqrt(root);
            double tanTheta = (v2 - sqrt) / (g * dxz);
            double angle = Math.atan(tanTheta);
            double aimDx = effectiveDelta.x / dxz;
            double aimDz = effectiveDelta.z / dxz;
            double aimY = Math.tan(angle);
            Vec3d aimDir = new Vec3d(aimDx, aimY, aimDz).normalize();
            Vec3d aimPoint = shooterPos.add(aimDir.multiply(dxz)).add(shooterVel.multiply(timeToHit));
            // Simulate the projectile path to check for block collision
            if (isProjectilePathClear(client, shooterPos, aimDir, v, shooterVel, g, timeToHit, predictedTarget)) {
                return aimPoint;
            }
            if (bestPoint == null) bestPoint = aimPoint;
        }
        return bestPoint;
    }

    /**
     * Checks if there is a direct line of sight (no blocks) from start to end.
     * Uses the player's actual eye position for start, and the exact target point for end.
     */
    private boolean hasLineOfSight(MinecraftClient client, Vec3d start, Vec3d end) {
        if (client.world == null) return false;
        // Use the player's actual eye position for start, and the exact target point for end
        Vec3d dir = end.subtract(start);
        double len = dir.length();
        if (len < 0.1) return true;
        Vec3d norm = dir.normalize();
        for (double d = 0; d < len; d += 0.25) {
            Vec3d pos = start.add(norm.multiply(d));
            net.minecraft.util.math.BlockPos blockPos = new net.minecraft.util.math.BlockPos(
                (int)Math.floor(pos.x), (int)Math.floor(pos.y), (int)Math.floor(pos.z)
            );
            if (!client.world.getBlockState(blockPos).isAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Simulates the projectile's path and checks if it is clear of blocks up to the target point.
     * Returns true if the path is clear, false if a block is hit.
     */
    private boolean isProjectilePathClear(MinecraftClient client, Vec3d start, Vec3d aimDir, double speed, Vec3d shooterVel, double gravity, double time, Vec3d target) {
        if (client.world == null) return false;
        // Simulate the projectile's path in small steps
        double dt = 0.1;
        Vec3d velocity = aimDir.multiply(speed).add(shooterVel);
        Vec3d pos = start;
        for (double t = 0; t < time; t += dt) {
            // Update position and velocity
            pos = pos.add(velocity.multiply(dt));
            velocity = velocity.add(0, -gravity * dt, 0);
            // Check for block collision
            net.minecraft.util.math.BlockPos blockPos = new net.minecraft.util.math.BlockPos((int)Math.floor(pos.x), (int)Math.floor(pos.y), (int)Math.floor(pos.z));
            if (!client.world.getBlockState(blockPos).isAir()) {
                return false;
            }
            // Early exit if we've passed the target
            if (pos.squaredDistanceTo(target) < 0.25) break;
        }
        return true;
    }

    /**
     * Solves for the time it will take a projectile (with shooter velocity) to hit a moving target, accounting for gravity.
     * Uses an iterative approach, updating the future target position and the effective projectile velocity each iteration.
     */
    private double solveProjectileTimeWithShooterVelocity(Vec3d shooterPos, Vec3d shooterVel, Vec3d targetPos, Vec3d targetVel, double speed, double gravity) {
        // Initial guess: direct distance divided by (projectile speed + shooter's velocity projected toward target)
        Vec3d relVel = targetVel.subtract(shooterVel);
        double time = shooterPos.distanceTo(targetPos) / (speed + relVel.length());
        for (int i = 0; i < 5; i++) {
            Vec3d futureTarget = targetPos.add(targetVel.multiply(time));
            Vec3d delta = futureTarget.subtract(shooterPos);
            // Remove the shooter's velocity component from the delta, since the projectile will inherit it
            Vec3d effectiveDelta = delta.subtract(shooterVel.multiply(time));
            double dxz = Math.sqrt(effectiveDelta.x * effectiveDelta.x + effectiveDelta.z * effectiveDelta.z);
            double dy = effectiveDelta.y;
            double v = speed;
            double g = gravity;
            double v2 = v * v;
            double root = v2 * v2 - g * (g * dxz * dxz + 2 * dy * v2);
            if (root < 0) {
                // No valid solution, fallback to direct distance
                time = Math.sqrt(dxz * dxz + dy * dy) / v;
                break;
            }
            double sqrt = Math.sqrt(root);
            double tanTheta = (v2 - sqrt) / (g * dxz);
            double angle = Math.atan(tanTheta);
            double cos = Math.cos(angle);
            double newTime = dxz / (v * cos);
            if (Math.abs(newTime - time) < 0.01) break;
            time = newTime;
        }
        return time;
    }

    public LivingEntity getCurrentTarget() {
        return currentTarget;
    }

    public boolean isTargetValid() {
        return currentTarget != null && currentTarget.isAlive();
    }
}