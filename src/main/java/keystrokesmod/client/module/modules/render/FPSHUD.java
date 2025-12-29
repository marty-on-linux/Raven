package keystrokesmod.client.module.modules.render;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.impl.Render2DEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.RGBSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.utils.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class FPSHUD extends Module {
    private TickSetting showAverage;
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
    
    private int[] fpsHistory = new int[100];
    private int historyIndex = 0;
    
    public enum DisplayMode {
        FULL, COMPACT, ICON_ONLY
    }
    
    public FPSHUD() {
        super("FPS HUD", ModuleCategory.render);
        
        // Display settings
        this.registerSetting(displayMode = new ComboSetting("Mode:", DisplayMode.FULL));
        this.registerSetting(showAverage = new TickSetting("Show average", true));
        this.registerSetting(colorBased = new TickSetting("Color by FPS", true));
        this.registerSetting(showGraph = new TickSetting("Show graph", false));
        
        // Position settings
        this.registerSetting(posX = new SliderSetting("Position X", 5, 0, 500, 1));
        this.registerSetting(posY = new SliderSetting("Position Y", 100, 0, 500, 1));
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
        GL11.glPushMatrix();
        float scale = (float) fontSize.getInput();
        GL11.glScalef(scale, scale, scale);
        
        int x = (int) (posX.getInput() / scale);
        int y = (int) (posY.getInput() / scale);
        
        drawFPS(x, y);
        
        GL11.glPopMatrix();
        
        // Update FPS history
        int currentFps = Minecraft.getDebugFPS();
        fpsHistory[historyIndex] = currentFps;
        historyIndex = (historyIndex + 1) % fpsHistory.length;
    }
    
    private void drawFPS(int x, int y) {
        int fps = Minecraft.getDebugFPS();
        DisplayMode mode = (DisplayMode) displayMode.getMode();
        
        String text = getFPSText(fps, mode);
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
        int color = colorBased.isToggled() ? getFPSColor(fps) : textColor.getRGB();
        mc.fontRendererObj.drawStringWithShadow(text, x + 4, y + 3, color);
        
        // Draw graph
        if (showGraph.isToggled() && mode != DisplayMode.ICON_ONLY) {
            drawFPSGraph(x + 4, y + mc.fontRendererObj.FONT_HEIGHT + 6, width - 8, 25);
        }
    }
    
    private String getFPSText(int fps, DisplayMode mode) {
        switch (mode) {
            case ICON_ONLY:
                return String.valueOf(fps);
            case COMPACT:
                return "FPS: " + fps;
            case FULL:
            default:
                if (showAverage.isToggled()) {
                    int avg = getAverageFPS();
                    return String.format("FPS: %d (Avg: %d)", fps, avg);
                }
                return "FPS: " + fps;
        }
    }
    
    private void drawFPSGraph(int x, int y, int width, int height) {
        // Draw graph background
        Gui.drawRect(x, y, x + width, y + height, 0x40000000);
        
        // Draw FPS history
        int maxFps = 200; // Max FPS for scaling
        for (int i = 0; i < fpsHistory.length && i < width; i++) {
            int fpsIndex = (historyIndex - fpsHistory.length + i + fpsHistory.length) % fpsHistory.length;
            int fps = fpsHistory[fpsIndex];
            
            int barHeight = (int) ((float) fps / maxFps * height);
            if (barHeight > height) barHeight = height;
            
            int color = getFPSColor(fps);
            Gui.drawRect(x + i, y + height - barHeight, x + i + 1, y + height, color);
        }
    }
    
    private int getFPSColor(int fps) {
        if (fps >= 60) {
            return Color.GREEN.getRGB();
        } else if (fps >= 30) {
            return Color.YELLOW.getRGB();
        } else {
            return Color.RED.getRGB();
        }
    }
    
    private int getAverageFPS() {
        int sum = 0;
        int count = 0;
        for (int fps : fpsHistory) {
            if (fps > 0) {
                sum += fps;
                count++;
            }
        }
        return count > 0 ? sum / count : 0;
    }
}
