package keystrokesmod.client.module.modules.combat;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import org.lwjgl.input.Mouse;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.main.Raven;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.modules.client.Targets;
import keystrokesmod.client.module.modules.player.RightClicker;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.CoolDown;
import keystrokesmod.client.utils.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Enhanced AimAssist module with advanced targeting and smoothing
 * Inspired by OpenMyau's AimAssist implementation
 */
public class AimAssist extends Module {
    // Speed settings
    public static SliderSetting speedYaw, complimentYaw, speedPitch, complimentPitch;
    public static SliderSetting smoothing;
    
    // Range and FOV
    public static SliderSetting fov;
    public static SliderSetting distance;
    public static SliderSetting pitchOffSet;
    
    // Mode settings
    public static ComboSetting aimMode;
    
    // Conditions
    public static TickSetting clickAim;
    public static TickSetting stopWhenOver;
    public static TickSetting aimPitch;
    public static TickSetting weaponOnly;
    public static TickSetting allowTools;
    public static TickSetting aimInvis;
    public static TickSetting breakBlocks;
    public static TickSetting blatantMode;
    public static TickSetting movingOnly;
    public static TickSetting sprintOnly;
    
    // Advanced
    public static SliderSetting maxAngleStep;
    public static TickSetting predictMovement;
    public static SliderSetting predictionTicks;
    
    public static ArrayList<Entity> friends = new ArrayList<>();
    
    // Internal state
    private CoolDown lastAttackTimer = new CoolDown(350);
    private float lastYaw = 0;
    private float lastPitch = 0;
    
    public AimMode currentMode = AimMode.Smooth;

    public AimAssist() {
        super("AimAssist", ModuleCategory.combat);
        
        this.registerSetting(new DescriptionSetting("Set targets in Client->Targets"));
        
        this.registerSetting(new DescriptionSetting("Mode"));
        this.registerSetting(aimMode = new ComboSetting("Aim Mode", currentMode));
        
        this.registerSetting(new DescriptionSetting("Yaw Settings"));
        this.registerSetting(speedYaw = new SliderSetting("Speed 1 (yaw)", 45.0D, 5.0D, 100.0D, 1.0D));
        this.registerSetting(complimentYaw = new SliderSetting("Speed 2 (yaw)", 15.0D, 2D, 97.0D, 1.0D));
        
        this.registerSetting(new DescriptionSetting("Pitch Settings"));
        this.registerSetting(speedPitch = new SliderSetting("Speed 1 (pitch)", 45.0D, 5.0D, 100.0D, 1.0D));
        this.registerSetting(complimentPitch = new SliderSetting("Speed 2 (pitch)", 15.0D, 2D, 97.0D, 1.0D));
        this.registerSetting(pitchOffSet = new SliderSetting("Pitch Offset (blocks)", 0.4D, -2, 2, 0.05D));
        
        this.registerSetting(new DescriptionSetting("Smoothing"));
        this.registerSetting(smoothing = new SliderSetting("Smoothing %", 50.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(maxAngleStep = new SliderSetting("Max Angle Step", 90.0D, 10.0D, 180.0D, 5.0D));
        
        this.registerSetting(new DescriptionSetting("Range"));
        this.registerSetting(distance = new SliderSetting("Distance", 4.5D, 1.0D, 8.0D, 0.1D));
        this.registerSetting(fov = new SliderSetting("FOV", 90.0D, 30.0D, 360.0D, 5.0D));
        
        this.registerSetting(new DescriptionSetting("Conditions"));
        this.registerSetting(clickAim = new TickSetting("Click aim", true));
        this.registerSetting(breakBlocks = new TickSetting("Break blocks", true));
        this.registerSetting(weaponOnly = new TickSetting("Weapon only", false));
        this.registerSetting(allowTools = new TickSetting("Allow tools", false));
        this.registerSetting(movingOnly = new TickSetting("Moving only", false));
        this.registerSetting(sprintOnly = new TickSetting("Sprint only", false));
        this.registerSetting(blatantMode = new TickSetting("Blatant mode", false));
        this.registerSetting(aimPitch = new TickSetting("Aim pitch", false));
        
        this.registerSetting(new DescriptionSetting("Prediction"));
        this.registerSetting(predictMovement = new TickSetting("Predict movement", false));
        this.registerSetting(predictionTicks = new SliderSetting("Prediction ticks", 3.0D, 1.0D, 10.0D, 1.0D));
    }

    @Subscribe
    public void onRender(ForgeEvent fe) {
        try {
            if (fe.getEvent() instanceof TickEvent.ClientTickEvent) {
                if (!Utils.Client.currentScreenMinecraft() || !Utils.Player.isPlayerInGame())
                    return;

                // Check break blocks condition
                if (breakBlocks.isToggled() && (mc.objectMouseOver != null)) {
                    BlockPos p = mc.objectMouseOver.getBlockPos();
                    if (p != null) {
                        Block bl = mc.theWorld.getBlockState(p).getBlock();
                        if ((bl != Blocks.air) && !(bl instanceof BlockLiquid) && (bl != null))
                            return;
                    }
                }

                // Check weapon only condition
                if (weaponOnly.isToggled() && !Utils.Player.isPlayerHoldingWeapon()) {
                    if (!allowTools.isToggled() || !Utils.Player.isPlayerHoldingTool()) {
                        return;
                    }
                }
                
                // Check moving only condition
                if (movingOnly.isToggled() && 
                    mc.thePlayer.moveForward == 0 && mc.thePlayer.moveStrafing == 0) {
                    return;
                }
                
                // Check sprint only condition
                if (sprintOnly.isToggled() && !mc.thePlayer.isSprinting()) {
                    return;
                }

                Module autoClicker = Raven.moduleManager.getModuleByClazz(RightClicker.class);
                
                // Check if we should aim based on click condition
                boolean shouldAim = !clickAim.isToggled() || 
                    Utils.Client.autoClickerClicking() ||
                    (Mouse.isButtonDown(0) && (autoClicker == null || !autoClicker.isEnabled())) ||
                    !lastAttackTimer.hasFinished();
                
                if (!shouldAim) return;
                
                Entity en = this.getEnemy();
                if (en == null) return;
                
                // Check distance
                if (mc.thePlayer.getDistanceToEntity(en) > distance.getInput()) {
                    return;
                }
                
                // Check FOV
                if (Utils.Player.fovFromEntity(en) > fov.getInput() / 2) {
                    return;
                }
                
                // Reset attack timer when clicking
                if (Mouse.isButtonDown(0)) {
                    lastAttackTimer.setCooldown(350);
                    lastAttackTimer.start();
                }
                
                AimMode mode = AimMode.values()[aimMode.getMode()];
                
                if (blatantMode.isToggled() || mode == AimMode.Instant) {
                    // Instant/Blatant aim
                    Utils.Player.aim(en, (float) pitchOffSet.getInput());
                } else {
                    // Smooth aim
                    smoothAim(en, mode);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void smoothAim(Entity target, AimMode mode) {
        // Get target position, with prediction if enabled
        double targetX = target.posX;
        double targetY = target.posY + target.getEyeHeight() - pitchOffSet.getInput();
        double targetZ = target.posZ;
        
        if (predictMovement.isToggled()) {
            int ticks = (int) predictionTicks.getInput();
            targetX += (target.posX - target.lastTickPosX) * ticks;
            targetY += (target.posY - target.lastTickPosY) * ticks;
            targetZ += (target.posZ - target.lastTickPosZ) * ticks;
        }
        
        // Calculate required rotations
        double dx = targetX - mc.thePlayer.posX;
        double dy = targetY - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz = targetZ - mc.thePlayer.posZ;
        
        double dist = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        
        // Calculate yaw delta
        float yawDelta = MathHelper.wrapAngleTo180_float(targetYaw - mc.thePlayer.rotationYaw);
        float pitchDelta = targetPitch - mc.thePlayer.rotationPitch;
        
        // Apply smoothing
        float smoothFactor = (float) (smoothing.getInput() / 100.0D);
        float maxStep = (float) maxAngleStep.getInput();
        
        // Calculate speed based on settings
        double yawSpeed = ThreadLocalRandom.current().nextDouble(
            speedYaw.getInput() - 4.723847, speedYaw.getInput());
        double pitchSpeed = ThreadLocalRandom.current().nextDouble(
            speedPitch.getInput() - 4.723847, speedPitch.getInput());
        
        // Apply yaw rotation
        if (Math.abs(yawDelta) > 0.5) {
            double complimentSpeed = yawDelta * (ThreadLocalRandom.current().nextDouble(
                complimentYaw.getInput() - 1.47328, complimentYaw.getInput() + 2.48293) / 100);
            
            float yawChange = (float) (-(complimentSpeed + (yawDelta / (101.0D - yawSpeed))));
            
            // Apply smoothing
            yawChange = yawChange * (1.0f - smoothFactor) + (lastYaw * smoothFactor);
            
            // Clamp to max step
            yawChange = MathHelper.clamp_float(yawChange, -maxStep, maxStep);
            
            mc.thePlayer.rotationYaw += yawChange;
            lastYaw = yawChange;
        }
        
        // Apply pitch rotation if enabled
        if (aimPitch.isToggled() && Math.abs(pitchDelta) > 0.5) {
            double complimentSpeed = pitchDelta * (ThreadLocalRandom.current().nextDouble(
                complimentPitch.getInput() - 1.47328, complimentPitch.getInput() + 2.48293) / 100);
            
            float pitchChange = (float) (-(complimentSpeed + (pitchDelta / (101.0D - pitchSpeed))));
            
            // Apply smoothing
            pitchChange = pitchChange * (1.0f - smoothFactor) + (lastPitch * smoothFactor);
            
            // Clamp to max step and valid pitch range
            pitchChange = MathHelper.clamp_float(pitchChange, -maxStep, maxStep);
            float newPitch = mc.thePlayer.rotationPitch + pitchChange;
            newPitch = MathHelper.clamp_float(newPitch, -90.0f, 90.0f);
            
            mc.thePlayer.rotationPitch = newPitch;
            lastPitch = pitchChange;
        }
    }

    public Entity getEnemy() {
       return Targets.getTarget();
    }

    public static void addFriend(Entity entityPlayer) {
        friends.add(entityPlayer);
    }

    public static boolean addFriend(String name) {
        boolean found = false;
        for (Entity entity : mc.theWorld.getLoadedEntityList())
            if (entity.getName().equalsIgnoreCase(name) || entity.getCustomNameTag().equalsIgnoreCase(name))
                if (!Targets.isAFriend(entity)) {
                    addFriend(entity);
                    found = true;
                }

        return found;
    }

    public static boolean removeFriend(String name) {
        boolean removed = false;
        boolean found = false;
        for (NetworkPlayerInfo networkPlayerInfo : new ArrayList<>(mc.getNetHandler().getPlayerInfoMap())) {
            Entity entity = mc.theWorld.getPlayerEntityByName(networkPlayerInfo.getDisplayName().getUnformattedText());
            if (entity != null && (entity.getName().equalsIgnoreCase(name) || entity.getCustomNameTag().equalsIgnoreCase(name))) {
                removed = removeFriend(entity);
                found = true;
            }
        }

        return found && removed;
    }

    public static boolean removeFriend(Entity entityPlayer) {
        try {
            friends.remove(entityPlayer);
        } catch (Exception eeeeee) {
            eeeeee.printStackTrace();
            return false;
        }
        return true;
    }

    public static ArrayList<Entity> getFriends() {
        return friends;
    }
    
    @Override
    public String getInfo() {
        return AimMode.values()[aimMode.getMode()].name();
    }
    
    public enum AimMode {
        Smooth,
        Instant,
        Legit
    }
}