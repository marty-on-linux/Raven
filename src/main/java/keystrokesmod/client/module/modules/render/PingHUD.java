package keystrokesmod.client.module.modules.render;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.impl.Render2DEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.RGBSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.utils.RenderUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetworkPlayerInfo;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class PingHUD extends Module {
    private TickSetting colorBased;
    private TickSetting background;
    private TickSetting roundedCorners;
    private TickSetting showGraph;
    private SliderSetting posX;
    private SliderSetting posY;
    private SliderSetting backgroundOpacity;
    private SliderSetting cornerRadius;
    private SliderSetting fontSize;
    private RGBSetting backgroundColor;
    private RGBSetting textColor;
    private ComboSetting displayMode;
    
    private int[] pingHistory = new int[100];
    private int historyIndex = 0;
    
    public enum DisplayMode {
        FULL, COMPACT, ICON_ONLY
    }
    
    public PingHUD() {
        super("Ping HUD", ModuleCategory.render);
        
        // Display settings
        this.registerSetting(displayMode = new ComboSetting("Mode:", DisplayMode.FULL));
        this.registerSetting(colorBased = new TickSetting("Color by ping", true));
        this.registerSetting(showGraph = new TickSetting("Show graph", false));
        
        // Position settings
        this.registerSetting(posX = new SliderSetting("Position X", 5, 0, 500, 1));
        this.registerSetting(posY = new SliderSetting("Position Y", 150, 0, 500, 1));
        this.registerSetting(fontSize = new SliderSetting("Font size", 1.0, 0.5, 2.0, 0.1));
        
        // Style settings
        this.registerSetting(background = new TickSetting("Background", true));
        this.registerSetting(backgroundColor = new RGBSetting("BG Color:", 0, 0, 0));
        this.registerSetting(backgroundOpacity = new SliderSetting("BG Opacity", 0.5, 0, 1, 0.05));
        this.registerSetting(roundedCorners = new TickSetting("Rounded", true));
        this.registerSetting(cornerRadius = new SliderSetting("Corner radius", 4, 0, 15, 0.5));
        this.registerSetting(textColor = new RGBSetting("Text color:", 255, 255, 255));
    }
    
    @Subscribe
    public void onRender2D(Render2DEvent e) {
        if (mc.thePlayer == null) return;
        
        GL11.glPushMatrix();
        float scale = (float) fontSize.getInput();
        GL11.glScalef(scale, scale, scale);
        
        int x = (int) (posX.getInput() / scale);
        int y = (int) (posY.getInput() / scale);
        
        drawPing(x, y);
        
        GL11.glPopMatrix();
        
        // Update ping history
        int currentPing = getPing();
        pingHistory[historyIndex] = currentPing;
        historyIndex = (historyIndex + 1) % pingHistory.length;
    }
    
    private void drawPing(int x, int y) {
        int ping = getPing();
        DisplayMode mode = (DisplayMode) displayMode.getMode();
        
        String text = getPingText(ping, mode);
        int width = mc.fontRendererObj.getStringWidth(text) + 8;
        int height = mc.fontRendererObj.FONT_HEIGHT + 6;
        
        if (showGraph.isToggled() && mode != DisplayMode.ICON_ONLY) {
            height += 30;
        }
        
        // Draw background
        if (background.isToggled()) {
            int bgColor = backgroundColor.getRGB();
            int alpha = (int) (backgroundOpacity.getInput() * 255);
            int color = (alpha << 24) | (bgColor & 0xFFFFFF);
            
            if (roundedCorners.isToggled()) {
                RenderUtils.drawRoundedRect(x, y, x + width, y + height, 
                    (int) cornerRadius.getInput(), color);
            } else {
                Gui.drawRect(x, y, x + width, y + height, color);
            }
        }
        
        // Draw text
        int color = colorBased.isToggled() ? getPingColor(ping) : textColor.getRGB();
        mc.fontRendererObj.drawStringWithShadow(text, x + 4, y + 3, color);
        
        // Draw graph
        if (showGraph.isToggled() && mode != DisplayMode.ICON_ONLY) {
            drawPingGraph(x + 4, y + mc.fontRendererObj.FONT_HEIGHT + 6, width - 8, 25);
        }
    }
    
    private String getPingText(int ping, DisplayMode mode) {
        switch (mode) {
            case ICON_ONLY:
                return ping + "ms";
            case COMPACT:
                return "Ping: " + ping;
            case FULL:
            default:
                return "Ping: " + ping + "ms";
        }
    }
    
    private void drawPingGraph(int x, int y, int width, int height) {
        // Draw graph background
        Gui.drawRect(x, y, x + width, y + height, 0x40000000);
        
        // Draw ping history (inverted - lower is better)
        int maxPing = 300; // Max ping for scaling
        for (int i = 0; i < pingHistory.length && i < width; i++) {
            int pingIndex = (historyIndex - pingHistory.length + i + pingHistory.length) % pingHistory.length;
            int ping = pingHistory[pingIndex];
            
            int barHeight = (int) ((float) ping / maxPing * height);
            if (barHeight > height) barHeight = height;
            
            int color = getPingColor(ping);
            Gui.drawRect(x + i, y + height - barHeight, x + i + 1, y + height, color);
        }
    }
    
    private int getPing() {
        try {
            if (mc.thePlayer != null && mc.getNetHandler() != null) {
                NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
                if (playerInfo != null) {
                    return playerInfo.getResponseTime();
                }
            }
        } catch (Exception e) {
            // Handle error silently
        }
        return 0;
    }
    
    private int getPingColor(int ping) {
        if (ping < 50) {
            return Color.GREEN.getRGB();
        } else if (ping < 100) {
            return Color.YELLOW.getRGB();
        } else if (ping < 200) {
            return Color.ORANGE.getRGB();
        } else {
            return Color.RED.getRGB();
        }
    }
}
