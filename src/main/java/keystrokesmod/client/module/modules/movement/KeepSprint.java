package keystrokesmod.client.module.modules.movement;

import keystrokesmod.client.main.Raven;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.modules.combat.Reach;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import net.minecraft.entity.Entity;

/**
 * Enhanced KeepSprint module with advanced options
 * Inspired by OpenMyau's KeepSprint implementation
 */
public class KeepSprint extends Module {
    public static SliderSetting slowdown;
    public static TickSetting groundOnly;
    public static TickSetting reachOnly;
    public static TickSetting stopSprint;
    public static TickSetting onlyWhileSprinting;
    public static TickSetting onlyWhileMoving;

    public KeepSprint() {
        super("KeepSprint", ModuleCategory.movement);
        this.registerSetting(new DescriptionSetting("Default is 40% motion reduction"));
        this.registerSetting(new DescriptionSetting("and stopping sprint."));
        
        this.registerSetting(slowdown = new SliderSetting("Slowdown %", 40.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(stopSprint = new TickSetting("Stop Sprint", true));
        
        this.registerSetting(new DescriptionSetting("Conditions"));
        this.registerSetting(groundOnly = new TickSetting("Ground only", false));
        this.registerSetting(reachOnly = new TickSetting("Only reduce reach hits", false));
        this.registerSetting(onlyWhileSprinting = new TickSetting("Only while sprinting", true));
        this.registerSetting(onlyWhileMoving = new TickSetting("Only while moving", false));
    }
    
    /**
     * Check if KeepSprint should activate
     */
    public static boolean shouldKeepSprint() {
        if (mc.thePlayer == null) return false;
        
        // Check ground only condition
        if (groundOnly.isToggled() && !mc.thePlayer.onGround) {
            return false;
        }
        
        // Check sprinting condition
        if (onlyWhileSprinting.isToggled() && !mc.thePlayer.isSprinting()) {
            return false;
        }
        
        // Check moving condition
        if (onlyWhileMoving.isToggled() && 
            mc.thePlayer.moveForward == 0 && mc.thePlayer.moveStrafing == 0) {
            return false;
        }
        
        return true;
    }

    /**
     * Apply slowdown on hit
     */
    public static void sl(Entity en) {
        if (mc.thePlayer == null) return;
        
        // Check if conditions are met
        if (!shouldKeepSprint()) {
            // Apply default Minecraft slowdown
            mc.thePlayer.motionX *= 0.6D;
            mc.thePlayer.motionZ *= 0.6D;
            mc.thePlayer.setSprinting(false);
            return;
        }
        
        double dist;
        Module reach = Raven.moduleManager.getModuleByClazz(Reach.class);
        
        if (reachOnly.isToggled() && reach != null && reach.isEnabled() && !mc.thePlayer.capabilities.isCreativeMode) {
            // Only apply slowdown for reach hits
            dist = mc.objectMouseOver.hitVec.distanceTo(mc.getRenderViewEntity().getPositionEyes(1.0F));
            double val;
            if (dist > 3.0D) {
                // Apply configured slowdown for reach hits
                val = (100.0D - slowdown.getInput()) / 100.0D;
            } else {
                // Default slowdown for normal reach hits
                val = 0.6D;
            }

            mc.thePlayer.motionX *= val;
            mc.thePlayer.motionZ *= val;
        } else {
            // Apply configured slowdown
            dist = (100.0D - slowdown.getInput()) / 100.0D;
            mc.thePlayer.motionX *= dist;
            mc.thePlayer.motionZ *= dist;
        }
        
        // Handle sprint stopping
        if (stopSprint.isToggled()) {
            mc.thePlayer.setSprinting(false);
        }
    }
    
    /**
     * Get the current slowdown multiplier
     */
    public static double getSlowdownMultiplier() {
        return (100.0D - slowdown.getInput()) / 100.0D;
    }
}
