package keystrokesmod.client.module.modules;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.impl.Render2DEvent;
import keystrokesmod.client.main.Raven;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.modules.client.FakeHud;
import keystrokesmod.client.module.setting.Setting;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.RGBSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.RenderUtils;
import keystrokesmod.client.utils.Utils;
import keystrokesmod.client.utils.font.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.config.GuiButtonExt;

public class HUD extends Module {
    public static TickSetting editPosition, dropShadow, logo, background, roundedCorners, customFont, blur, fadeIn;
    public static ComboSetting logoMode, backgroundMode;
    public static SliderSetting colourMode, logoScaleh, logoScalew, fontSize, textSpacing, backgroundOpacity, cornerRadius, padding, glowStrength;
    public static RGBSetting backgroundColor, customTextColor;
    public static DescriptionSetting colourModeDesc, logoDesc1, logoDesc2;
    private static int hudX = 5;
    private static int hudY = 70;
    private double logoHeight;
    public static boolean e;

    private InputStream inputStream;
    private ResourceLocation ravenLogo;

    public static Utils.HUD.PositionMode positionMode;
    public static boolean showedError;
    public static final String HUDX_prefix = "HUDX~ ";
    public static final String HUDY_prefix = "HUDY~ ";

    public enum lmv {
        l1, l2, l3, l4, l5, l6, l7, CD
    }

    public HUD() {
        super("HUD", ModuleCategory.render);
        // Position & Edit
        this.registerSetting(editPosition = new TickSetting("Edit position", false));
        
        // Text Settings
        this.registerSetting(dropShadow = new TickSetting("Drop shadow", true));
        this.registerSetting(customFont = new TickSetting("Custom font", false));
        this.registerSetting(fontSize = new SliderSetting("Font size", 1.0, 0.5, 2.0, 0.05));
        this.registerSetting(textSpacing = new SliderSetting("Text spacing", 2, 0, 10, 0.5));
        
        // Color Settings
        this.registerSetting(colourMode = new SliderSetting("Color mode", 1, 1, 7, 1));
        this.registerSetting(colourModeDesc = new DescriptionSetting("Mode: RAVEN"));
        this.registerSetting(customTextColor = new RGBSetting("Custom color:", 255, 255, 255));
        
        // Background Settings
        this.registerSetting(background = new TickSetting("Background", true));
        this.registerSetting(backgroundMode = new ComboSetting("BG Mode:", BackgroundMode.SOLID));
        this.registerSetting(backgroundColor = new RGBSetting("BG color:", 0, 0, 0));
        this.registerSetting(backgroundOpacity = new SliderSetting("BG opacity", 0.5, 0, 1, 0.05));
        this.registerSetting(roundedCorners = new TickSetting("Rounded corners", true));
        this.registerSetting(cornerRadius = new SliderSetting("Corner radius", 4, 0, 15, 0.5));
        this.registerSetting(padding = new SliderSetting("Padding", 4, 0, 20, 1));
        this.registerSetting(blur = new TickSetting("Blur background", false));
        
        // Effects
        this.registerSetting(glowStrength = new SliderSetting("Glow strength", 0, 0, 10, 0.5));
        this.registerSetting(fadeIn = new TickSetting("Fade in animation", false));
        
        // Logo Settings
        this.registerSetting(logo = new TickSetting("Logo", true));
        this.registerSetting(logoScaleh = new SliderSetting("Logo height scale", 1, 0, 10, 0.01));
        this.registerSetting(logoScalew = new SliderSetting("Logo width scale", 2, 0, 10, 0.01));
        this.registerSetting(logoMode = new ComboSetting("Logo Mode:", lmv.l7));
        this.registerSetting(logoDesc1 = new DescriptionSetting("cd logomode put an image logo.png"));
        this.registerSetting(logoDesc2 = new DescriptionSetting("in the keystrokes folder"));
        
        showedError = false;
        showInHud = false;
    }

    private void setUpLogo() {
        RenderUtils.getResourcePath("/assets/keystrokes/logohud/" + logoMode.getMode().toString() + ".png");
    }

    @Override
	public void postApplyConfig() {
        setUpLogo();
    }

    @Override
	public void guiButtonToggled(Setting b) {
        if (b == logoMode)
            setUpLogo();
        else if (b == editPosition) {
            editPosition.disable();
            mc.displayGuiScreen(new EditHudPositionScreen());
        }
    }

    public boolean logoLoaded() {
        return (ravenLogo != null) && logo.isToggled();
    }

    @Override
	public void guiUpdate() {
        int colorIndex = (int) colourMode.getInput() - 1;
        if (colorIndex >= 0 && colorIndex < ColourModes.values().length) {
            colourModeDesc.setDesc(Utils.md + ColourModes.values()[colorIndex]);
        }
    }

    @Override
	public void onEnable() {
        Raven.moduleManager.sort();
    }

    @Subscribe
    public void onRender2D(Render2DEvent ev) {
        if (Utils.Player.isPlayerInGame()) {
            if ((mc.currentScreen != null) || mc.gameSettings.showDebugInfo)
				return;
            boolean fhe = Raven.moduleManager.getModuleByName("Fake Hud").isEnabled();
            if (!e) {
                ScaledResolution sr = new ScaledResolution(mc);
                positionMode = Utils.HUD.getPostitionMode(hudX, hudY, sr.getScaledWidth(), sr.getScaledHeight());
                if ((positionMode == Utils.HUD.PositionMode.UPLEFT) || (positionMode == Utils.HUD.PositionMode.UPRIGHT)) {
                    if (!fhe)
						Raven.moduleManager.sortShortLong();
					else
						FakeHud.sortShortLong();
                } else if ((positionMode == Utils.HUD.PositionMode.DOWNLEFT)
                        || (positionMode == Utils.HUD.PositionMode.DOWNRIGHT))
					if (!fhe)
						Raven.moduleManager.sortLongShort();
					else
						FakeHud.sortLongShort();
                e = true;
            }
            
            double margin = textSpacing.getInput();
            double scaleFactor = fontSize.getInput();
            int y = hudY;
            int del = 0;

            List<Module> en = fhe ? FakeHud.getModules() : new ArrayList<>(Raven.moduleManager.getModules());
            if (en.isEmpty())
                return;

            int textBoxWidth = Raven.moduleManager.getLongestActiveModule(mc.fontRendererObj);
            int textBoxHeight = Raven.moduleManager.getBoxHeight(mc.fontRendererObj, margin);

            if (hudX < 0)
				hudX = margin;
            if (hudY < 0)
				hudY = margin;

            if ((hudX + textBoxWidth) > (mc.displayWidth / 2))
				hudX = (mc.displayWidth / 2) - textBoxWidth - margin;

            if ((hudY + textBoxHeight) > (mc.displayHeight / 2))
				hudY = (mc.displayHeight / 2) - textBoxHeight;

            // Draw background if enabled
            if (background.isToggled()) {
                drawHudBackground(hudX, hudY, textBoxWidth, textBoxHeight);
            }
            
            drawLogo(textBoxWidth);
            y += logoHeight;
            
            // Apply font scaling
            GL11.glPushMatrix();
            double scale = fontSize.getInput();
            GL11.glScaled(scale, scale, scale);
            
            for (Module m : en)
						if (m.isEnabled() && m.showInHud()) {
                    boolean rightAlign = (positionMode == Utils.HUD.PositionMode.DOWNRIGHT) 
                        || (positionMode == Utils.HUD.PositionMode.UPRIGHT);
                    
                    y = renderModule(m, hudX, y, textBoxWidth, del, rightAlign, margin);
                    del -= getColorDelayOffset();
                }
            
            GL11.glPopMatrix();
        }

    }
    
    private void drawHudBackground(int x, int y, int width, int height) {
        int padding = (int) this.padding.getInput();
        int bgX = x - padding;
        int bgY = y - padding;
        int bgWidth = width + (padding * 2);
        int bgHeight = height + (padding * 2) + (int) logoHeight;
        
        int bgColor = backgroundColor.getRGB();
        int alpha = (int) (backgroundOpacity.getInput() * 255);
        int color = (alpha << 24) | (bgColor & 0xFFFFFF);
        
        if (roundedCorners.isToggled()) {
            RenderUtils.drawRoundedRect(bgX, bgY, bgX + bgWidth, bgY + bgHeight, 
                (int) cornerRadius.getInput(), color);
        } else {
            drawRect(bgX, bgY, bgX + bgWidth, bgY + bgHeight, color);
        }
        
        // Draw glow effect if enabled
        if (glowStrength.getInput() > 0) {
            drawGlowEffect(bgX, bgY, bgWidth, bgHeight, (int) glowStrength.getInput());
        }
    }
    
    private void drawGlowEffect(int x, int y, int width, int height, int strength) {
        // Simple glow by drawing slightly larger semi-transparent rectangles
        for (int i = 1; i <= strength; i++) {
            int alpha = (int) (30 / i);
            int glowColor = (alpha << 24) | (backgroundColor.getRGB() & 0xFFFFFF);
            if (roundedCorners.isToggled()) {
                RenderUtils.drawRoundedRect(x - i, y - i, x + width + i, y + height + i,
                    (int) cornerRadius.getInput() + i, glowColor);
            } else {
                drawRect(x - i, y - i, x + width + i, y + height + i, glowColor);
            }
        }
    }
    
    private int getModuleColor(int offset) {
        int colorMode = (int) colourMode.getInput() - 1;
        if (colorMode < 0 || colorMode >= ColourModes.values().length) {
            return -1; // White
        }
        
        ColourModes mode = ColourModes.values()[colorMode];
        switch (mode) {
            case RAVEN:
                return Utils.Client.rainbowDraw(2L, offset);
            case RAVEN2:
                return Utils.Client.rainbowDraw(2L, offset);
            case ASTOLFO:
            case ASTOLFO2:
            case ASTOLFO3:
                return Utils.Client.astolfoColorsDraw(10, offset);
            case KV:
                return Utils.Client.customDraw(offset);
            case CUSTOM:
                return customTextColor.getRGB();
            default:
                return -1;
        }
    }
    
    private int renderModule(Module module, int baseX, int baseY, int maxWidth, int colorOffset, boolean rightAlign, double margin) {
        String name = module.getName();
        int color = getModuleColor(colorOffset);
        float x = baseX;
        
        if (rightAlign) {
            x = baseX + (maxWidth - mc.fontRendererObj.getStringWidth(name));
        }
        
        ColourModes mode = getCurrentColorMode();
        
        // Use custom font for KV mode
        if (mode == ColourModes.KV) {
            if (rightAlign) {
                FontUtil.two.drawString(name, (double) baseX + (maxWidth - mc.fontRendererObj.getStringWidth(name)), 
                    baseY, color, dropShadow.isToggled(), 10);
            } else {
                FontUtil.two.drawString(name, x, baseY, color, dropShadow.isToggled(), 10);
            }
            return baseY + mc.fontRendererObj.FONT_HEIGHT + (int) margin - (mode == ColourModes.KV ? 2 : 0);
        } else {
            mc.fontRendererObj.drawString(name, x, baseY, color, dropShadow.isToggled());
            return baseY + mc.fontRendererObj.FONT_HEIGHT + (int) margin;
        }
    }
    
    private ColourModes getCurrentColorMode() {
        int colorMode = (int) colourMode.getInput() - 1;
        if (colorMode >= 0 && colorMode < ColourModes.values().length) {
            return ColourModes.values()[colorMode];
        }
        return ColourModes.RAVEN;
    }
    
    private int getColorDelayOffset() {
        ColourModes mode = getCurrentColorMode();
        switch (mode) {
            case RAVEN:
            case ASTOLFO:
            case ASTOLFO2:
                return 120;
            case RAVEN2:
            case ASTOLFO3:
            case KV:
            case CUSTOM:
            default:
                return 10;
        }
    }

    private void drawLogo(int e) {

        ScaledResolution sr = new ScaledResolution(mc);
        logoHeight = (sr.getScaledHeight() * logoScaleh.getInput()) / 10;
        if (logoLoaded()) {
            if ((positionMode == Utils.HUD.PositionMode.DOWNRIGHT) || (positionMode == Utils.HUD.PositionMode.UPRIGHT)) {
                double logoWidth = (sr.getScaledWidth() * logoScalew.getInput()) / 8;
                Minecraft.getMinecraft().getTextureManager().bindTexture(ravenLogo);
                GL11.glColor4f(1, 1, 1, 1);
				Gui.drawModalRectWithCustomSizedTexture((int) ((hudX + e) - logoWidth), hudY, 0, 0, (int) logoWidth,
						(int) logoHeight, (int) logoWidth, (int) logoHeight);
            } else {
                double logoWidth = (sr.getScaledWidth() * logoScalew.getInput()) / 8;
                Minecraft.getMinecraft().getTextureManager().bindTexture(ravenLogo);
                GL11.glColor4f(1, 1, 1, 1);
                Gui.drawModalRectWithCustomSizedTexture(hudX, hudY, 0, 0, (int) logoWidth, (int) logoHeight,
                        (int) logoWidth, (int) logoHeight);
            }
        } else
			logoHeight = 0;
    }

    static class EditHudPositionScreen extends GuiScreen {
        final String hudTextExample = "This is an-Example-HUD";
        GuiButtonExt resetPosButton;
        boolean mouseDown;
        int textBoxStartX;
        int textBoxStartY;
        ScaledResolution sr;
        int textBoxEndX;
        int textBoxEndY;
        int marginX = 5;
        int marginY = 70;
        int lastMousePosX;
        int lastMousePosY;
        int sessionMousePosX;
        int sessionMousePosY;

        @Override
		public void initGui() {
            super.initGui();
            this.buttonList
                    .add(this.resetPosButton = new GuiButtonExt(1, this.width - 90, 5, 85, 20, "Reset position"));
            this.marginX = hudX;
            this.marginY = hudY;
            sr = new ScaledResolution(mc);
            positionMode = Utils.HUD.getPostitionMode(marginX, marginY, sr.getScaledWidth(), sr.getScaledHeight());
            e = false;
        }

        @Override
		public void drawScreen(int mX, int mY, float pt) {
            drawRect(0, 0, this.width, this.height, -1308622848);
            drawRect(0, this.height / 2, this.width, (this.height / 2) + 1, 0x9936393f);
            drawRect(this.width / 2, 0, (this.width / 2) + 1, this.height, 0x9936393f);
            int textBoxStartX = this.marginX;
            int textBoxStartY = this.marginY;
            int textBoxEndX = textBoxStartX + 50;
            int textBoxEndY = textBoxStartY + 32;
            this.drawArrayList(this.mc.fontRendererObj, this.hudTextExample);
            this.textBoxStartX = textBoxStartX;
            this.textBoxStartY = textBoxStartY;
            this.textBoxEndX = textBoxEndX;
            this.textBoxEndY = textBoxEndY;
            hudX = textBoxStartX;
            hudY = textBoxStartY;
            ScaledResolution res = new ScaledResolution(this.mc);
            int descriptionOffsetX = (res.getScaledWidth() / 2) - 84;
            int descriptionOffsetY = (res.getScaledHeight() / 2) - 20;
            Utils.HUD.drawColouredText("Edit the HUD position by dragging.", '-', descriptionOffsetX,
                    descriptionOffsetY, 2L, 0L, true, this.mc.fontRendererObj);

            try {
                this.handleInput();
            } catch (IOException var12) {
            }

            super.drawScreen(mX, mY, pt);
        }

        private void drawArrayList(FontRenderer fr, String t) {
            int x = this.textBoxStartX;
            int gap = this.textBoxEndX - this.textBoxStartX;
            int y = this.textBoxStartY;
            double marginY = fr.FONT_HEIGHT + 2;
            String[] var4 = t.split("-");
            ArrayList<String> var5 = Utils.Java.toArrayList(var4);
            if ((positionMode == Utils.HUD.PositionMode.UPLEFT) || (positionMode == Utils.HUD.PositionMode.UPRIGHT))
				var5.sort((o1, o2) -> Utils.mc.fontRendererObj.getStringWidth(o2)
                        - Utils.mc.fontRendererObj.getStringWidth(o1));
			else if ((positionMode == Utils.HUD.PositionMode.DOWNLEFT)
                    || (positionMode == Utils.HUD.PositionMode.DOWNRIGHT))
				var5.sort(Comparator.comparingInt(o2 -> Utils.mc.fontRendererObj.getStringWidth(o2)));

            if ((positionMode == Utils.HUD.PositionMode.DOWNRIGHT) || (positionMode == Utils.HUD.PositionMode.UPRIGHT))
				for (String s : var5) {
                    fr.drawString(s, (float) x + (gap - fr.getStringWidth(s)), (float) y, Color.white.getRGB(),
                            dropShadow.isToggled());
                    y += marginY;
                }
			else
				for (String s : var5) {
                    fr.drawString(s, (float) x, (float) y, Color.white.getRGB(), dropShadow.isToggled());
                    y += marginY;
                }
        }

        @Override
		protected void mouseClickMove(int mousePosX, int mousePosY, int clickedMouseButton, long timeSinceLastClick) {
            super.mouseClickMove(mousePosX, mousePosY, clickedMouseButton, timeSinceLastClick);
            if (clickedMouseButton == 0)
				if (this.mouseDown) {
                    this.marginX = this.lastMousePosX + (mousePosX - this.sessionMousePosX);
                    this.marginY = this.lastMousePosY + (mousePosY - this.sessionMousePosY);
                    sr = new ScaledResolution(mc);
                    positionMode = Utils.HUD.getPostitionMode(marginX, marginY, sr.getScaledWidth(),
                            sr.getScaledHeight());

                    // in the else if statement, we check if the mouse is clicked AND inside the
                    // "text box"
                } else if ((mousePosX > this.textBoxStartX) && (mousePosX < this.textBoxEndX)
                        && (mousePosY > this.textBoxStartY) && (mousePosY < this.textBoxEndY)) {
                    this.mouseDown = true;
                    this.sessionMousePosX = mousePosX;
                    this.sessionMousePosY = mousePosY;
                    this.lastMousePosX = this.marginX;
                    this.lastMousePosY = this.marginY;
                }
        }

        @Override
		protected void mouseReleased(int mX, int mY, int state) {
            super.mouseReleased(mX, mY, state);
            if (state == 0)
				this.mouseDown = false;

        }

        @Override
		public void actionPerformed(GuiButton b) {
            if (b == this.resetPosButton) {
                this.marginX = hudX = 5;
                this.marginY = hudY = 70;
            }

        }

        @Override
		public boolean doesGuiPauseGame() {
            return false;
        }
    }

    public enum ColourModes {
        RAVEN, RAVEN2, ASTOLFO, ASTOLFO2, ASTOLFO3, KV, CUSTOM
    }
    
    public enum BackgroundMode {
        SOLID, GRADIENT, BLUR
    }

    public static int getHudX() {
        return hudX;
    }

    public static int getHudY() {
        return hudY;
    }

    public static void setHudX(int hudX) {
        HUD.hudX = hudX;
    }

    public static void setHudY(int hudY) {
        HUD.hudY = hudY;
    }
}
