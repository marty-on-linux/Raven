package keystrokesmod.client.module.modules.movement;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.main.Raven;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.modules.combat.aura.KillAura;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * TargetStrafe module - Strafe around targets during combat
 * Inspired by OpenMyau's TargetStrafe implementation
 * 
 * Features:
 * - Automatic strafing around combat targets
 * - Configurable radius and points
 * - Direction switching on collision
 * - Integration with Speed/Fly modules
 */
public class TargetStrafe extends Module {
    
    public static SliderSetting radius;
    public static SliderSetting points;
    public static SliderSetting switchDistance;
    public static TickSetting requirePress;
    public static TickSetting speedOnly;
    public static TickSetting switchOnCollide;
    public static TickSetting renderPath;
    public static ComboSetting targetMode;
    
    private EntityLivingBase target = null;
    private float targetYaw = Float.NaN;
    private int direction = 1; // 1 = clockwise, -1 = counter-clockwise
    
    public TargetStrafeMode currentMode = TargetStrafeMode.KillAura;
    
    public TargetStrafe() {
        super("TargetStrafe", ModuleCategory.movement);
        
        this.registerSetting(new DescriptionSetting("Strafe Settings"));
        this.registerSetting(radius = new SliderSetting("Radius", 2.0D, 0.5D, 6.0D, 0.1D));
        this.registerSetting(points = new SliderSetting("Points", 8.0D, 3.0D, 24.0D, 1.0D));
        this.registerSetting(switchDistance = new SliderSetting("Switch Distance", 0.5D, 0.1D, 2.0D, 0.1D));
        
        this.registerSetting(new DescriptionSetting("Conditions"));
        this.registerSetting(requirePress = new TickSetting("Require movement key", true));
        this.registerSetting(speedOnly = new TickSetting("Speed/Fly only", true));
        this.registerSetting(switchOnCollide = new TickSetting("Switch on collide", true));
        
        this.registerSetting(new DescriptionSetting("Target"));
        this.registerSetting(targetMode = new ComboSetting("Target Mode", currentMode));
        
        this.registerSetting(new DescriptionSetting("Visual"));
        this.registerSetting(renderPath = new TickSetting("Render path", false));
    }
    
    @Override
    public void onEnable() {
        target = null;
        targetYaw = Float.NaN;
        direction = 1;
    }
    
    @Override
    public void onDisable() {
        target = null;
        targetYaw = Float.NaN;
    }
    
    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof LivingUpdateEvent)) return;
        if (!Utils.Player.isPlayerInGame()) return;
        
        // Check direction input
        boolean left = isMovingLeft();
        boolean right = isMovingRight();
        if (left ^ right) {
            direction = left ? 1 : -1;
        }
        
        // Switch direction on collision
        if (switchOnCollide.isToggled() && mc.thePlayer.isCollidedHorizontally) {
            direction = -direction;
        }
        
        // Check if we can strafe
        if (!canStrafe()) {
            target = null;
            targetYaw = Float.NaN;
            return;
        }
        
        // Get target
        target = getTarget();
        if (target == null) {
            targetYaw = Float.NaN;
            return;
        }
        
        // Calculate strafe points around target
        List<Vec3> strafePoints = calculateStrafePoints(target);
        if (strafePoints.isEmpty()) {
            targetYaw = Float.NaN;
            return;
        }
        
        // Find best strafe point
        Vec3 bestPoint = findBestStrafePoint(strafePoints);
        if (bestPoint == null) {
            targetYaw = Float.NaN;
            return;
        }
        
        // Calculate yaw to best point
        targetYaw = getYawToPoint(bestPoint);
    }
    
    private boolean canStrafe() {
        // Check speed/fly only condition
        if (speedOnly.isToggled()) {
            Module speed = Raven.moduleManager.getModuleByName("Speed");
            Module fly = Raven.moduleManager.getModuleByName("Fly");
            Module legitSpeed = Raven.moduleManager.getModuleByName("LegitSpeed");
            
            boolean hasSpeed = (speed != null && speed.isEnabled()) || 
                               (fly != null && fly.isEnabled()) ||
                               (legitSpeed != null && legitSpeed.isEnabled());
            
            if (!hasSpeed) {
                return false;
            }
        }
        
        // Check require press condition
        if (requirePress.isToggled()) {
            return isMovingForward() || isMovingLeft() || isMovingRight();
        }
        
        return true;
    }
    
    private EntityLivingBase getTarget() {
        TargetStrafeMode mode = TargetStrafeMode.values()[targetMode.getMode()];
        
        switch (mode) {
            case KillAura:
                // Get target from KillAura
                KillAura killAura = (KillAura) Raven.moduleManager.getModuleByClazz(KillAura.class);
                if (killAura != null && killAura.isEnabled()) {
                    Entity target = killAura.getTarget();
                    if (target instanceof EntityLivingBase) {
                        return (EntityLivingBase) target;
                    }
                }
                break;
                
            case Crosshair:
                // Get entity at crosshair
                if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit instanceof EntityLivingBase) {
                    return (EntityLivingBase) mc.objectMouseOver.entityHit;
                }
                break;
                
            case Nearest:
                // Find nearest valid entity
                return findNearestTarget();
        }
        
        return null;
    }
    
    private EntityLivingBase findNearestTarget() {
        EntityLivingBase nearest = null;
        double nearestDist = Double.MAX_VALUE;
        
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) continue;
            if (entity == mc.thePlayer) continue;
            if (entity.isDead) continue;
            if (!(entity instanceof EntityPlayer)) continue;
            
            double dist = mc.thePlayer.getDistanceToEntity(entity);
            if (dist < nearestDist && dist < radius.getInput() * 2) {
                nearest = (EntityLivingBase) entity;
                nearestDist = dist;
            }
        }
        
        return nearest;
    }
    
    private List<Vec3> calculateStrafePoints(EntityLivingBase target) {
        List<Vec3> strafePoints = new ArrayList<>();
        
        double r = radius.getInput();
        int numPoints = (int) points.getInput();
        
        for (int i = 0; i < numPoints; i++) {
            double angle = 2 * Math.PI * i / numPoints;
            double x = target.posX + r * Math.cos(angle);
            double z = target.posZ + r * Math.sin(angle);
            
            Vec3 point = new Vec3(x, target.posY, z);
            
            // Check if point is valid (not inside block)
            if (isValidStrafePoint(point)) {
                strafePoints.add(point);
            }
        }
        
        return strafePoints;
    }
    
    private boolean isValidStrafePoint(Vec3 point) {
        // Basic check - could be improved with block collision detection
        return point.yCoord >= 0 && point.yCoord < 256;
    }
    
    private Vec3 findBestStrafePoint(List<Vec3> strafePoints) {
        Vec3 playerPos = mc.thePlayer.getPositionVector();
        Vec3 bestPoint = null;
        double bestScore = Double.MIN_VALUE;
        
        for (Vec3 point : strafePoints) {
            double dist = playerPos.distanceTo(point);
            
            // Calculate direction preference
            double yawToPoint = getYawToPoint(point);
            double currentYaw = mc.thePlayer.rotationYaw;
            double yawDiff = MathHelper.wrapAngleTo180_float((float) (yawToPoint - currentYaw));
            
            // Prefer points in our current strafe direction
            double directionScore = (direction > 0) ? -yawDiff : yawDiff;
            
            // Score based on distance (prefer closer points) and direction
            double score = -dist + directionScore * 0.1;
            
            if (score > bestScore) {
                bestScore = score;
                bestPoint = point;
            }
        }
        
        return bestPoint;
    }
    
    private float getYawToPoint(Vec3 point) {
        double dx = point.xCoord - mc.thePlayer.posX;
        double dz = point.zCoord - mc.thePlayer.posZ;
        return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
    }
    
    /**
     * Get the target yaw for movement modification
     */
    public float getTargetYaw() {
        return targetYaw;
    }
    
    /**
     * Check if target strafe is active
     */
    public boolean isActive() {
        return this.isEnabled() && target != null && !Float.isNaN(targetYaw);
    }
    
    /**
     * Get the current target
     */
    public EntityLivingBase getTargetEntity() {
        return target;
    }
    
    // Input helpers
    private boolean isMovingForward() {
        return Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode());
    }
    
    private boolean isMovingLeft() {
        return Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode());
    }
    
    private boolean isMovingRight() {
        return Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode());
    }
    
    @Override
    public String getInfo() {
        if (target != null) {
            return String.format("%.1f", mc.thePlayer.getDistanceToEntity(target));
        }
        return null;
    }
    
    public enum TargetStrafeMode {
        KillAura,   // Use KillAura target
        Crosshair,  // Use entity at crosshair
        Nearest     // Use nearest player
    }
}
