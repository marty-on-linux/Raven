package keystrokesmod.client.module.modules.movement;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockLiquid;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * AntiVoid module - Prevents death from falling into the void
 * Inspired by OpenMyau's AntiVoid implementation
 * 
 * Modes:
 * - Blink: Stores packets and teleports back to safe position
 * - Position: Sends position packets to return to safe location
 * - Freeze: Stops movement when over void
 */
public class AntiVoid extends Module {
    // Mode settings
    public static ComboSetting mode;
    
    // Detection settings
    public static SliderSetting voidDistance;
    public static SliderSetting fallDistance;
    public static SliderSetting safetyHeight;
    
    // Behavior settings
    public static TickSetting onlyInBedwars;
    public static TickSetting resetOnPearl;
    public static TickSetting debugMessages;
    
    // Internal state
    private double[] lastSafePosition = null;
    private boolean isOverVoid = false;
    private boolean wasOverVoid = false;
    private boolean blinking = false;
    private Deque<Packet<?>> blinkedPackets = new ConcurrentLinkedDeque<>();
    private int blinkTicks = 0;
    
    private AntiVoidMode currentMode = AntiVoidMode.Blink;

    public AntiVoid() {
        super("AntiVoid", ModuleCategory.movement);
        
        this.registerSetting(new DescriptionSetting("Prevents death from void falls"));
        
        this.registerSetting(new DescriptionSetting("Mode"));
        this.registerSetting(mode = new ComboSetting("Mode", currentMode));
        
        this.registerSetting(new DescriptionSetting("Detection"));
        this.registerSetting(voidDistance = new SliderSetting("Void Distance", 5.0D, 1.0D, 20.0D, 1.0D));
        this.registerSetting(fallDistance = new SliderSetting("Fall Distance", 5.0D, 2.0D, 15.0D, 1.0D));
        this.registerSetting(safetyHeight = new SliderSetting("Safety Height", 0.0D, -64.0D, 20.0D, 1.0D));
        
        this.registerSetting(new DescriptionSetting("Behavior"));
        this.registerSetting(onlyInBedwars = new TickSetting("Only in Bedwars", false));
        this.registerSetting(resetOnPearl = new TickSetting("Reset on pearl", true));
        this.registerSetting(debugMessages = new TickSetting("Debug messages", false));
    }

    @Override
    public void onEnable() {
        super.onEnable();
        resetState();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        releasePackets();
        resetState();
    }

    @Subscribe
    public void onTick(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof TickEvent.ClientTickEvent)) return;
        if (!Utils.Player.isPlayerInGame()) return;
        if (mc.thePlayer.capabilities.allowFlying) return;
        
        // Check if only in bedwars mode
        if (onlyInBedwars.isToggled()) {
            // Simple check - could be improved with scoreboard parsing
            if (!mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(1) != null) {
                return;
            }
        }
        
        AntiVoidMode currentModeValue = AntiVoidMode.values()[mode.getMode()];
        
        // Update void state
        wasOverVoid = isOverVoid;
        isOverVoid = isPlayerOverVoid();
        
        switch (currentModeValue) {
            case Blink:
                handleBlinkMode();
                break;
            case Position:
                handlePositionMode();
                break;
            case Freeze:
                handleFreezeMode();
                break;
        }
    }
    
    private void handleBlinkMode() {
        // If not over void and not blinking, update safe position
        if (!isOverVoid && !blinking) {
            updateSafePosition();
        }
        
        // Check if we just entered void
        if (!wasOverVoid && isOverVoid && canUseAntiVoid()) {
            if (lastSafePosition != null) {
                // Start blinking
                blinking = true;
                blinkTicks = 0;
                
                if (debugMessages.isToggled()) {
                    Utils.Player.sendMessageToSelf("§a[AntiVoid] Started blinking over void");
                }
            }
        }
        
        // If blinking and fallen enough, teleport back
        if (blinking && lastSafePosition != null) {
            blinkTicks++;
            
            double fallDist = lastSafePosition[1] - mc.thePlayer.posY;
            
            if (fallDist >= fallDistance.getInput()) {
                // Send position packet to teleport back
                mc.thePlayer.setPosition(lastSafePosition[0], lastSafePosition[1], lastSafePosition[2]);
                
                // Clear blinked packets (don't send them)
                blinkedPackets.clear();
                
                if (debugMessages.isToggled()) {
                    Utils.Player.sendMessageToSelf("§a[AntiVoid] Teleported back to safe position!");
                }
                
                resetState();
            }
            
            // Safety check - don't blink forever
            if (blinkTicks > 100) {
                if (debugMessages.isToggled()) {
                    Utils.Player.sendMessageToSelf("§c[AntiVoid] Blink timeout, releasing packets");
                }
                releasePackets();
                resetState();
            }
        }
        
        // If we were over void but now we're not (landed safely), release packets
        if (wasOverVoid && !isOverVoid && blinking) {
            releasePackets();
            resetState();
            
            if (debugMessages.isToggled()) {
                Utils.Player.sendMessageToSelf("§a[AntiVoid] Landed safely, releasing packets");
            }
        }
    }
    
    private void handlePositionMode() {
        // Update safe position when not over void
        if (!isOverVoid) {
            updateSafePosition();
        }
        
        // If over void and falling, send position back
        if (isOverVoid && lastSafePosition != null) {
            double fallDist = lastSafePosition[1] - mc.thePlayer.posY;
            
            if (fallDist >= fallDistance.getInput()) {
                // Send position packet
                mc.getNetHandler().addToSendQueue(
                    new C03PacketPlayer.C04PacketPlayerPosition(
                        lastSafePosition[0], 
                        lastSafePosition[1], 
                        lastSafePosition[2], 
                        true
                    )
                );
                
                mc.thePlayer.setPosition(lastSafePosition[0], lastSafePosition[1], lastSafePosition[2]);
                mc.thePlayer.motionY = 0;
                
                if (debugMessages.isToggled()) {
                    Utils.Player.sendMessageToSelf("§a[AntiVoid] Position mode - teleported back!");
                }
            }
        }
    }
    
    private void handleFreezeMode() {
        // Update safe position when not over void
        if (!isOverVoid) {
            updateSafePosition();
        }
        
        // If over void, freeze vertical motion
        if (isOverVoid && lastSafePosition != null) {
            double fallDist = lastSafePosition[1] - mc.thePlayer.posY;
            
            if (fallDist >= fallDistance.getInput() - 2) {
                // Stop falling
                mc.thePlayer.motionY = 0;
                mc.thePlayer.motionX *= 0.9;
                mc.thePlayer.motionZ *= 0.9;
                
                // Try to move back to safe position slowly
                double dx = lastSafePosition[0] - mc.thePlayer.posX;
                double dz = lastSafePosition[2] - mc.thePlayer.posZ;
                
                mc.thePlayer.motionX = dx * 0.1;
                mc.thePlayer.motionZ = dz * 0.1;
                mc.thePlayer.motionY = 0.05; // Slight upward motion
                
                if (debugMessages.isToggled() && mc.thePlayer.ticksExisted % 20 == 0) {
                    Utils.Player.sendMessageToSelf("§e[AntiVoid] Freeze mode active");
                }
            }
        }
    }
    
    private void updateSafePosition() {
        // Check if current position is safe (not over void)
        if (mc.thePlayer.onGround || isBlockBelow(3)) {
            lastSafePosition = new double[]{
                mc.thePlayer.posX,
                mc.thePlayer.posY,
                mc.thePlayer.posZ
            };
        }
    }
    
    private boolean isPlayerOverVoid() {
        // Check if player is above the safety height
        if (mc.thePlayer.posY < safetyHeight.getInput()) {
            return true;
        }
        
        // Check for blocks below player within void distance
        return !isBlockBelow((int) voidDistance.getInput());
    }
    
    private boolean isBlockBelow(int depth) {
        BlockPos playerPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        
        for (int i = 0; i <= depth; i++) {
            BlockPos checkPos = playerPos.down(i);
            Block block = mc.theWorld.getBlockState(checkPos).getBlock();
            
            if (!(block instanceof BlockAir) && !(block instanceof BlockLiquid)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean canUseAntiVoid() {
        // Don't activate if already flying or creative mode
        if (mc.thePlayer.capabilities.isFlying || mc.thePlayer.capabilities.isCreativeMode) {
            return false;
        }
        
        // Don't activate if on ladder
        if (mc.thePlayer.isOnLadder()) {
            return false;
        }
        
        // Don't activate if in water
        if (mc.thePlayer.isInWater() || mc.thePlayer.isInLava()) {
            return false;
        }
        
        return true;
    }
    
    private void releasePackets() {
        // Send all stored packets
        if (mc.getNetHandler() != null) {
            for (Packet<?> packet : blinkedPackets) {
                mc.getNetHandler().addToSendQueue(packet);
            }
        }
        blinkedPackets.clear();
    }
    
    private void resetState() {
        blinking = false;
        blinkTicks = 0;
        isOverVoid = false;
        wasOverVoid = false;
        blinkedPackets.clear();
    }
    
    @Override
    public String getInfo() {
        return AntiVoidMode.values()[mode.getMode()].name();
    }
    
    public enum AntiVoidMode {
        Blink,
        Position,
        Freeze
    }
}
