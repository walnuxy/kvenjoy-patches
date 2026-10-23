package com.kenjoy.uitrial;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

/** Java 8 needs native-size Swing metrics when Windows bitmap scaling is disabled. */
public final class NativeDpi {
    private static final String MARK="modernization.nativeDpi";
    private static final float SCALE=detect();
    private NativeDpi(){}
    private static float detect(){
        String simulation=System.getProperty("trial.nativeDpi");
        if(simulation!=null)return Float.parseFloat(simulation);
        if(!System.getProperty("java.specification.version","").equals("1.8")||GraphicsEnvironment.isHeadless())return 1;
        return Math.max(1,Toolkit.getDefaultToolkit().getScreenResolution()/96f);
    }
    public static float scale(){return SCALE;}
    public static int px(int value){return value==Integer.MAX_VALUE?value:Math.round(value*SCALE);}
    public static float font(float value){return value*SCALE;}
    /** Preserve the pre-upgrade processing scale while the UI uses the physical screen DPI. */
    public static int processingDpi(Toolkit toolkit){return Integer.getInteger("kvenjoy.processingDpi",toolkit.getScreenResolution());}
    public static void preferred(JComponent c,Dimension logical){c.setPreferredSize(Boolean.TRUE.equals(c.getClientProperty(MARK))?size(logical):logical);}
    public static void dialog(JDialog dialog){
        if(SCALE==1||!dialog.getClass().getName().startsWith("com.kenjoy.")||Boolean.TRUE.equals(dialog.getRootPane().getClientProperty(MARK+".window"))||dialog.getWidth()<=0||dialog.getHeight()<=0)return;
        dialog.getRootPane().putClientProperty(MARK+".window",Boolean.TRUE);
        GraphicsConfiguration gc=dialog.getGraphicsConfiguration();Rectangle work=new Rectangle(gc.getBounds());Insets i=Toolkit.getDefaultToolkit().getScreenInsets(gc);
        work.x+=i.left;work.y+=i.top;work.width-=i.left+i.right;work.height-=i.top+i.bottom;
        int width=Math.min(work.width,px(dialog.getWidth())),height=Math.min(work.height,px(dialog.getHeight()));
        int x=Math.max(work.x,Math.min(dialog.getX()+(dialog.getWidth()-width)/2,work.x+work.width-width));
        int y=Math.max(work.y,Math.min(dialog.getY()+(dialog.getHeight()-height)/2,work.y+work.height-height));
        dialog.setBounds(x,y,width,height);
    }
    public static void tree(Component component){
        if(SCALE==1||!(component instanceof JComponent))return;
        JComponent j=(JComponent)component;
        if(j.getClass().getName().startsWith("com.formdev.flatlaf."))return;
        for(Class<?> type=j.getClass();type!=null;type=type.getSuperclass())if(type.getName().equals("com.kenjoy.kenjoycnc.h")||type.getName().equals("com.jogamp.opengl.awt.GLJPanel"))return;
        if(!Boolean.TRUE.equals(j.getClientProperty(MARK))){
            j.putClientProperty(MARK,Boolean.TRUE);
            boolean caption=j.getClass().getName().endsWith("TrialChrome$CaptionButton");
            if(j.isPreferredSizeSet())j.setPreferredSize(caption?captionSize(j.getPreferredSize()):size(j.getPreferredSize()));
            if(j.isMinimumSizeSet())j.setMinimumSize(caption?captionSize(j.getMinimumSize()):size(j.getMinimumSize()));
            if(j.isMaximumSizeSet())j.setMaximumSize(caption?captionSize(j.getMaximumSize()):size(j.getMaximumSize()));
            LayoutManager layout=j.getLayout();
            if(layout==null)for(Component child:j.getComponents()){Rectangle b=child.getBounds();child.setBounds(px(b.x),px(b.y),px(b.width),px(b.height));}
            if(layout instanceof FlowLayout){FlowLayout f=(FlowLayout)layout;f.setHgap(px(f.getHgap()));f.setVgap(px(f.getVgap()));}
            if(layout instanceof GridLayout){GridLayout g=(GridLayout)layout;g.setHgap(px(g.getHgap()));g.setVgap(px(g.getVgap()));}
            if(layout instanceof BorderLayout){BorderLayout b=(BorderLayout)layout;b.setHgap(px(b.getHgap()));b.setVgap(px(b.getVgap()));}
            if(layout instanceof GridBagLayout){GridBagLayout grid=(GridBagLayout)layout;for(Component child:j.getComponents()){GridBagConstraints g=grid.getConstraints(child);g.insets=insets(g.insets);g.ipadx=px(g.ipadx);g.ipady=px(g.ipady);grid.setConstraints(child,g);}}
            Border border=j.getBorder();
            if(border instanceof EmptyBorder&&!(border instanceof javax.swing.plaf.UIResource))j.setBorder(new EmptyBorder(insets(border.getBorderInsets(j))));
            if(j instanceof AbstractButton&&!(j instanceof JMenuItem)&&!(j instanceof JCheckBox)&&!(j instanceof JRadioButton)&&!caption){
                AbstractButton b=(AbstractButton)j;b.updateUI();b.setBorder(UIManager.getBorder(j instanceof JToggleButton?"ToggleButton.border":"Button.border"));
                b.setBackground(UiStyle.isPrimaryButton(j)?UiPalette.PRIMARY:UiPalette.BUTTON);b.setContentAreaFilled(true);
                b.putClientProperty("FlatLaf.style","arc:14;focusWidth:0;innerFocusWidth:0;borderWidth:1;focusedBorderColor:#7295BB");
            }else if(j instanceof JTextField){j.updateUI();j.setBorder(UIManager.getBorder("TextField.border"));}
            else if(j instanceof JComboBox){j.updateUI();j.setBorder(UIManager.getBorder("ComboBox.border"));}
            else if(j instanceof JLabel)j.updateUI();
            if(j instanceof AbstractButton){AbstractButton b=(AbstractButton)j;b.setIcon(icon(b.getIcon()));}
            else if(j instanceof JLabel){JLabel l=(JLabel)j;l.setIcon(icon(l.getIcon()));}
        }
        if(j instanceof JMenu)for(Component child:((JMenu)j).getMenuComponents())tree(child);
        if(!(j instanceof JComboBox))for(Component child:j.getComponents())tree(child);
    }
    private static Dimension size(Dimension d){return new Dimension(px(d.width),px(d.height));}
    private static Dimension captionSize(Dimension d){return new Dimension(px(d.width),d.height);}
    private static Insets insets(Insets i){return new Insets(px(i.top),px(i.left),px(i.bottom),px(i.right));}
    private static Icon icon(Icon icon){return icon==null||icon instanceof ScaledIcon||icon.getClass().getName().startsWith("com.formdev.")||icon.getClass().getName().endsWith("UiStyle$ChoiceIcon")?icon:new ScaledIcon(icon);}
    private static final class ScaledIcon implements Icon {
        final Icon original;ScaledIcon(Icon icon){original=icon;}
        public int getIconWidth(){return px(original.getIconWidth());}public int getIconHeight(){return px(original.getIconHeight());}
        public void paintIcon(Component c,Graphics graphics,int x,int y){Graphics2D g=(Graphics2D)graphics.create();try{g.translate(x,y);g.scale(SCALE,SCALE);original.paintIcon(c,g,0,0);}finally{g.dispose();}}
    }
}
