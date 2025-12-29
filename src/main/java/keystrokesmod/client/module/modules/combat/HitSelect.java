package keystrokesmod.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;

/**
 * HitSelect module - Prioritizes optimal hit timing for maximum knockback
 * Inspired by OpenMyau's HitSelect implementation
 * 
 * Modes:
 * - SECOND: Prioritizes hitting when the opponent is also hitting (double hit)
 * - CRITICALS: Prioritizes critical hits (falling)
 * - W_TAP: Only allows hits when properly W-tapping for maximum knockback
 */
public class HitSelect extends Module {
    
    public static ComboSetting<HitSelectMode> mode;
    public static SliderSetting minDistance;
    public static SliderSetting maxAngle;
    public static TickSetting showStats;
    public static TickSetting onlyPlayers;
    
    private HitSelectMode currentMode = HitSelectMode.Second;
    private boolean sprintState = false;
    private boolean shouldBlock = false;
    
    // Statistics
    private int blockedHits = 0;
    private int allowedHits = 0;
    private long lastStatsTime = 0;

    public HitSelect() {
        super("HitSelect", ModuleCategory.combat);
        
        this.registerSetting(new DescriptionSetting("Hit Optimization"));
        this.registerSetting(mode = new ComboSetting("Mode", currentMode));
        
        this.registerSetting(new DescriptionSetting("Settings"));
        this.registerSetting(minDistance = new SliderSetting("Min Distance", 2.5D, 0.0D, 6.0D, 0.1D));
        this.registerSetting(maxAngle = new SliderSetting("Max Angle", 60.0D, 0.0D, 180.0D, 5.0D));
        this.registerSetting(onlyPlayers = new TickSetting("Only players", true));
        
        this.registerSetting(new DescriptionSetting("Debug"));
        this.registerSetting(showStats = new TickSetting("Show statistics", false));
    }
    
    @Override
    public void onEnable() {
        blockedHits = 0;
        allowedHits = 0;
        sprintState = mc.thePlayer != null && mc.thePlayer.isSprinting();
    }
    
    @Override
    public void onDisable() {
        blockedHits = 0;
        allowedHits = 0;
        sprintState = false;
        shouldBlock = false;
    }
    
    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (fe.getEvent() instanceof LivingUpdateEvent) {
            onUpdate();
        }
    }
    
    private void onUpdate() {
        if (!Utils.Player.isPlayerInGame()) return;
        
        // Update sprint state
        sprintState = mc.thePlayer.isSprinting();
        
        // Show statistics periodically
        if (showStats.isToggled() && System.currentTimeMillis() - lastStatsTime > 5000) {
            if (blockedHits > 0 || allowedHits > 0) {
                int total = blockedHits + allowedHits;
                double ratio = total > 0 ? (double) allowedHits / total * 100 : 0;
                Utils.Player.sendMessageToSelf(String.format(
                    "§7[HitSelect] §fAllowed: %d, Blocked: %d (%.1f%% efficiency)",
                    allowedHits, blockedHits, ratio
                ));
            }
            lastStatsTime = System.currentTimeMillis();
        }
        
        // Determine if we should block the next hit
        Entity target = mc.objectMouseOver != null ? mc.objectMouseOver.entityHit : null;
        if (target instanceof EntityLivingBase) {
            shouldBlock = !shouldAllowHit((EntityLivingBase) target);
        } else {
            shouldBlock = false;
        }
    }
    
    /**
     * Call this before an attack to check if it should be allowed
     */
    public boolean shouldAllowHit(EntityLivingBase target) {
        if (!this.isEnabled()) return true;
        if (target == null) return true;
        
        // Only check players if setting enabled
        if (onlyPlayers.isToggled() && !(target instanceof EntityPlayer)) {
            return true;
        }
        
        HitSelectMode currentMode = mode.getMode();
        boolean allow;
        
        switch (currentMode) {
            case Second:
                allow = prioritizeSecondHit(mc.thePlayer, target);
                break;
            case Criticals:
                allow = prioritizeCriticalHits(mc.thePlayer);
                break;
            case WTap:
                allow = prioritizeWTapHits(mc.thePlayer, sprintState);
                break;
            default:
                allow = true;
        }
        
        if (allow) {
            allowedHits++;
        } else {
            blockedHits++;
        }
        
        return allow;
    }
    
    /**
     * Second hit mode - allows hits when opponent is also hitting (maximizes trade damage)
     */
    private boolean prioritizeSecondHit(EntityLivingBase player, EntityLivingBase target) {
        // If target is already hurt, allow the hit
        if (target.hurtTime != 0) {
            return true;
        }
        
        // If player hasn't recovered from hurt time, allow the hit
        if (player.hurtTime <= player.maxHurtTime - 1) {
            return true;
        }
        
        // If too close, allow the hit
        double dist = player.getDistanceToEntity(target);
        if (dist < minDistance.getInput()) {
            return true;
        }
        
        // If not moving towards each other, allow the hit
        if (!isMovingTowards(target, player, maxAngle.getInput())) {
            return true;
        }
        
        if (!isMovingTowards(player, target, maxAngle.getInput())) {
            return true;
        }
        
        // Block the hit - opponent needs to be hitting too
        return false;
    }
    
    /**
     * Criticals mode - only allows hits when falling (for critical damage)
     */
    private boolean prioritizeCriticalHits(EntityLivingBase player) {
        // Allow hit if falling (critical hit condition)
        if (player.fallDistance > 0.0F && !player.onGround && !player.isOnLadder() && 
            !player.isInWater() && !player.isRiding()) {
            return true;
        }
        
        // If on ground and moving up, allow (start of jump)
        if (player.onGround && player.motionY > 0) {
            return true;
        }
        
        // If against wall, allow
        if (player.isCollidedHorizontally) {
            return true;
        }
        
        // Block non-critical hits
        return false;
    }
    
    /**
     * W-Tap mode - only allows hits when properly sprinting for max knockback
     */
    private boolean prioritizeWTapHits(EntityLivingBase player, boolean sprinting) {
        // If against wall, allow the hit
        if (player.isCollidedHorizontally) {
            return true;
        }
        
        // If not moving forward, allow the hit
        if (!mc.gameSettings.keyBindForward.isKeyDown()) {
            return true;
        }
        
        // If already sprinting, allow the hit
        if (sprinting) {
            return true;
        }
        
        // Block hits when not sprinting for W-tap
        return false;
    }
    
    /**
     * Check if source entity is moving towards target entity
     */
    private boolean isMovingTowards(EntityLivingBase source, EntityLivingBase target, double maxAngleDeg) {
        Vec3 currentPos = source.getPositionVector();
        Vec3 lastPos = new Vec3(source.lastTickPosX, source.lastTickPosY, source.lastTickPosZ);
        Vec3 targetPos = target.getPositionVector();
        
        // Calculate movement vector
        double mx = currentPos.xCoord - lastPos.xCoord;
        double mz = currentPos.zCoord - lastPos.zCoord;
        double movementLength = Math.sqrt(mx * mx + mz * mz);
        
        // If not moving, return false
        if (movementLength == 0.0) {
            return false;
        }
        
        // Normalize movement vector
        mx /= movementLength;
        mz /= movementLength;
        
        // Calculate vector to target
        double tx = targetPos.xCoord - currentPos.xCoord;
        double tz = targetPos.zCoord - currentPos.zCoord;
        double targetLength = Math.sqrt(tx * tx + tz * tz);
        
        // If target is at same position, return false
        if (targetLength == 0.0) {
            return false;
        }
        
        // Normalize target vector
        tx /= targetLength;
        tz /= targetLength;
        
        // Calculate dot product (cosine of angle between vectors)
        double dotProduct = mx * tx + mz * tz;
        
        // Check if angle is within threshold
        return dotProduct >= Math.cos(Math.toRadians(maxAngleDeg));
    }
    
    /**
     * Check if a hit should be blocked (for external use)
     */
    public boolean shouldBlockHit() {
        return shouldBlock;
    }
    
    @Override
    public String getInfo() {
        return mode.getMode().name();
    }
    
    public enum HitSelectMode {
        Second,     // Prioritize hitting when opponent hits
        Criticals,  // Prioritize critical hits
        WTap        // Prioritize W-tap hits
    }
}
