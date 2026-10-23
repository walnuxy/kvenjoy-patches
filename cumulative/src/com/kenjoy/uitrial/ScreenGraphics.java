package com.kenjoy.uitrial;

import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.Map;

/** Palette adapter used exclusively by on-screen 2D canvas paint, never image/G-code exports. */
public final class ScreenGraphics extends Graphics2D {
    private final Graphics2D g;
    private ScreenGraphics(Graphics2D g){this.g=g;g.setBackground(UiPalette.CANVAS);}
    public static Graphics prepare(Graphics source,javax.swing.JComponent canvas){Graphics copy=source.create();return Boolean.TRUE.equals(canvas.getClientProperty(UiCanvasColors.KEY))?new ScreenGraphics((Graphics2D)copy):copy;}
    private static Color map(Color c){
        if(c==null)return null;
        int rgb=c.getRGB()&0xFFFFFF;
        if(rgb==0)return UiPalette.INK;
        if(rgb==0xFFFFFF)return UiPalette.CANVAS;
        if(rgb==0xFF0000)return UiPalette.PRIMARY_EDGE;
        if(rgb==0x0000FF)return UiPalette.ACCENT;
        if(c.getRed()==c.getGreen()&&c.getGreen()==c.getBlue()&&c.getRed()>150)return UiPalette.GUIDE;
        return c;
    }
    public void setColor(Color c){g.setColor(map(c));}public Color getColor(){return g.getColor();}
    public void setPaint(Paint p){g.setPaint(p instanceof Color?map((Color)p):p);}public Paint getPaint(){return g.getPaint();}
    public Graphics create(){return new ScreenGraphics((Graphics2D)g.create());}public void dispose(){g.dispose();}
    public void draw(Shape s){g.draw(s);}public void fill(Shape s){g.fill(s);}
    public void drawImage(BufferedImage i,BufferedImageOp op,int x,int y){g.drawImage(i,op,x,y);}
    public boolean drawImage(Image i,AffineTransform t,ImageObserver o){return g.drawImage(i,t,o);}
    public void drawRenderedImage(RenderedImage i,AffineTransform t){g.drawRenderedImage(i,t);}
    public void drawRenderableImage(RenderableImage i,AffineTransform t){g.drawRenderableImage(i,t);}
    public void drawString(String s,int x,int y){g.drawString(s,x,y);}public void drawString(String s,float x,float y){g.drawString(s,x,y);}
    public void drawString(AttributedCharacterIterator s,int x,int y){g.drawString(s,x,y);}public void drawString(AttributedCharacterIterator s,float x,float y){g.drawString(s,x,y);}
    public void drawGlyphVector(GlyphVector v,float x,float y){g.drawGlyphVector(v,x,y);}
    public boolean hit(Rectangle r,Shape s,boolean onStroke){return g.hit(r,s,onStroke);}
    public GraphicsConfiguration getDeviceConfiguration(){return g.getDeviceConfiguration();}
    public void setComposite(Composite c){g.setComposite(c);}public Composite getComposite(){return g.getComposite();}
    public void setStroke(Stroke s){g.setStroke(s);}public Stroke getStroke(){return g.getStroke();}
    public void setRenderingHint(RenderingHints.Key k,Object v){g.setRenderingHint(k,v);}public Object getRenderingHint(RenderingHints.Key k){return g.getRenderingHint(k);}
    public void setRenderingHints(Map<?,?> h){g.setRenderingHints(h);}public void addRenderingHints(Map<?,?> h){g.addRenderingHints(h);}public RenderingHints getRenderingHints(){return g.getRenderingHints();}
    public void translate(int x,int y){g.translate(x,y);}public void translate(double x,double y){g.translate(x,y);}
    public void rotate(double a){g.rotate(a);}public void rotate(double a,double x,double y){g.rotate(a,x,y);}
    public void scale(double x,double y){g.scale(x,y);}public void shear(double x,double y){g.shear(x,y);}
    public void transform(AffineTransform t){g.transform(t);}public void setTransform(AffineTransform t){g.setTransform(t);}public AffineTransform getTransform(){return g.getTransform();}
    public void setBackground(Color c){g.setBackground(map(c));}public Color getBackground(){return g.getBackground();}
    public void clip(Shape s){g.clip(s);}public FontRenderContext getFontRenderContext(){return g.getFontRenderContext();}
    public void setPaintMode(){g.setPaintMode();}public void setXORMode(Color c){g.setXORMode(c);}
    public Font getFont(){return g.getFont();}public void setFont(Font f){g.setFont(f);}public FontMetrics getFontMetrics(Font f){return g.getFontMetrics(f);}
    public Rectangle getClipBounds(){return g.getClipBounds();}public void clipRect(int x,int y,int w,int h){g.clipRect(x,y,w,h);}
    public void setClip(int x,int y,int w,int h){g.setClip(x,y,w,h);}public Shape getClip(){return g.getClip();}public void setClip(Shape s){g.setClip(s);}
    public void copyArea(int x,int y,int w,int h,int dx,int dy){g.copyArea(x,y,w,h,dx,dy);}
    public void drawLine(int x,int y,int x2,int y2){g.drawLine(x,y,x2,y2);}public void fillRect(int x,int y,int w,int h){g.fillRect(x,y,w,h);}public void clearRect(int x,int y,int w,int h){g.clearRect(x,y,w,h);}
    public void drawRoundRect(int x,int y,int w,int h,int aw,int ah){g.drawRoundRect(x,y,w,h,aw,ah);}public void fillRoundRect(int x,int y,int w,int h,int aw,int ah){g.fillRoundRect(x,y,w,h,aw,ah);}
    public void drawOval(int x,int y,int w,int h){g.drawOval(x,y,w,h);}public void fillOval(int x,int y,int w,int h){g.fillOval(x,y,w,h);}
    public void drawArc(int x,int y,int w,int h,int start,int arc){g.drawArc(x,y,w,h,start,arc);}public void fillArc(int x,int y,int w,int h,int start,int arc){g.fillArc(x,y,w,h,start,arc);}
    public void drawPolyline(int[] x,int[] y,int n){g.drawPolyline(x,y,n);}public void drawPolygon(int[] x,int[] y,int n){g.drawPolygon(x,y,n);}public void fillPolygon(int[] x,int[] y,int n){g.fillPolygon(x,y,n);}
    public boolean drawImage(Image i,int x,int y,ImageObserver o){return g.drawImage(i,x,y,o);}public boolean drawImage(Image i,int x,int y,int w,int h,ImageObserver o){return g.drawImage(i,x,y,w,h,o);}
    public boolean drawImage(Image i,int x,int y,Color b,ImageObserver o){return g.drawImage(i,x,y,b,o);}public boolean drawImage(Image i,int x,int y,int w,int h,Color b,ImageObserver o){return g.drawImage(i,x,y,w,h,b,o);}
    public boolean drawImage(Image i,int dx,int dy,int dx2,int dy2,int sx,int sy,int sx2,int sy2,ImageObserver o){return g.drawImage(i,dx,dy,dx2,dy2,sx,sy,sx2,sy2,o);}
    public boolean drawImage(Image i,int dx,int dy,int dx2,int dy2,int sx,int sy,int sx2,int sy2,Color b,ImageObserver o){return g.drawImage(i,dx,dy,dx2,dy2,sx,sy,sx2,sy2,b,o);}
}
