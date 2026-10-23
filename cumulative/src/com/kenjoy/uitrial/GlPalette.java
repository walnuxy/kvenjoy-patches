package com.kenjoy.uitrial;
import java.awt.Color;
import javax.swing.JComponent;

/** Upload a screen-color copy without changing the source path/color arrays or exported data. */
public final class GlPalette {
    private GlPalette(){}
    public static Color background(Color original,JComponent canvas){return Boolean.TRUE.equals(canvas.getClientProperty(UiCanvasColors.KEY))?UiPalette.CANVAS:original;}
    public static byte[] colors(byte[] original,JComponent canvas){
        if(!Boolean.TRUE.equals(canvas.getClientProperty(UiCanvasColors.KEY)))return original;
        byte[] copy=original.clone();
        for(int i=0;i+2<copy.length;i+=3){int r=copy[i]&255,g=copy[i+1]&255,b=copy[i+2]&255;
            if(r==g&&g==b&&r<=50){copy[i]=(byte)UiPalette.INK.getRed();copy[i+1]=(byte)UiPalette.INK.getGreen();copy[i+2]=(byte)UiPalette.INK.getBlue();}
        }
        return copy;
    }
}
