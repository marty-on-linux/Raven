package keystrokesmod.client.module.modules.render;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.event.impl.Render2DEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.RGBSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class TargetHUD extends Module {
    // Settings
    private TickSetting showHead;
    private TickSetting showHealth;
    private TickSetting showArmor;
    private TickSetting showDistance;
    private TickSetting background;
    private TickSetting roundedCorners;
    private SliderSetting posX;
    private SliderSetting posY;
    private SliderSetting backgroundOpacity;
    private SliderSetting cornerRadius;
    private SliderSetting scale;
    private SliderSetting fadeOutTime;
    private RGBSetting backgroundColor;
    private RGBSetting healthBarColor;
    
    // State
    private EntityLivingBase target;
    private long lastAttackTime;
    private float animatedHealth;
    private float displayHealth;
    
    public TargetHUD() {
        super("Target HUD", ModuleCategory.render);
        
        // Display settings
        this.registerSetting(showHead = new TickSetting("Show head", true));
        this.registerSetting(showHealth = new TickSetting("Show health", true));
        this.registerSetting(showArmor = new TickSetting("Show armor", true));
        this.registerSetting(showDistance = new TickSetting("Show distance", true));
        
        // Position settings
        this.registerSetting(posX = new SliderSetting("Position X", 5, 0, 500, 1));
        this.registerSetting(posY = new SliderSetting("Position Y", 5, 0, 500, 1));
        this.registerSetting(scale = new SliderSetting("Scale", 1.0, 0.5, 2.0, 0.1));
        
        // Style settings
        this.registerSetting(background = new TickSetting("Background", true));
        this.registerSetting(backgroundColor = new RGBSetting("BG Color:", 0, 0, 0));
        this.registerSetting(backgroundOpacity = new SliderSetting("BG Opacity", 0.5, 0, 1, 0.05));
        this.registerSetting(roundedCorners = new TickSetting("Rounded", true));
        this.registerSetting(cornerRadius = new SliderSetting("Corner radius", 4, 0, 15, 0.5));
        
        // Animation settings
        this.registerSetting(fadeOutTime = new SliderSetting("Fade time (ms)", 2000, 500, 5000, 100));
        this.registerSetting(healthBarColor = new RGBSetting("Health color:", 255, 0, 0));
        
        animatedHealth = 0;
        displayHealth = 0;
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (fe.getEvent() instanceof AttackEntityEvent) {
            AttackEntityEvent event = (AttackEntityEvent) fe.getEvent();
            if (event.target instanceof EntityLivingBase) {
                target = (EntityLivingBase) event.target;
                lastAttackTime = System.currentTimeMillis();
                displayHealth = target.getHealth();
            }
        }
    }

    @Subscribe
    public void onRender2d(Render2DEvent e) {
        if (target == null) return;
        
        // Fade out after timeout
        long timeSinceAttack = System.currentTimeMillis() - lastAttackTime;
        if (timeSinceAttack > fadeOutTime.getInput()) {
            target = null;
            return;
        }
        
        // Check if target is still valid
        if (target.isDead || !mc.theWorld.loadedEntityList.contains(target)) {
            target = null;
            return;
        }
        
        // Smooth health animation
        float targetHealth = target.getHealth();
        animatedHealth += (targetHealth - animatedHealth) * 0.1f;
        
        // Calculate fade alpha
        float fadeAlpha = 1.0f;
        if (timeSinceAttack > fadeOutTime.getInput() - 500) {
            fadeAlpha = 1.0f - ((timeSinceAttack - (fadeOutTime.getInput() - 500)) / 500f);
        }
        
        GL11.glPushMatrix();
        float scaleValue = (float) scale.getInput();
        GL11.glScalef(scaleValue, scaleValue, scaleValue);
        
        int x = (int) (posX.getInput() / scaleValue);
        int y = (int) (posY.getInput() / scaleValue);
        
        drawTargetHUD(x, y, fadeAlpha);
        
        GL11.glPopMatrix();
    }
    
    private void drawTargetHUD(int x, int y, float alpha) {
        FontRenderer fr = mc.fontRendererObj;
        int width = 140;
        int height = 45;
        
        // Draw background
        if (background.isToggled()) {
            int bgColor = backgroundColor.getRGB();
            int bgAlpha = (int) (backgroundOpacity.getInput() * 255 * alpha);
            int color = (bgAlpha << 24) | (bgColor & 0xFFFFFF);
            
            if (roundedCorners.isToggled()) {
                RenderUtils.drawRoundedRect(x, y, x + width, y + height, 
                    (int) cornerRadius.getInput(), color);
            } else {
                Gui.drawRect(x, y, x + width, y + height, color);
            }
        }
        
        int contentX = x + 3;
        int contentY = y + 3;
        
        // Draw player head
        if (showHead.isToggled() && target instanceof AbstractClientPlayer) {
            drawPlayerHead((AbstractClientPlayer) target, contentX, contentY, 30, alpha);
            contentX += 35;
        }
        
        // Draw player name
        String name = target.getName();
        fr.drawStringWithShadow(name, contentX, contentY, applyAlpha(0xFFFFFF, alpha));
        
        contentY += 10;
        
        // Draw health bar
        if (showHealth.isToggled()) {
            float health = animatedHealth;
            float maxHealth = target.getMaxHealth();
            float healthPercent = Math.min(health / maxHealth, 1.0f);
            
            int barWidth = width - (contentX - x) - 6;
            int barHeight = 4;
            
            // Background bar
            Gui.drawRect(contentX, contentY, contentX + barWidth, contentY + barHeight, 
                applyAlpha(0x80000000, alpha));
            
            // Health bar
            int healthBarWidth = (int) (barWidth * healthPercent);
            int healthColor = getHealthColor(healthPercent);
            Gui.drawRect(contentX, contentY, contentX + healthBarWidth, contentY + barHeight,
                applyAlpha(healthColor, alpha));
            
            // Health text
            String healthText = String.format("%.1f / %.1f", health, maxHealth);
            fr.drawStringWithShadow(healthText, contentX + barWidth / 2 - fr.getStringWidth(healthText) / 2,
                contentY + barHeight + 2, applyAlpha(0xFFFFFF, alpha));
            
            contentY += barHeight + 12;
        }
        
        // Draw distance
        if (showDistance.isToggled()) {
            double distance = mc.thePlayer.getDistanceToEntity(target);
            String distanceText = String.format("Distance: %.1fm", distance);
            fr.drawStringWithShadow(distanceText, contentX, contentY, applyAlpha(0xAAAAAA, alpha));
            contentY += 10;
        }
        
        // Draw armor
        if (showArmor.isToggled() && target instanceof EntityPlayer) {
            drawArmor((EntityPlayer) target, contentX, contentY, alpha);
        }
    }
    
    private void drawPlayerHead(AbstractClientPlayer player, int x, int y, int size, float alpha) {
        ResourceLocation skin = player.getLocationSkin();
        mc.getTextureManager().bindTexture(skin);
        
        GlStateManager.enableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, alpha);
        
        // Draw face
        Gui.drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, size, size, 64, 64);
        
        // Draw overlay
        Gui.drawScaledCustomSizeModalRect(x, y, 40, 8, 8, 8, size, size, 64, 64);
        
        GlStateManager.disableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    private void drawArmor(EntityPlayer player, int x, int y, float alpha) {
        int armorX = x;
        
        for (int i = 3; i >= 0; i--) {
            ItemStack stack = player.inventory.armorInventory[i];
            if (stack != null) {
                GlStateManager.enableRescaleNormal();
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                GlStateManager.color(1.0f, 1.0f, 1.0f, alpha);
                
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack, armorX, y);
                mc.getRenderItem().renderItemOverlays(mc.fontRendererObj, stack, armorX, y);
                
                GlStateManager.disableRescaleNormal();
                GlStateManager.disableBlend();
                
                armorX += 20;
            }
        }
    }
    
    private int getHealthColor(float percent) {
        if (percent > 0.5f) {
            return Color.GREEN.getRGB();
        } else if (percent > 0.25f) {
            return Color.YELLOW.getRGB();
        } else {
            return Color.RED.getRGB();
        }
    }
    
    private int applyAlpha(int color, float alpha) {
        int a = (int) ((color >> 24 & 0xFF) * alpha);
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
