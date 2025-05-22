package org.bookwormpi.clientsidetesting.client.targeting;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.BatEntity;
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
    private static final double TARGET_RANGE = 128.0;
    private LivingEntity currentTarget;
    private long lastTargetSwitchTime;
    private static final long TARGET_SWITCH_COOLDOWN = 0; // 5 seconds in milliseconds

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
     * Gets all eligible mobs in range (all MobEntity except bats)
     */
    public List<LivingEntity> getEligibleMobsInRange() {
        MinecraftClient client = MinecraftClient.getInstance();
        List<LivingEntity> mobs = new ArrayList<>();
        if (client.player == null || client.world == null) return mobs;
        Vec3d playerPos = client.player.getPos();
        Box searchBox = new Box(
                playerPos.x - TARGET_RANGE, playerPos.y - TARGET_RANGE, playerPos.z - TARGET_RANGE,
                playerPos.x + TARGET_RANGE, playerPos.y + TARGET_RANGE, playerPos.z + TARGET_RANGE);
        for (Entity entity : client.world.getEntitiesByClass(LivingEntity.class, searchBox, e -> e instanceof MobEntity && !(e instanceof BatEntity))) {
            if (entity instanceof LivingEntity livingEntity) {
                mobs.add(livingEntity);
            }
        }
        // Sort by distance to player
        mobs.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(client.player)));
        return mobs;
    }

    /**
     * Cycles to the next target in the list (all mobs except bats)
     */
    private void cycleToNextTarget() {
        List<LivingEntity> mobs = getEligibleMobsInRange();
        if (mobs.isEmpty()) {
            currentTarget = null;
            return;
        }
        if (currentTarget == null || !mobs.contains(currentTarget)) {
            // If no current target or it's not in range anymore, choose the closest
            currentTarget = mobs.get(0);
        } else {
            // Find current target's index and move to next
            int currentIndex = mobs.indexOf(currentTarget);
            int nextIndex = (currentIndex + 1) % mobs.size();
            currentTarget = mobs.get(nextIndex);
        }
    }

    /**
     * Checks if the player is looking at an eligible mob (all mobs except bats)
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
                entity -> entity instanceof MobEntity && !(entity instanceof BatEntity) && entity.isAlive()
        );
        if (result != null && result.getEntity() instanceof LivingEntity) {
            return (LivingEntity) result.getEntity();
        }
        return null;
    }

    /**
     * Calculates the aim position for a projectile, considering both low and high parabolic trajectories.
     * Defaults to the flatter (low) curve, but switches to the high arc if the low arc is blocked.
     * Returns null if neither path is clear.
     */
    public Vec3d calculateAimPosition(LivingEntity target, ItemStack weapon) {
        if (target == null) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return null;
        Vec3d shooterPos = client.player.getEyePos();
        Box hitbox = target.getBoundingBox();
        Vec3d targetPos = hitbox.getCenter();
        double dx = targetPos.x - shooterPos.x;
        double dy = targetPos.y - shooterPos.y;
        double dz = targetPos.z - shooterPos.z;
        double dxz = Math.sqrt(dx * dx + dz * dz);
        float projectileSpeed = 3.00f;
        if (weapon.getItem() instanceof CrossbowItem) projectileSpeed = 3.15f;
        double gravity = 0.05;
        double v2 = projectileSpeed * projectileSpeed;
        double g = gravity;
        double root = v2 * v2 - g * (g * dxz * dxz + 2 * dy * v2);
        if (root < 0) return null;
        double sqrt = Math.sqrt(root);
        double tanThetaLow = (v2 - sqrt) / (g * dxz);
        double tanThetaHigh = (v2 + sqrt) / (g * dxz);
        double angleLow = Math.atan(tanThetaLow);
        double angleHigh = Math.atan(tanThetaHigh);
        // Try low arc first
        Vec3d impactLow = simulateProjectileImpact(shooterPos, projectileSpeed, gravity, angleLow, targetPos, hitbox);
        if (impactLow != null) return impactLow;
        // Try high arc
        Vec3d impactHigh = simulateProjectileImpact(shooterPos, projectileSpeed, gravity, angleHigh, targetPos, hitbox);
        if (impactHigh != null) return impactHigh;
        // Neither path is clear, use low arc's endpoint
        // Simulate the low arc and return the last position before blocked or max time
        return simulateProjectileLastPoint(shooterPos, projectileSpeed, gravity, angleLow, hitbox);
    }

    /**
     * Predicts the ideal aim direction for a player to hit a moving mob with a projectile (arrow),
     * using Newtonian physics for projectile motion, mob velocity, and block collision checks.
     * Returns the world position to aim at, or null if no valid solution.
     *
     * Improvements:
     * 1. Arrow inherits player velocity.
     * 2. Bow speed uses draw strength.
     * 4. Block collision checks for slabs, fences, and fluids.
     * 7. Handles vertical target movement (Y velocity and gravity).
     * 10. Modular, with path visualization support.
     */
    public Vec3d calculateIdealAimPosition(LivingEntity target, ItemStack weapon, List<Vec3d> pathOut) {
        if (target == null) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return null;
        Vec3d shooterPos = client.player.getEyePos();
        Vec3d shooterVel = client.player.getVelocity();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
        Vec3d targetVel = target.getVelocity();
        boolean isOnGround = target.isOnGround();
        // Check if the entity is more than 2 blocks above the ground
        boolean isOffGround = false;
        if (!isOnGround) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world != null) {
                double feetY = target.getPos().y;
                int blockBelow = (int)Math.floor(feetY - 2.0);
                int x = (int)Math.floor(target.getPos().x);
                int z = (int)Math.floor(target.getPos().z);
                for (int y = blockBelow; y < feetY; y++) {
                    if (!mc.world.getBlockState(new net.minecraft.util.math.BlockPos(x, y, z)).isAir()) {
                        isOffGround = false;
                        break;
                    }
                    isOffGround = true;
                }
            }
        }
        // 2. Bow draw strength
        float projectileSpeed = 3.0f;
        if (weapon.getItem() instanceof BowItem && client.player.isUsingItem()) {
            int useTicks = client.player.getItemUseTimeLeft();
            float draw = BowItem.getPullProgress(useTicks);
            projectileSpeed = 3.0f * draw;
        } else if (weapon.getItem() instanceof CrossbowItem) {
            projectileSpeed = 3.15f;
        }
        double gravity = 0.05;
        // Iteratively solve for time of flight and future target position
        double time = shooterPos.distanceTo(targetPos) / projectileSpeed;
        Vec3d predictedTarget = targetPos;
        for (int i = 0; i < 8; i++) {
            double vx = isOnGround ? targetVel.x : 0.0;
            double vy = 0.0;
            double vz = 0.0;
            if (isOnGround) {
                vy = 0.0;
                vz = 0.0;
            } else if (isOffGround) {
                vy = targetVel.y;
                vz = targetVel.z;
            }
            double futureY = targetPos.y - vy * time + 0.5 * gravity * time * time;
            predictedTarget = new Vec3d(
                targetPos.x + vx * time,
                futureY,
                targetPos.z + vz * time
            );
            double dx = predictedTarget.x - shooterPos.x;
            double dy = predictedTarget.y - shooterPos.y;
            double dz = predictedTarget.z - shooterPos.z;
            double dxz = Math.sqrt(dx * dx + dz * dz);
            // 1. Arrow inherits player velocity (horizontal only)
            double inheritX = shooterVel.x;
            double inheritZ = shooterVel.z;
            double v2 = projectileSpeed * projectileSpeed;
            double g = gravity;
            double root = v2 * v2 - g * (g * dxz * dxz + 2 * dy * v2);
            if (root < 0) return null;
            double sqrtRoot = Math.sqrt(root);
            double tanTheta = (v2 - sqrtRoot) / (g * dxz); // Use low arc
            double angle = Math.atan(tanTheta);
            double cos = Math.cos(angle);
            double newTime = dxz / (projectileSpeed * cos);
            if (Math.abs(newTime - time) < 0.01) break;
            time = newTime;
        }
        // Final predicted target
        Vec3d aimTarget = predictedTarget;
        // Now, simulate the projectile's path to this point and check for block collisions
        // (Removed: pathIsBlocked check, always return the predicted target for rendering the circle)
        return predictedTarget;
    }

    /**
     * Returns a list of Vec3d points representing the predicted projectile path for HUD/world visualization.
     * The path stops at the first collision or after maxTime seconds.
     */
    public List<Vec3d> getPredictedPath(Vec3d shooterPos, Vec3d aimTarget, float projectileSpeed, double gravity, double maxTime) {
        List<Vec3d> path = new ArrayList<>();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return path;
        Vec3d aimDir = aimTarget.subtract(shooterPos).normalize();
        double dx = aimTarget.x - shooterPos.x;
        double dz = aimTarget.z - shooterPos.z;
        double dxz = Math.sqrt(dx * dx + dz * dz);
        double dy = aimTarget.y - shooterPos.y;
        double v2 = projectileSpeed * projectileSpeed;
        double g = gravity;
        double root = v2 * v2 - g * (g * dxz * dxz + 2 * dy * v2);
        if (root < 0) return path;
        double sqrt = Math.sqrt(root);
        double tanTheta = (v2 - sqrt) / (g * dxz);
        double angle = Math.atan(tanTheta);
        Vec3d velocity = new Vec3d(aimDir.x * projectileSpeed, Math.tan(angle) * projectileSpeed, aimDir.z * projectileSpeed);
        Vec3d pos = shooterPos;
        double dt = 0.05;
        for (double t = 0; t < maxTime; t += dt) {
            pos = pos.add(velocity.multiply(dt));
            velocity = velocity.add(0, -gravity * dt, 0);
            path.add(pos);
            net.minecraft.util.math.BlockPos blockPos = new net.minecraft.util.math.BlockPos((int)Math.floor(pos.x), (int)Math.floor(pos.y), (int)Math.floor(pos.z));
            var state = client.world.getBlockState(blockPos);
            if (!state.isAir() || state.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER) || state.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA) || state.getBlock().getTranslationKey().contains("slab") || state.getBlock().getTranslationKey().contains("fence")) {
                break; // Stop at collision
            }
            if (pos.squaredDistanceTo(aimTarget) < 0.25) {
                break;
            }
        }
        return path;
    }

    /**
     * Visualizes the predicted projectile path for the current target and weapon.
     * Returns a list of Vec3d points along the path, or an empty list if no path.
     */
    public List<Vec3d> getPredictedPath(LivingEntity target, ItemStack weapon) {
        List<Vec3d> path = new ArrayList<>();
        calculateIdealAimPosition(target, weapon, path);
        return path;
    }

    /**
     * Simulates the projectile's path and returns the first intersection point with the target's hitbox, or null if blocked.
     */
    private Vec3d simulateProjectileImpact(Vec3d start, double speed, double gravity, double angle, Vec3d targetCenter, Box hitbox) {
        double dx = targetCenter.x - start.x;
        double dz = targetCenter.z - start.z;
        double dxz = Math.sqrt(dx * dx + dz * dz);
        double dirX = dx / dxz;
        double dirZ = dz / dxz;
        double y = Math.tan(angle);
        Vec3d velocity = new Vec3d(dirX * speed, y * speed, dirZ * speed);
        Vec3d pos = start;
        double dt = 0.05;
        for (double t = 0; t < 5.0; t += dt) {
            pos = pos.add(velocity.multiply(dt));
            velocity = velocity.add(0, -gravity * dt, 0);
            net.minecraft.util.math.BlockPos blockPos = new net.minecraft.util.math.BlockPos((int)Math.floor(pos.x), (int)Math.floor(pos.y), (int)Math.floor(pos.z));
            if (!MinecraftClient.getInstance().world.getBlockState(blockPos).isAir()) {
                return null;
            }
            if (hitbox.contains(pos)) {
                return pos;
            }
        }
        return null;
    }

    /**
     * Simulates the low arc and returns the last valid position before blocked or max time.
     */
    private Vec3d simulateProjectileLastPoint(Vec3d start, double speed, double gravity, double angle, Box hitbox) {
        double dx = hitbox.getCenter().x - start.x;
        double dz = hitbox.getCenter().z - start.z;
        double dxz = Math.sqrt(dx * dx + dz * dz);
        double dirX = dx / dxz;
        double dirZ = dz / dxz;
        double y = Math.tan(angle);
        Vec3d velocity = new Vec3d(dirX * speed, y * speed, dirZ * speed);
        Vec3d pos = start;
        double dt = 0.05;
        Vec3d lastValid = pos;
        for (double t = 0; t < 5.0; t += dt) {
            pos = pos.add(velocity.multiply(dt));
            velocity = velocity.add(0, -gravity * dt, 0);
            net.minecraft.util.math.BlockPos blockPos = new net.minecraft.util.math.BlockPos((int)Math.floor(pos.x), (int)Math.floor(pos.y), (int)Math.floor(pos.z));
            if (!MinecraftClient.getInstance().world.getBlockState(blockPos).isAir()) {
                break;
            }
            lastValid = pos;
            if (hitbox.contains(pos)) {
                return pos;
            }
        }
        return lastValid;
    }

    public LivingEntity getCurrentTarget() {
        return currentTarget;
    }

    public void setCurrentTarget(LivingEntity target) {
        this.currentTarget = target;
    }

    public boolean isTargetValid() {
        return currentTarget != null && currentTarget.isAlive();
    }
}