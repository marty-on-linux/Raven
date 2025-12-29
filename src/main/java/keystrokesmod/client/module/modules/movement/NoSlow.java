package keystrokesmod.client.module.modules.movement;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.event.impl.PacketEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.server.S30PacketWindowItems;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;

/**
 * Enhanced NoSlow module with multiple bypass modes
 * Inspired by OpenMyau's NoSlow implementation
 */
public class NoSlow extends Module {
    // Mode settings
    public static ComboSetting<NoSlowMode> mode;
    
    // Sword settings
    public static TickSetting swordNoSlow;
    public static SliderSetting swordMotion;
    public static TickSetting swordSprint;
    
    // Food/Potion settings
    public static TickSetting foodNoSlow;
    public static SliderSetting foodMotion;
    public static TickSetting foodSprint;
    
    // Bow settings
    public static TickSetting bowNoSlow;
    public static SliderSetting bowMotion;
    public static TickSetting bowSprint;
    
    // Float mode settings (jump when using items)
    public static TickSetting floatMode;
    public static SliderSetting floatMotion;
    
    // Bypass settings
    public static TickSetting noReset;
    
    public NoSlowMode currentMode = NoSlowMode.Vanilla;

    public NoSlow() {
        super("NoSlow", ModuleCategory.movement);
        
        this.registerSetting(new DescriptionSetting("Mode Settings"));
        this.registerSetting(mode = new ComboSetting("Mode", currentMode));
        
        this.registerSetting(new DescriptionSetting("Sword Blocking"));
        this.registerSetting(swordNoSlow = new TickSetting("Sword NoSlow", true));
        this.registerSetting(swordMotion = new SliderSetting("Sword Motion %", 100.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(swordSprint = new TickSetting("Sword Sprint", true));
        
        this.registerSetting(new DescriptionSetting("Food/Potion"));
        this.registerSetting(foodNoSlow = new TickSetting("Food NoSlow", true));
        this.registerSetting(foodMotion = new SliderSetting("Food Motion %", 100.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(foodSprint = new TickSetting("Food Sprint", false));
        
        this.registerSetting(new DescriptionSetting("Bow"));
        this.registerSetting(bowNoSlow = new TickSetting("Bow NoSlow", true));
        this.registerSetting(bowMotion = new SliderSetting("Bow Motion %", 100.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(bowSprint = new TickSetting("Bow Sprint", false));
        
        this.registerSetting(new DescriptionSetting("Float Mode"));
        this.registerSetting(floatMode = new TickSetting("Float (jump on use)", false));
        this.registerSetting(floatMotion = new SliderSetting("Float Motion %", 100.0D, 0.0D, 100.0D, 1.0D));
        
        this.registerSetting(new DescriptionSetting("Bypass"));
        this.registerSetting(noReset = new TickSetting("No Reset (Hypixel)", false));
    }

    @Subscribe
    public void onPacket(PacketEvent e) {
        if (noReset.isToggled()) {
            if (e.getPacket() instanceof S30PacketWindowItems) {
                if (mc.thePlayer != null && mc.thePlayer.isUsingItem()) {
                    e.cancel();
                }
            }
        }
    }
    
    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof LivingUpdateEvent)) return;
        if (!Utils.Player.isPlayerInGame()) return;
        if (!mc.thePlayer.isUsingItem()) return;
        
        NoSlowMode currentMode = mode.getMode();
        
        // Check what item is being used
        boolean isSword = mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword;
        boolean isFood = mc.thePlayer.getHeldItem() != null && 
            (mc.thePlayer.getHeldItem().getItem() instanceof ItemFood || mc.thePlayer.getHeldItem().getItem() instanceof ItemPotion);
        boolean isBow = mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemBow;
        
        // Handle float mode - jump when using items on ground
        if (floatMode.isToggled() && mc.thePlayer.onGround) {
            if (currentMode == NoSlowMode.Float) {
                mc.thePlayer.motionY = 0.42F;
            }
        }
        
        // Apply motion multipliers
        if (isSword && swordNoSlow.isToggled()) {
            applyMotion(swordMotion.getInput() / 100.0D, swordSprint.isToggled());
        } else if (isFood && foodNoSlow.isToggled()) {
            applyMotion(foodMotion.getInput() / 100.0D, foodSprint.isToggled());
        } else if (isBow && bowNoSlow.isToggled()) {
            applyMotion(bowMotion.getInput() / 100.0D, bowSprint.isToggled());
        }
    }
    
    private void applyMotion(double multiplier, boolean canSprint) {
        // Apply motion multiplier
        mc.thePlayer.movementInput.moveForward *= (float) multiplier;
        mc.thePlayer.movementInput.moveStrafe *= (float) multiplier;
        
        // Handle sprint
        if (!canSprint) {
            mc.thePlayer.setSprinting(false);
        }
    }
    
    /**
     * Get the slow multiplier for external use (e.g., by movement code)
     */
    public static double getSlowMultiplier() {
        if (mc.thePlayer == null || !mc.thePlayer.isUsingItem()) {
            return 1.0D;
        }
        
        boolean isSword = mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword;
        boolean isFood = mc.thePlayer.getHeldItem() != null && 
            (mc.thePlayer.getHeldItem().getItem() instanceof ItemFood || mc.thePlayer.getHeldItem().getItem() instanceof ItemPotion);
        boolean isBow = mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemBow;
        
        if (isSword && swordNoSlow.isToggled()) {
            return swordMotion.getInput() / 100.0D;
        } else if (isFood && foodNoSlow.isToggled()) {
            return foodMotion.getInput() / 100.0D;
        } else if (isBow && bowNoSlow.isToggled()) {
            return bowMotion.getInput() / 100.0D;
        }
        
        return 0.2D; // Default slow factor
    }
    
    public enum NoSlowMode {
        Vanilla,    // Standard percentage-based no slow
        Float       // Jump when using items for bypass
    }
}
