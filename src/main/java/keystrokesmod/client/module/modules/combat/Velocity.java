package keystrokesmod.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import org.lwjgl.input.Keyboard;

/**
 * Enhanced Velocity module with advanced bypass modes inspired by OpenMyau
 * Includes multiple velocity manipulation modes for different anticheats
 */
public class Velocity extends Module {
    // Main velocity settings
    public static ComboSetting<VelocityMode> velocityMode;
    public static SliderSetting horizontal, vertical, chance;
    public static TickSetting onlyWhileTargeting, disableWhileHoldingS, fakeCheck;
    
    // Jump mode settings
    public static SliderSetting jumpCooldown;
    
    // Delay mode settings  
    public static SliderSetting delayTicks, delayChance;
    
    // Reverse mode settings
    public static TickSetting reverseOnGround;
    
    // Explosion settings
    public static TickSetting separateExplosions;
    public static SliderSetting explosionHorizontal, explosionVertical;
    
    // Projectile settings
    public static TickSetting differentVeloProjectiles;
    public static ComboSetting<ProjectileMode> projectilesMode;
    public static SliderSetting horizontalProjectiles, verticalProjectiles, chanceProjectiles, distanceProjectiles;
    
    // Debug
    public static TickSetting debugLog;
    
    // Internal state
    private int chanceCounter = 0;
    private int delayChanceCounter = 0;
    private boolean pendingExplosion = false;
    private boolean allowNext = true;
    private boolean jumpFlag = false;
    private boolean reverseFlag = false;
    private boolean delayActive = false;
    private boolean shouldJump = false;
    private int jumpCooldownTicks = 0;
    private int delayTickCounter = 0;
    private double savedMotionX, savedMotionY, savedMotionZ;
    
    public VelocityMode mode = VelocityMode.Vanilla;
    public ProjectileMode projMode = ProjectileMode.Distance;

    public Velocity() {
        super("Velocity", ModuleCategory.combat);
        
        // Mode selection
        this.registerSetting(new DescriptionSetting("Mode Settings"));
        this.registerSetting(velocityMode = new ComboSetting("Mode", mode));
        
        // Main velocity percentages
        this.registerSetting(new DescriptionSetting("Velocity Reduction"));
        this.registerSetting(horizontal = new SliderSetting("Horizontal %", 0.0D, -100.0D, 100.0D, 1.0D));
        this.registerSetting(vertical = new SliderSetting("Vertical %", 100.0D, -100.0D, 100.0D, 1.0D));
        this.registerSetting(chance = new SliderSetting("Chance %", 100.0D, 0.0D, 100.0D, 1.0D));
        
        // Conditions
        this.registerSetting(new DescriptionSetting("Conditions"));
        this.registerSetting(onlyWhileTargeting = new TickSetting("Only while targeting", false));
        this.registerSetting(disableWhileHoldingS = new TickSetting("Disable while holding S", false));
        this.registerSetting(fakeCheck = new TickSetting("Fake velocity check", true));
        
        // Jump mode settings
        this.registerSetting(new DescriptionSetting("Jump Mode Settings"));
        this.registerSetting(jumpCooldown = new SliderSetting("Jump cooldown (ticks)", 2.0D, 1.0D, 10.0D, 1.0D));
        
        // Delay mode settings
        this.registerSetting(new DescriptionSetting("Delay Mode Settings"));
        this.registerSetting(delayTicks = new SliderSetting("Delay ticks", 3.0D, 1.0D, 20.0D, 1.0D));
        this.registerSetting(delayChance = new SliderSetting("Delay chance %", 100.0D, 0.0D, 100.0D, 1.0D));
        
        // Reverse mode settings
        this.registerSetting(new DescriptionSetting("Reverse Mode Settings"));
        this.registerSetting(reverseOnGround = new TickSetting("Only on ground", true));
        
        // Explosion handling
        this.registerSetting(new DescriptionSetting("Explosion Handling"));
        this.registerSetting(separateExplosions = new TickSetting("Separate explosion velo", true));
        this.registerSetting(explosionHorizontal = new SliderSetting("Explosion Horizontal %", 100.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(explosionVertical = new SliderSetting("Explosion Vertical %", 100.0D, 0.0D, 100.0D, 1.0D));
        
        // Projectile settings
        this.registerSetting(new DescriptionSetting("Projectile Settings"));
        this.registerSetting(differentVeloProjectiles = new TickSetting("Different velo for projectiles", false));
        this.registerSetting(projectilesMode = new ComboSetting("Projectiles Mode", projMode));
        this.registerSetting(horizontalProjectiles = new SliderSetting("Horizontal projectiles %", 90.0D, -100.0D, 100.0D, 1.0D));
        this.registerSetting(verticalProjectiles = new SliderSetting("Vertical projectiles %", 100.0D, -100.0D, 100.0D, 1.0D));
        this.registerSetting(chanceProjectiles = new SliderSetting("Chance projectiles %", 100.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(distanceProjectiles = new SliderSetting("Distance projectiles", 3D, 0.0D, 20D, 0.1D));
        
        // Debug
        this.registerSetting(new DescriptionSetting("Debug"));
        this.registerSetting(debugLog = new TickSetting("Debug logging", false));
    }

    @Override
    public void onEnable() {
        resetState();
    }
    
    @Override
    public void onDisable() {
        resetState();
    }
    
    private void resetState() {
        chanceCounter = 0;
        delayChanceCounter = 0;
        pendingExplosion = false;
        allowNext = true;
        jumpFlag = false;
        reverseFlag = false;
        delayActive = false;
        shouldJump = false;
        jumpCooldownTicks = 0;
        delayTickCounter = 0;
    }
    
    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava();
    }
    
    private boolean canDelay() {
        return mc.thePlayer.onGround;
    }

    @Subscribe
    public void onLivingUpdate(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof LivingUpdateEvent)) return;
        if (!Utils.Player.isPlayerInGame()) return;
        
        VelocityMode currentMode = velocityMode.getMode();
        
        // Handle jump cooldown
        if (jumpCooldownTicks > 0) {
            jumpCooldownTicks--;
        }
        
        // Handle delayed velocity release (for delay mode)
        if (delayTickCounter > 0) {
            delayTickCounter--;
            if (delayTickCounter == 0 && reverseFlag) {
                // Release the saved velocity
                mc.thePlayer.motionX = savedMotionX * (horizontal.getInput() / 100.0D);
                mc.thePlayer.motionZ = savedMotionZ * (horizontal.getInput() / 100.0D);
                if (vertical.getInput() > 0) {
                    mc.thePlayer.motionY = savedMotionY * (vertical.getInput() / 100.0D);
                }
                reverseFlag = false;
            }
        }
        
        // Handle jump mode jumping
        if (currentMode == VelocityMode.Jump || currentMode == VelocityMode.Delay) {
            if (jumpFlag) {
                jumpFlag = false;
                if (mc.thePlayer.onGround && mc.thePlayer.isSprinting() && 
                    !mc.thePlayer.isPotionActive(Potion.jump) && !isInLiquidOrWeb()) {
                    mc.thePlayer.jump();
                }
            }
        }
        
        // Handle legit test mode - jump at specific hurt time
        if (currentMode == VelocityMode.LegitTest) {
            int hurtTime = mc.thePlayer.hurtTime;
            if (hurtTime >= 8) {
                if (jumpCooldownTicks <= 0) {
                    shouldJump = true;
                    jumpCooldownTicks = (int) jumpCooldown.getInput();
                }
            } else if (hurtTime <= 1) {
                shouldJump = false;
                jumpCooldownTicks = 0;
            }
            
            if (shouldJump && mc.thePlayer.onGround && jumpCooldownTicks <= 0) {
                mc.thePlayer.jump();
                shouldJump = false;
            }
        }
        
        // Handle velocity at hurt time
        if (mc.thePlayer.maxHurtTime > 0 && mc.thePlayer.hurtTime == mc.thePlayer.maxHurtTime) {
            handleVelocity(currentMode);
        }
        
        // Handle reverse mode motion fix
        if (delayActive) {
            double speed = Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
            float yaw = (float) Math.toRadians(mc.thePlayer.rotationYaw);
            mc.thePlayer.motionX = -MathHelper.sin(yaw) * speed;
            mc.thePlayer.motionZ = MathHelper.cos(yaw) * speed;
            delayActive = false;
        }
    }
    
    private void handleVelocity(VelocityMode currentMode) {
        // Check conditions
        if (onlyWhileTargeting.isToggled() && (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null)) {
            return;
        }
        
        if (disableWhileHoldingS.isToggled() && Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode())) {
            return;
        }
        
        // Handle projectile-based velocity
        if (differentVeloProjectiles.isToggled() && mc.thePlayer.getLastAttacker() instanceof EntityPlayer) {
            EntityPlayer attacker = (EntityPlayer) mc.thePlayer.getLastAttacker();
            Item item = attacker.getCurrentEquippedItem() != null ? attacker.getCurrentEquippedItem().getItem() : null;
            
            ProjectileMode pMode = projectilesMode.getMode();
            if ((item instanceof ItemEgg || item instanceof ItemBow || item instanceof ItemSnow
                    || item instanceof ItemFishingRod) && pMode == ProjectileMode.ItemHeld) {
                applyProjectileVelocity();
                return;
            } else if (attacker.getDistanceToEntity(mc.thePlayer) > distanceProjectiles.getInput()) {
                applyProjectileVelocity();
                return;
            }
        }
        
        // Fake check - allow first velocity packet after damage
        if (fakeCheck.isToggled() && allowNext) {
            allowNext = false;
            return;
        }
        allowNext = true;
        
        // Handle explosion velocity separately
        if (separateExplosions.isToggled() && pendingExplosion) {
            pendingExplosion = false;
            if (explosionHorizontal.getInput() > 0) {
                mc.thePlayer.motionX *= explosionHorizontal.getInput() / 100.0D;
                mc.thePlayer.motionZ *= explosionHorizontal.getInput() / 100.0D;
            } else {
                // Maintain current motion
            }
            if (explosionVertical.getInput() > 0) {
                mc.thePlayer.motionY *= explosionVertical.getInput() / 100.0D;
            }
            return;
        }
        
        // Chance-based velocity
        chanceCounter = chanceCounter % 100 + (int) chance.getInput();
        if (chanceCounter < 100) {
            return;
        }
        
        // Apply velocity based on mode
        switch (currentMode) {
            case Vanilla:
                applyVanillaVelocity();
                break;
                
            case Jump:
                applyVanillaVelocity();
                if (mc.thePlayer.motionY > 0) {
                    jumpFlag = true;
                }
                break;
                
            case Delay:
                // Save velocity and delay it
                delayChanceCounter = delayChanceCounter % 100 + (int) delayChance.getInput();
                if (delayChanceCounter >= 100 && !canDelay() && !isInLiquidOrWeb()) {
                    savedMotionX = mc.thePlayer.motionX;
                    savedMotionY = mc.thePlayer.motionY;
                    savedMotionZ = mc.thePlayer.motionZ;
                    
                    // Cancel immediate velocity
                    mc.thePlayer.motionX = 0;
                    mc.thePlayer.motionZ = 0;
                    
                    delayTickCounter = (int) delayTicks.getInput();
                    reverseFlag = true;
                    
                    if (mc.thePlayer.motionY > 0) {
                        jumpFlag = true;
                    }
                } else {
                    applyVanillaVelocity();
                }
                break;
                
            case Reverse:
                applyVanillaVelocity();
                if (!reverseOnGround.isToggled() || mc.thePlayer.onGround) {
                    delayActive = true;
                }
                break;
                
            case LegitTest:
                // Velocity is handled by jump timing above
                applyVanillaVelocity();
                break;
        }
        
        if (debugLog.isToggled()) {
            Utils.Player.sendMessageToSelf(String.format(
                "§7[Velocity] §fMode: %s, H: %.1f%%, V: %.1f%%, motionX: %.3f, motionY: %.3f, motionZ: %.3f",
                currentMode.name(), horizontal.getInput(), vertical.getInput(),
                mc.thePlayer.motionX, mc.thePlayer.motionY, mc.thePlayer.motionZ
            ));
        }
    }
    
    private void applyVanillaVelocity() {
        if (horizontal.getInput() != 100.0D) {
            mc.thePlayer.motionX *= horizontal.getInput() / 100.0D;
            mc.thePlayer.motionZ *= horizontal.getInput() / 100.0D;
        }
        
        if (vertical.getInput() != 100.0D) {
            mc.thePlayer.motionY *= vertical.getInput() / 100.0D;
        }
    }

    private void applyProjectileVelocity() {
        if (chanceProjectiles.getInput() != 100.0D) {
            double ch = Math.random();
            if (ch >= chanceProjectiles.getInput() / 100.0D) {
                return;
            }
        }

        if (horizontalProjectiles.getInput() != 100.0D) {
            mc.thePlayer.motionX *= horizontalProjectiles.getInput() / 100.0D;
            mc.thePlayer.motionZ *= horizontalProjectiles.getInput() / 100.0D;
        }

        if (verticalProjectiles.getInput() != 100.0D) {
            mc.thePlayer.motionY *= verticalProjectiles.getInput() / 100.0D;
        }
    }
    
    /**
     * Call this when an explosion packet is received
     */
    public void onExplosion(float motionX, float motionY, float motionZ) {
        if (motionX != 0.0F || motionY != 0.0F || motionZ != 0.0F) {
            pendingExplosion = true;
        }
    }
    
    @Override
    public String getInfo() {
        return velocityMode.getMode().name();
    }

    public enum VelocityMode {
        Vanilla,    // Standard percentage-based velocity reduction
        Jump,       // Jump when hit to reduce knockback
        Delay,      // Delay velocity application for bypass
        Reverse,    // Reverse velocity direction
        LegitTest   // Timed jumps based on hurt time
    }
    
    public enum ProjectileMode {
        Distance, ItemHeld
    }
}
