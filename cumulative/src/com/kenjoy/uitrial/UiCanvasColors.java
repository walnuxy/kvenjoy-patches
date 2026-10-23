package com.kenjoy.uitrial;
import java.awt.*;
import javax.swing.JComponent;

/** Screen-only palette hook; geometry, selection, documents and exports are unchanged. */
public final class UiCanvasColors {
    public static final String KEY="trial.canvasPalette";
    private UiCanvasColors(){}
    public static Graphics prepare(Graphics source,JComponent canvas){
        Graphics2D out=(Graphics2D)source.create();
        if(Boolean.TRUE.equals(canvas.getClientProperty(KEY)))out.setBackground(UiPalette.CANVAS);
        return out;
    }
    public static Color map(Color color,JComponent canvas,boolean exporting){
        if(exporting||!Boolean.TRUE.equals(canvas.getClientProperty(KEY)))return color;
        switch(color.getRGB()&0xFFFFFF){
            case 0x000000:return UiPalette.INK;
            case 0xE1E1E1:return UiPalette.GUIDE;
            case 0xB4B4B4:return UiPalette.EDGE;
            case 0x6E6E6E:return UiPalette.MUTED;
            case 0x1E64C8:return UiPalette.ACCENT;
            case 0x5A9BF0:return new Color(UiPalette.ACCENT.getRed(),UiPalette.ACCENT.getGreen(),UiPalette.ACCENT.getBlue(),65);
            default:return color;
        }
    }
}
