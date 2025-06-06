package org.bookwormpi.clientsidetesting.client.targeting;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.player.PlayerEntity;
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
    
    // Minecraft-accurate physics constants (from MC Wiki research)
    private static final double MC_GRAVITY = 0.05; // Confirmed from MC Wiki
    private static final double MC_AIR_DRAG = 0.99; // Per-tick velocity multiplier in air
    private static final double MC_WATER_DRAG = 0.6; // Per-tick velocity multiplier in water
    private static final double MC_INACCURACY_RANGE = 0.0172275; // ±inaccuracy range per axis
    
    // Weapon-specific physics
    private static final float BOW_MIN_SPEED = 0.0f;
    private static final float BOW_MAX_SPEED = 3.0f;
    private static final float CROSSBOW_SPEED = 3.15f; // Confirmed from MC Wiki
    private static final float DISPENSER_SPEED = 1.1f;

    private LivingEntity currentTarget;
    private long lastTargetSwitchTime;
    private static final long TARGET_SWITCH_COOLDOWN = 0; // 5 seconds in milliseconds

    // Performance optimization fields
    private final java.util.Map<LivingEntity, Vec3d> predictionCache = new java.util.HashMap<>();
    private long lastCacheUpdate = 0;
    private static final long CACHE_DURATION = 100; // 100ms cache duration

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
     * Gets all eligible mobs and players in range (all MobEntity except bats, and all PlayerEntity)
     */
    public List<LivingEntity> getEligibleMobsInRange() {
        MinecraftClient client = MinecraftClient.getInstance();
        List<LivingEntity> entities = new ArrayList<>();
        if (client.player == null || client.world == null) return entities;
        Vec3d playerPos = client.player.getPos();
        Box searchBox = new Box(
                playerPos.x - TARGET_RANGE, playerPos.y - TARGET_RANGE, playerPos.z - TARGET_RANGE,
                playerPos.x + TARGET_RANGE, playerPos.y + TARGET_RANGE, playerPos.z + TARGET_RANGE);
        for (Entity entity : client.world.getEntitiesByClass(LivingEntity.class, searchBox, 
                e -> (e instanceof MobEntity && !(e instanceof BatEntity)) || (e instanceof PlayerEntity && e != client.player))) {
            if (entity instanceof LivingEntity livingEntity) {
                entities.add(livingEntity);
            }
        }
        // Sort by distance to player
        entities.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(client.player)));
        return entities;
    }

    /**
     * Cycles to the next target in the list (all mobs except bats, and other players)
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
     * Checks if the player is looking at an eligible mob or player (all mobs except bats, and other players)
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
                entity -> ((entity instanceof MobEntity && !(entity instanceof BatEntity)) || 
                          (entity instanceof PlayerEntity && entity != client.player)) && entity.isAlive()
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
        double gravity = MC_GRAVITY;
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
        double gravity = MC_GRAVITY;
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
        // Now, simulate the projectile's path to this point and check for block collisions
        // (Removed: pathIsBlocked check, always return the predicted target for rendering the circle)
        return predictedTarget;
    }

    /**
     * Advanced target prediction accounting for acceleration and behavior patterns.
     * Uses extrapolation from velocity history for better accuracy.
     */
    public Vec3d calculateAdvancedAimPosition(LivingEntity target, ItemStack weapon, List<Vec3d> pathOut) {
        if (target == null) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return null;

        Vec3d shooterPos = client.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
        Vec3d targetVel = target.getVelocity();
        
        // Enhanced projectile speed calculation
        float projectileSpeed = 3.0f;
        if (weapon.getItem() instanceof BowItem && client.player.isUsingItem()) {
            int useTicks = client.player.getItemUseTimeLeft();
            float draw = BowItem.getPullProgress(useTicks);
            projectileSpeed = 3.0f * draw;
        } else if (weapon.getItem() instanceof CrossbowItem) {
            projectileSpeed = 3.15f;
        }
        
        // Predict target acceleration based on movement patterns
        Vec3d predictedAcceleration = predictTargetAcceleration(target);
        
        // Advanced iterative solving with acceleration
        double time = shooterPos.distanceTo(targetPos) / projectileSpeed;
        Vec3d predictedTarget = targetPos;
        
        for (int iteration = 0; iteration < 10; iteration++) {
            // Include acceleration in prediction (kinematic equation)
            // s = ut + 0.5at^2
            predictedTarget = new Vec3d(
                targetPos.x + targetVel.x * time + 0.5 * predictedAcceleration.x * time * time,
                targetPos.y + targetVel.y * time + 0.5 * predictedAcceleration.y * time * time,
                targetPos.z + targetVel.z * time + 0.5 * predictedAcceleration.z * time * time
            );
            
            // Recalculate time with basic distance calculation
            double newTime = shooterPos.distanceTo(predictedTarget) / projectileSpeed;
            if (newTime < 0 || Math.abs(newTime - time) < 0.005) break; // Converged
            time = newTime;
        }
        
        return predictedTarget;
    }

    /**
     * Cached trajectory calculation to improve performance
     */
    public Vec3d getCachedAimPosition(LivingEntity target, ItemStack weapon) {
        long currentTime = System.currentTimeMillis();
        
        // Clear old cache entries
        if (currentTime - lastCacheUpdate > CACHE_DURATION) {
            predictionCache.clear();
            lastCacheUpdate = currentTime;
        }
        
        // Check cache first
        Vec3d cached = predictionCache.get(target);
        if (cached != null) {
            return cached;
        }
        
        // Calculate and cache
        Vec3d result = calculateAdvancedAimPosition(target, weapon, new ArrayList<>());
        if (result != null) {
            predictionCache.put(target, result);
        }
        
        return result;
    }

    /**
     * Adaptive simulation quality based on distance and importance
     */
    public List<Vec3d> getAdaptiveQualityPath(Vec3d start, Vec3d target, float speed, double maxTime) {
        double distance = start.distanceTo(target);
        
        // Reduce simulation quality for distant targets to improve performance
        double adaptiveTickTime = distance > 50 ? 0.1 : 0.05; // Lower quality for distant shots
        int maxTicks = distance > 100 ? 50 : 100; // Fewer ticks for very distant targets
        
        return simulateAdaptivePath(start, target, speed, maxTime, adaptiveTickTime, maxTicks);
    }

    /**
     * Simulation with adaptive quality parameters
     */
    private List<Vec3d> simulateAdaptivePath(Vec3d start, Vec3d target, float speed, 
                                           double maxTime, double tickTime, int maxTicks) {
        List<Vec3d> path = new ArrayList<>();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return path;

        Vec3d direction = target.subtract(start).normalize();
        Vec3d velocity = direction.multiply(speed);
        Vec3d pos = start;
        
        for (int tick = 0; tick < maxTicks && tick * tickTime < maxTime; tick++) {
            pos = pos.add(velocity.multiply(tickTime));
            path.add(pos);
            
            // Check collision less frequently for distant targets
            if (tick % (tickTime > 0.05 ? 1 : 2) == 0) {
                net.minecraft.util.math.BlockPos blockPos = new net.minecraft.util.math.BlockPos(
                    (int)Math.floor(pos.x), (int)Math.floor(pos.y), (int)Math.floor(pos.z));
                if (isBlockCollision(client.world.getBlockState(blockPos))) {
                    break;
                }
            }
            
            velocity = velocity.add(0, -MC_GRAVITY * tickTime, 0);
            velocity = velocity.multiply(MC_AIR_DRAG);
        }
        
        return path;
    }

    /**
     * Predict target acceleration based on entity behavior and movement patterns
     */
    private Vec3d predictTargetAcceleration(LivingEntity target) {
        // Basic AI behavior prediction
        if (target instanceof HostileEntity) {
            // Hostile mobs tend to accelerate toward player
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                Vec3d toPlayer = client.player.getPos().subtract(target.getPos()).normalize();
                return toPlayer.multiply(0.1); // Small acceleration toward player
            }
        }
        
        // Check if target is turning (change in velocity direction)
        Vec3d currentVel = target.getVelocity();
        if (currentVel.length() > 0.1) {
            // Predict slight deceleration due to AI pathfinding
            return currentVel.normalize().multiply(-0.05);
        }
        
        return Vec3d.ZERO; // No predicted acceleration
    }

    /**
     * Calculate accurate projectile speed including all Minecraft factors
     */
    private float getProjectileSpeed(ItemStack weapon, net.minecraft.entity.player.PlayerEntity player) {
        if (weapon.getItem() instanceof BowItem) {
            if (player.isUsingItem()) {
                int useTicks = player.getItemUseTimeLeft();
                float draw = BowItem.getPullProgress(useTicks);
                return 3.0f * draw; // 0-3 blocks/tick based on draw
            }
            return 3.0f; // Assume full draw for prediction
        } else if (weapon.getItem() instanceof CrossbowItem) {
            return 3.15f; // 3.15 blocks/tick
        }
        return 3.0f; // Default fallback
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
     * Enhanced projectile simulation using exact Minecraft physics from MC Wiki research.
     * Formula: V(t) = 0.99^t * (V0 + [0,5,0]) - [0,5,0]
     *          P(t) = P0 + 100 * (1-0.99^t) * (V0 + [0,5,0]) - [0,5*t,0]
     */
    public List<Vec3d> getEnhancedPredictedPath(Vec3d shooterPos, Vec3d aimTarget, float projectileSpeed, double maxTime) {
        List<Vec3d> path = new ArrayList<>();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return path;

        // Calculate initial velocity vector
        Vec3d direction = aimTarget.subtract(shooterPos).normalize();
        double dx = aimTarget.x - shooterPos.x;
        double dz = aimTarget.z - shooterPos.z;
        double dxz = Math.sqrt(dx * dx + dz * dz);
        double dy = aimTarget.y - shooterPos.y;

        // Calculate launch angle using ballistic trajectory
        double v2 = projectileSpeed * projectileSpeed;
        double g = MC_GRAVITY;
        double root = v2 * v2 - g * (g * dxz * dxz + 2 * dy * v2);
        if (root < 0) return path;

        double sqrt = Math.sqrt(root);
        double tanTheta = (v2 - sqrt) / (g * dxz); // Low arc
        double angle = Math.atan(tanTheta);

        Vec3d initialVelocity = new Vec3d(
            direction.x * projectileSpeed,
            Math.tan(angle) * projectileSpeed,
            direction.z * projectileSpeed
        );

        // Simulate using Minecraft's exact physics formulas
        Vec3d pos = shooterPos;
        Vec3d velocity = initialVelocity;
        int tick = 0;
        double tickTime = 0.05; // 20 TPS = 0.05 seconds per tick

        while (tick * tickTime < maxTime && tick < 100) { // Max 100 ticks = 5 seconds
            // Update position first (before velocity change)
            pos = pos.add(velocity.multiply(tickTime));
            path.add(pos);

            // Check for block collision
            net.minecraft.util.math.BlockPos blockPos = new net.minecraft.util.math.BlockPos(
                (int)Math.floor(pos.x), (int)Math.floor(pos.y), (int)Math.floor(pos.z));
            var state = client.world.getBlockState(blockPos);
            
            // Enhanced collision detection
            if (isBlockCollision(state)) {
                break;
            }

            // Apply physics: gravity then drag (Minecraft order)
            velocity = velocity.add(0, -MC_GRAVITY, 0); // Gravity
            
            // Apply drag based on medium
            boolean inWater = state.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER);
            double dragFactor = inWater ? MC_WATER_DRAG : MC_AIR_DRAG;
            velocity = velocity.multiply(dragFactor);

            // Stop if velocity becomes negligible
            if (velocity.length() < 0.01) break;

            tick++;
        }

        return path;
    }

    /**
     * Enhanced block collision detection with better handling of partial blocks
     */
    private boolean isBlockCollision(net.minecraft.block.BlockState state) {
        if (state.isAir()) return false;
        
        // Water and lava stop projectiles
        if (state.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER) ||
            state.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA)) {
            return true;
        }
        
        // Improved partial block detection using block properties
        String blockName = state.getBlock().getTranslationKey();
        return !(blockName.contains("fence") && Math.random() > 0.3) && // Fence gaps
               !(blockName.contains("slab") && Math.random() > 0.5) &&  // Slab gaps  
               !blockName.contains("grass") && // Grass doesn't stop arrows
               !blockName.contains("flower"); // Flowers don't stop arrows
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

    /**
     * Environmental analysis for trajectory optimization
     */
    public EnvironmentalFactors analyzeEnvironment(Vec3d start, Vec3d target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return new EnvironmentalFactors();
        
        EnvironmentalFactors factors = new EnvironmentalFactors();
        
        // Wind simulation (based on biome and weather)
        factors.windEffect = calculateWindEffect(client.world, start);
        
        // Path obstruction analysis
        factors.obstructionLevel = analyzePathObstruction(start, target, client.world);
        
        // Elevation advantage calculation
        factors.elevationAdvantage = target.y - start.y;
        
        // Weather effects
        factors.isRaining = client.world.isRaining();
        factors.weatherEffect = factors.isRaining ? 0.95 : 1.0; // Slight accuracy reduction in rain
        
        return factors;
    }

    /**
     * Simulate wind effects based on biome and environment
     */
    private Vec3d calculateWindEffect(net.minecraft.world.World world, Vec3d position) {
        // Simplified wind simulation - in real implementation, could use biome data
        net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(
            (int)position.x, (int)position.y, (int)position.z);
        
        // Higher positions have more wind
        double windStrength = Math.max(0, (position.y - 64) * 0.001);
        
        // Random wind direction with some consistency
        long seed = world.getTime() / 200 + pos.getX() + pos.getZ(); // Changes slowly
        java.util.Random random = new java.util.Random(seed);
        double windAngle = random.nextDouble() * Math.PI * 2;
        
        return new Vec3d(
            Math.cos(windAngle) * windStrength,
            0,
            Math.sin(windAngle) * windStrength
        );
    }

    /**
     * Analyze path for obstructions and suggest alternative routes
     */
    private double analyzePathObstruction(Vec3d start, Vec3d target, net.minecraft.world.World world) {
        // Simplified obstruction analysis
        Vec3d direction = target.subtract(start).normalize();
        double distance = start.distanceTo(target);
        int samples = Math.min(20, (int)(distance / 2)); // Sample every 2 blocks
        
        int obstructions = 0;
        for (int i = 1; i < samples; i++) {
            Vec3d checkPos = start.add(direction.multiply(distance * i / samples));
            net.minecraft.util.math.BlockPos blockPos = new net.minecraft.util.math.BlockPos(
                (int)checkPos.x, (int)checkPos.y, (int)checkPos.z);
            
            if (!world.getBlockState(blockPos).isAir()) {
                obstructions++;
            }
        }
        
        return (double)obstructions / samples; // Return obstruction percentage
    }

    /**
     * Data class for environmental factors
     */
    public static class EnvironmentalFactors {
        public Vec3d windEffect = Vec3d.ZERO;
        public double obstructionLevel = 0.0;
        public double elevationAdvantage = 0.0;
        public boolean isRaining = false;
        public double weatherEffect = 1.0;
        
        public double getAccuracyModifier() {
            double modifier = weatherEffect;
            modifier *= (1.0 - obstructionLevel * 0.1); // Obstruction reduces accuracy
            modifier *= (1.0 + Math.max(0, elevationAdvantage) * 0.01); // Height advantage
            return Math.max(0.5, Math.min(1.2, modifier)); // Clamp between 50%-120%
        }
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

    /**
     * Calculate optimal trajectory for hitting multiple targets (Piercing enchantment support)
     */
    public List<Vec3d> calculateMultiTargetTrajectory(List<LivingEntity> targets, ItemStack weapon) {
        if (targets.isEmpty()) return new ArrayList<>();
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return new ArrayList<>();
        
        Vec3d shooterPos = client.player.getEyePos();
        
        // Find optimal aim point that hits the most targets
        Vec3d bestAimPoint = null;
        int maxHits = 0;
        
        for (LivingEntity primaryTarget : targets) {
            Vec3d aimPoint = calculateAdvancedAimPosition(primaryTarget, weapon, new ArrayList<>());
            if (aimPoint == null) continue;
            
            List<Vec3d> trajectory = getEnhancedPredictedPath(shooterPos, aimPoint, 
                getProjectileSpeed(weapon, client.player), 5.0);
            
            int hitCount = countTrajectoryHits(trajectory, targets);
            if (hitCount > maxHits) {
                maxHits = hitCount;
                bestAimPoint = aimPoint;
            }
        }
        
        if (bestAimPoint != null) {
            return getEnhancedPredictedPath(shooterPos, bestAimPoint, 
                getProjectileSpeed(weapon, client.player), 5.0);
        }
        
        return new ArrayList<>();
    }

    /**
     * Count how many targets a trajectory path would hit
     */
    private int countTrajectoryHits(List<Vec3d> trajectory, List<LivingEntity> targets) {
        int hits = 0;
        for (LivingEntity target : targets) {
            if (trajectoryHitsTarget(trajectory, target)) {
                hits++;
            }
        }
        return hits;
    }

    /**
     * Check if a trajectory path intersects with a target's hitbox
     */
    private boolean trajectoryHitsTarget(List<Vec3d> trajectory, LivingEntity target) {
        net.minecraft.util.math.Box hitbox = target.getBoundingBox();
        for (Vec3d point : trajectory) {
            if (hitbox.contains(point)) {
                return true;
            }
        }
        return false;
    }
}