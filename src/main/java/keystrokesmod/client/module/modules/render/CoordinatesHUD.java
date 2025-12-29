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
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class CoordinatesHUD extends Module {
    private TickSetting showNetherCoords;
    private TickSetting showDirection;
    private TickSetting background;
    private TickSetting roundedCorners;
    private TickSetting showBiome;
    private SliderSetting posX;
    private SliderSetting posY;
    private SliderSetting backgroundOpacity;
    private SliderSetting cornerRadius;
    private SliderSetting fontSize;
    private RGBSetting backgroundColor;
    private RGBSetting textColor;
    private ComboSetting displayMode;
    
    public enum DisplayMode {
        FULL, COMPACT, MINIMAL
    }
    
    public CoordinatesHUD() {
        super("Coordinates HUD", ModuleCategory.render);
        
        // Display settings
        this.registerSetting(displayMode = new ComboSetting("Mode:", DisplayMode.FULL));
        this.registerSetting(showNetherCoords = new TickSetting("Nether coords", true));
        this.registerSetting(showDirection = new TickSetting("Direction", true));
        this.registerSetting(showBiome = new TickSetting("Biome", true));
        
        // Position settings
        this.registerSetting(posX = new SliderSetting("Position X", 5, 0, 500, 1));
        this.registerSetting(posY = new SliderSetting("Position Y", 200, 0, 500, 1));
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
        
        Entity entity = mc.getRenderViewEntity();
        if (entity == null) return;
        
        GL11.glPushMatrix();
        float scale = (float) fontSize.getInput();
        GL11.glScalef(scale, scale, scale);
        
        int x = (int) (posX.getInput() / scale);
        int y = (int) (posY.getInput() / scale);
        
        drawCoordinates(x, y);
        
        GL11.glPopMatrix();
    }
    
    private void drawCoordinates(int x, int y) {
        Entity entity = mc.getRenderViewEntity();
        int posX = (int) Math.floor(entity.posX);
        int posY = (int) Math.floor(entity.posY);
        int posZ = (int) Math.floor(entity.posZ);
        
        DisplayMode mode = (DisplayMode) displayMode.getMode();
        String[] lines = getDisplayLines(posX, posY, posZ, mode);
        
        int maxWidth = 0;
        for (String line : lines) {
            int width = mc.fontRendererObj.getStringWidth(line);
            if (width > maxWidth) maxWidth = width;
        }
        
        int height = lines.length * (mc.fontRendererObj.FONT_HEIGHT + 2) + 4;
        int width = maxWidth + 8;
        
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
        int textY = y + 4;
        int color = textColor.getRGB();
        for (String line : lines) {
            mc.fontRendererObj.drawStringWithShadow(line, x + 4, textY, color);
            textY += mc.fontRendererObj.FONT_HEIGHT + 2;
        }
    }
    
    private String[] getDisplayLines(int x, int y, int z, DisplayMode mode) {
        switch (mode) {
            case COMPACT:
                return getCompactDisplay(x, y, z);
            case MINIMAL:
                return getMinimalDisplay(x, y, z);
            case FULL:
            default:
                return getFullDisplay(x, y, z);
        }
    }
    
    private String[] getFullDisplay(int x, int y, int z) {
        int lineCount = 1;
        if (showNetherCoords.isToggled()) lineCount++;
        if (showDirection.isToggled()) lineCount++;
        if (showBiome.isToggled()) lineCount++;
        
        String[] lines = new String[lineCount];
        int index = 0;
        
        lines[index++] = String.format("XYZ: %d, %d, %d", x, y, z);
        
        if (showNetherCoords.isToggled()) {
            if (mc.thePlayer.dimension == -1) {
                lines[index++] = String.format("Overworld: %d, %d, %d", x * 8, y, z * 8);
            } else {
                lines[index++] = String.format("Nether: %d, %d, %d", x / 8, y, z / 8);
            }
        }
        
        if (showDirection.isToggled()) {
            lines[index++] = "Facing: " + getDirection();
        }
        
        if (showBiome.isToggled()) {
            lines[index++] = "Biome: " + getBiome();
        }
        
        return lines;
    }
    
    private String[] getCompactDisplay(int x, int y, int z) {
        int lineCount = showNetherCoords.isToggled() ? 2 : 1;
        String[] lines = new String[lineCount];
        
        String direction = showDirection.isToggled() ? " " + getDirection() : "";
        lines[0] = String.format("%d, %d, %d%s", x, y, z, direction);
        
        if (showNetherCoords.isToggled()) {
            if (mc.thePlayer.dimension == -1) {
                lines[1] = String.format("OW: %d, %d, %d", x * 8, y, z * 8);
            } else {
                lines[1] = String.format("N: %d, %d, %d", x / 8, y, z / 8);
            }
        }
        
        return lines;
    }
    
    private String[] getMinimalDisplay(int x, int y, int z) {
        return new String[] { String.format("%d %d %d", x, y, z) };
    }
    
    private String getDirection() {
        int direction = (int) ((mc.thePlayer.rotationYaw + 180) / 45) % 8;
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return directions[direction];
    }
    
    private String getBiome() {
        try {
            return mc.theWorld.getBiomeGenForCoords(mc.thePlayer.getPosition()).biomeName;
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
