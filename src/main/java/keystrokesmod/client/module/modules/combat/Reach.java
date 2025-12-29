package keystrokesmod.client.module.modules.combat;

import keystrokesmod.client.main.Raven;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.modules.combat.aura.KillAura;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.DoubleSliderSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Random;

/**
 * Enhanced Reach module with chance-based activation and better randomization
 * Inspired by OpenMyau's reach implementation
 */
public class Reach extends Module {
    private static final DecimalFormat df = new DecimalFormat("0.0#", new DecimalFormatSymbols(Locale.US));
    private final Random theRandom = new Random();
    private boolean expanding = true;
    
    public static DoubleSliderSetting reach;
    public static SliderSetting chance;
    public static TickSetting weapon_only;
    public static TickSetting moving_only;
    public static TickSetting sprint_only;
    public static TickSetting onlyWhileTargeting;
    public static TickSetting groundOnly;
    public static TickSetting hitThroughBlocks;
    public static KillAura la;

    public Reach() {
        super("Reach", ModuleCategory.combat);
        
        this.registerSetting(new DescriptionSetting("Reach Settings"));
        this.registerSetting(reach = new DoubleSliderSetting("Reach (Blocks)", 3.1, 3.3, 3, 6, 0.05));
        this.registerSetting(chance = new SliderSetting("Chance %", 100.0D, 0.0D, 100.0D, 1.0D));
        
        this.registerSetting(new DescriptionSetting("Conditions"));
        this.registerSetting(weapon_only = new TickSetting("Weapon only", false));
        this.registerSetting(moving_only = new TickSetting("Moving only", false));
        this.registerSetting(sprint_only = new TickSetting("Sprint only", false));
        this.registerSetting(onlyWhileTargeting = new TickSetting("Only while targeting", false));
        this.registerSetting(groundOnly = new TickSetting("Ground only", false));
        this.registerSetting(hitThroughBlocks = new TickSetting("Hit through blocks", false));
    }

    @Override
    public void postApplyConfig() {
       la = (KillAura) Raven.moduleManager.getModuleByClazz(KillAura.class);
    }
    
    @Override
    public void guiUpdate() {
        // Update expanding state based on chance each tick
        expanding = theRandom.nextDouble() <= chance.getInput() / 100.0;
    }

    public static double getReach() {
        if(la != null && la.isEnabled())
            return KillAura.reach.getInput();

        double normal = mc.playerController.extendedReach() ? 5 : 3;
        
        Reach reachModule = (Reach) Raven.moduleManager.getModuleByClazz(Reach.class);
        if (reachModule == null || !reachModule.isEnabled()) {
            return normal;
        }

        if (!Utils.Player.isPlayerInGame()) {
            return normal;
        }
        
        // Check chance - if not expanding this tick, return normal
        if (!reachModule.expanding) {
            return normal;
        }
        
        // Check weapon condition
        if (weapon_only.isToggled() && !Utils.Player.isPlayerHoldingWeapon()) {
            return normal;
        }

        // Check moving condition
        if (moving_only.isToggled() && 
            ((double) mc.thePlayer.moveForward == 0.0D) && ((double) mc.thePlayer.moveStrafing == 0.0D)) {
            return normal;
        }

        // Check sprint condition
        if (sprint_only.isToggled() && !mc.thePlayer.isSprinting()) {
            return normal;
        }
        
        // Check targeting condition
        if (onlyWhileTargeting.isToggled() && 
            (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null)) {
            return normal;
        }
        
        // Check ground condition
        if (groundOnly.isToggled() && !mc.thePlayer.onGround) {
            return normal;
        }

        return Utils.Client.ranModuleVal(reach, Utils.Java.rand()) + (mc.playerController.extendedReach() ? 2 : 0);
    }
    
    /**
     * Check if we should hit through blocks (for raytracing)
     */
    public static boolean shouldHitThroughBlocks() {
        Reach reachModule = (Reach) Raven.moduleManager.getModuleByClazz(Reach.class);
        return reachModule != null && reachModule.isEnabled() && hitThroughBlocks.isToggled();
    }
    
    @Override
    public String getInfo() {
        return df.format(reach.getInputMin()) + "-" + df.format(reach.getInputMax());
    }
}
