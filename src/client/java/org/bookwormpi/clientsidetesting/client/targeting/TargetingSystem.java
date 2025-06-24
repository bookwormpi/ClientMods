package org.bookwormpi.clientsidetesting.client.targeting;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
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

    public TargetingSystem() {
        this.currentTarget = null;
    }

    /**
     * Gets all eligible mobs and players in range (all MobEntity except bats, and all PlayerEntity)
     */
    public List<LivingEntity> getEligibleMobsInRange() {
        MinecraftClient client = MinecraftClient.getInstance();
        List<LivingEntity> entities = new ArrayList<>();

        if (client.player == null || client.world == null) return entities; // If not in a world, return an empty list.

        Vec3d playerPos = client.player.getPos();
        Box searchBox = new Box( // Create a box around the player using TARGET_RANGE
                playerPos.x - TARGET_RANGE, playerPos.y - TARGET_RANGE, playerPos.z - TARGET_RANGE,
                playerPos.x + TARGET_RANGE, playerPos.y + TARGET_RANGE, playerPos.z + TARGET_RANGE);

        for (Entity entity : client.world.getEntitiesByClass(LivingEntity.class, searchBox, 
                e -> (isTargetValid(e)))) {
            if (entity instanceof LivingEntity livingEntity) { // Make sure it's not dead.
                entities.add(livingEntity);
            }
        }
        // Sort by distance to player
        entities.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(client.player)));
        return entities;
    }


    /**
     * Predicts the ideal aim direction for a player to hit a moving mob with a projectile (arrow),
     * using Newtonian physics for projectile motion, mob velocity, and block collision checks.
     * Returns the world position to aim at, or null if no valid solution.
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
        for (int i = 0; i < 16; i++) {
            double vx, vy, vz;
            if (isOnGround) {
                // Ground targets: use X and Z velocity, Y velocity is 0 (on ground)
                // This ensures mobs jumping up blocks don't mess up the prediction.
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
        
        return predictedTarget;
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
        return !blockName.contains("grass") && // Grass doesn't stop arrows
               !blockName.contains("flower"); // Flowers don't stop arrows
    }


    public LivingEntity getCurrentTarget() {
        return currentTarget;
    }

    public void setCurrentTarget(LivingEntity target) {
        this.currentTarget = target;
    }

    public boolean isTargetValid(LivingEntity entity) {
        return entity != null && entity.isAlive() && !(entity instanceof BatEntity) || (entity instanceof PlayerEntity); // Ignore bats, but keep players
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