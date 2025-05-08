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
     * Calculates the aim position accounting for target velocity and bow/crossbow properties
     */
    public Vec3d calculateAimPosition(LivingEntity target, ItemStack weapon) {
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
    
    public LivingEntity getCurrentTarget() {
        return currentTarget;
    }
    
    public boolean isTargetValid() {
        return currentTarget != null && currentTarget.isAlive();
    }
}