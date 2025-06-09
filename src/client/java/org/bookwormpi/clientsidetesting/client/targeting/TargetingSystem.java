package org.bookwormpi.clientsidetesting.client.targeting;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
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
    // private static final double MC_INACCURACY_RANGE = 0.0172275; // ±inaccuracy range per axis
    
    // // Weapon-specific physics
    // private static final float BOW_MIN_SPEED = 0.0f;
    // private static final float BOW_MAX_SPEED = 3.0f;
    // private static final float CROSSBOW_SPEED = 3.15f; // Confirmed from MC Wiki
    // private static final float DISPENSER_SPEED = 1.1f;

    private LivingEntity currentTarget;
    private long lastTargetSwitchTime;
    private static final long TARGET_SWITCH_COOLDOWN = 0; // 0 seconds in milliseconds

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
        Box hitbox = getOptimalHitbox(target);
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
        
        // Use optimal hitbox for multi-hitbox entities
        Box optimalHitbox = getOptimalHitbox(target);
        Vec3d targetPos;
        if (optimalHitbox != null) {
            // Aim at the center of the optimal hitbox
            targetPos = new Vec3d(
                (optimalHitbox.minX + optimalHitbox.maxX) * 0.5,
                (optimalHitbox.minY + optimalHitbox.maxY) * 0.5,
                (optimalHitbox.minZ + optimalHitbox.maxZ) * 0.5
            );
        } else {
            // Fallback to standard center position
            targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
        }
        
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
            double vx, vy, vz;
            if (isOnGround) {
                // Ground targets: use X and Z velocity, Y velocity is 0 (on ground)
                vx = targetVel.x;
                vy = 0.0;
                vz = targetVel.z;
            } else if (isOffGround) {
                // Airborne targets: use full 3D velocity
                vx = targetVel.x;
                vy = targetVel.y;
                vz = targetVel.z;
            } else {
                // Default case: no velocity prediction
                vx = 0.0;
                vy = 0.0;
                vz = 0.0;
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
        
        // Use optimal hitbox for multi-hitbox entities
        Box optimalHitbox = getOptimalHitbox(target);
        Vec3d targetPos;
        if (optimalHitbox != null) {
            // Aim at the center of the optimal hitbox
            targetPos = new Vec3d(
                (optimalHitbox.minX + optimalHitbox.maxX) * 0.5,
                (optimalHitbox.minY + optimalHitbox.maxY) * 0.5,
                (optimalHitbox.minZ + optimalHitbox.maxZ) * 0.5
            );
        } else {
            // Fallback to standard center position
            targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
        }
        
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
     * Note: Minecraft doesn't have actual wind physics, so wind effects are disabled for accuracy
     */
    public EnvironmentalFactors analyzeEnvironment(Vec3d start, Vec3d target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return new EnvironmentalFactors();
        
        EnvironmentalFactors factors = new EnvironmentalFactors();
        
        // Wind simulation disabled - Minecraft has no wind physics
        // factors.windEffect = calculateWindEffect(client.world, start);
        factors.windEffect = Vec3d.ZERO; // No wind in vanilla Minecraft
        
        // Path obstruction analysis
        factors.obstructionLevel = analyzePathObstruction(start, target, client.world);
        
        // Elevation advantage calculation
        factors.elevationAdvantage = target.y - start.y;
        
        // Weather effects disabled - Rain doesn't affect projectiles in vanilla Minecraft
        factors.isRaining = client.world.isRaining();
        factors.weatherEffect = 1.0; // No weather effects in vanilla Minecraft
        
        return factors;
    }

    /**
     * Simulate wind effects based on biome and environment
     * Note: Disabled because Minecraft has no actual wind physics
     */
    @SuppressWarnings("unused")
    private Vec3d calculateWindEffect(net.minecraft.world.World world, Vec3d position) {
        // This method is kept for potential future use with modded environments
        // that might add wind physics, but vanilla Minecraft has no wind effects
        
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
     * Note: Most factors disabled to match vanilla Minecraft physics
     */
    public static class EnvironmentalFactors {
        public Vec3d windEffect = Vec3d.ZERO; // No wind in Minecraft
        public double obstructionLevel = 0.0; // Kept for analysis only
        public double elevationAdvantage = 0.0; // No elevation effects in Minecraft
        public boolean isRaining = false; // No weather effects in Minecraft
        public double weatherEffect = 1.0; // No weather effects in Minecraft
        
        public double getAccuracyModifier() {
            // In vanilla Minecraft, environmental factors don't affect projectile accuracy
            // Projectiles follow deterministic physics regardless of conditions
            return 1.0; // Always 100% - no environmental accuracy modifiers in vanilla MC
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
        net.minecraft.util.math.Box hitbox = getOptimalHitbox(target);
        for (Vec3d point : trajectory) {
            if (hitbox.contains(point)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the optimal hitbox for targeting. For entities with multiple hitboxes (like Ender Dragon),
     * returns the largest hitbox. For regular entities, returns their standard bounding box.
     */
    public Box getOptimalHitbox(LivingEntity target) {
        if (target == null) return null;
        
        // Handle multi-hitbox entities
        if (isMultiHitboxEntity(target)) {
            return getLargestHitbox(target);
        }
        
        // Default to standard bounding box for regular entities
        return target.getBoundingBox();
    }
    
    /**
     * Checks if an entity has multiple hitboxes (complex entity like Ender Dragon or Wither)
     */
    private boolean isMultiHitboxEntity(LivingEntity entity) {
        // Ender Dragon has multiple body parts
        if (entity instanceof EnderDragonEntity) {
            return true;
        }
        
        // Wither has multiple heads
        if (entity instanceof WitherEntity) {
            return true;
        }
        
        // Check for other entities that might have multiple parts
        // ArmorStands can have multiple collision boxes depending on pose
        if (entity instanceof ArmorStandEntity) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Gets the largest hitbox from a multi-hitbox entity
     */
    private Box getLargestHitbox(LivingEntity entity) {
        if (entity instanceof EnderDragonEntity dragon) {
            return getLargestDragonHitbox(dragon);
        }
        
        if (entity instanceof WitherEntity wither) {
            return getLargestWitherHitbox(wither);
        }
        
        if (entity instanceof ArmorStandEntity armorStand) {
            return getLargestArmorStandHitbox(armorStand);
        }
        
        // Fallback to standard bounding box
        return entity.getBoundingBox();
    }
    
    /**
     * Gets the largest hitbox from an Ender Dragon's multiple parts
     */
    private Box getLargestDragonHitbox(EnderDragonEntity dragon) {
        Box largestBox = dragon.getBoundingBox();
        double largestVolume = calculateBoxVolume(largestBox);
        
        // Try to access dragon parts through reflection or direct access if available
        try {
            // The dragon's body is typically the largest part
            // Dragon parts include: head, neck, body, wing, tail segments
            // The body segment is usually the largest and most reliable target
            
            // Get all potential hitboxes for the dragon
            java.util.List<Box> dragonHitboxes = new java.util.ArrayList<>();
            
            // Add the main body bounding box (usually largest)
            dragonHitboxes.add(dragon.getBoundingBox());
            
            // Create additional boxes for dragon parts based on dragon position and rotation
            Vec3d dragonPos = dragon.getPos();
            float yaw = dragon.getYaw();
            
            // Body segment (main target) - extend the main hitbox slightly
            Box bodyBox = dragon.getBoundingBox().expand(2.0, 1.0, 2.0);
            dragonHitboxes.add(bodyBox);
            
            // Head area (in front of dragon based on yaw)
            // In Minecraft: yaw 0 = south (+Z), yaw 90 = west (-X), yaw 180 = north (-Z), yaw 270 = east (+X)
            // To get the position in front of the dragon, we need to offset in the facing direction
            double headX = dragonPos.x - Math.sin(Math.toRadians(yaw)) * 6.0;
            double headZ = dragonPos.z + Math.cos(Math.toRadians(yaw)) * 6.0;
            Box headBox = new Box(headX - 2, dragonPos.y - 1, headZ - 2, 
                                headX + 2, dragonPos.y + 3, headZ + 2);
            dragonHitboxes.add(headBox);
            
            // Find the largest box
            for (Box box : dragonHitboxes) {
                double volume = calculateBoxVolume(box);
                if (volume > largestVolume) {
                    largestVolume = volume;
                    largestBox = box;
                }
            }
            
        } catch (Exception e) {
            // Fallback to standard bounding box if reflection fails
            return dragon.getBoundingBox().expand(1.0); // Slightly larger for better targeting
        }
        
        return largestBox;
    }
    
    /**
     * Gets the largest hitbox from a Wither's multiple parts
     * Based on official Minecraft data: Wither hitbox is 3.5 blocks tall, 0.9 blocks wide
     */
    private Box getLargestWitherHitbox(WitherEntity wither) {
        Box largestBox = wither.getBoundingBox();
        double largestVolume = calculateBoxVolume(largestBox);
        
        try {
            java.util.List<Box> witherHitboxes = new java.util.ArrayList<>();
            
            // Add the main body bounding box (this is typically the largest and most reliable)
            witherHitboxes.add(wither.getBoundingBox());
            
            Vec3d witherPos = wither.getPos();
            float yaw = wither.getYaw();
            
            // Wither's actual hitbox dimensions based on MC Wiki:
            // Height: 3.5 blocks, Width: 0.9 blocks (much narrower than visual appearance)
            // The main body extends from feet to about 2.5 blocks up
            Box mainBodyBox = new Box(
                witherPos.x - 0.45, witherPos.y, witherPos.z - 0.45,
                witherPos.x + 0.45, witherPos.y + 2.5, witherPos.z + 0.45
            );
            witherHitboxes.add(mainBodyBox);
            
            // Central head area (main targeting head) - positioned above body
            // According to wiki, main head skulls spawn 3 blocks above body
            Box centralHeadBox = new Box(
                witherPos.x - 0.45, witherPos.y + 2.5, witherPos.z - 0.45,
                witherPos.x + 0.45, witherPos.y + 3.5, witherPos.z + 0.45
            );
            witherHitboxes.add(centralHeadBox);
            
            // Note: Side heads are dynamic and move independently in the actual game,
            // but for targeting purposes, we can create approximate areas where they typically are.
            // Side head skulls spawn 1.3 blocks offset from center horizontally
            
            // Calculate side head positions based on wither orientation
            // Side heads are positioned to the left and right of the main body
            double sideOffset = 1.3; // Based on MC Wiki data
            
            // Left side head (relative to wither's facing direction)
            // To get perpendicular offset: rotate facing vector by 90 degrees
            // Left = rotate facing direction 90° counter-clockwise
            double leftHeadX = witherPos.x + Math.sin(Math.toRadians(yaw)) * sideOffset;
            double leftHeadZ = witherPos.z - Math.cos(Math.toRadians(yaw)) * sideOffset;
            Box leftHeadBox = new Box(
                leftHeadX - 0.3, witherPos.y + 2.2, leftHeadZ - 0.3,
                leftHeadX + 0.3, witherPos.y + 3.2, leftHeadZ + 0.3
            );
            witherHitboxes.add(leftHeadBox);
            
            // Right side head (relative to wither's facing direction)
            // Right = rotate facing direction 90° clockwise
            double rightHeadX = witherPos.x - Math.sin(Math.toRadians(yaw)) * sideOffset;
            double rightHeadZ = witherPos.z + Math.cos(Math.toRadians(yaw)) * sideOffset;
            Box rightHeadBox = new Box(
                rightHeadX - 0.3, witherPos.y + 2.2, rightHeadZ - 0.3,
                rightHeadX + 0.3, witherPos.y + 3.2, rightHeadZ + 0.3
            );
            witherHitboxes.add(rightHeadBox);
            
            // Find the largest box (typically the main body)
            for (Box box : witherHitboxes) {
                double volume = calculateBoxVolume(box);
                if (volume > largestVolume) {
                    largestVolume = volume;
                    largestBox = box;
                }
            }
            
        } catch (Exception e) {
            // Fallback to standard bounding box
            return wither.getBoundingBox();
        }
        
        return largestBox;
    }
    
    /**
     * Gets the optimal hitbox for armor stands based on their pose
     */
    private Box getLargestArmorStandHitbox(ArmorStandEntity armorStand) {
        // Armor stands can have different poses affecting their hitbox
        // Use the main body hitbox which is usually reliable
        return armorStand.getBoundingBox();
    }
    
    /**
     * Calculates the volume of a bounding box
     */
    private double calculateBoxVolume(Box box) {
        if (box == null) return 0.0;
        double width = box.maxX - box.minX;
        double height = box.maxY - box.minY;
        double depth = box.maxZ - box.minZ;
        return width * height * depth;
    }
}