package com.kenjoy.uitrial;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import javax.swing.border.LineBorder;

/** Compact trial-only window chrome. Business menus and close handler remain intact. */
public final class TrialChrome {
    private static AWTEventListener pending;
    private TrialChrome(){}
    /** Capture the main JFrame before its first peer is created; do not dispose/recreate it. */
    public static void arm(){
        pending=new AWTEventListener(){public void eventDispatched(AWTEvent event){
            if(event instanceof ContainerEvent && event.getID()==ContainerEvent.COMPONENT_ADDED){
                ContainerEvent e=(ContainerEvent)event;
                if(e.getContainer() instanceof JFrame && e.getChild() instanceof JRootPane){
                    JFrame frame=(JFrame)e.getContainer();
                    if(!frame.isDisplayable()){frame.setUndecorated(true);Toolkit.getDefaultToolkit().removeAWTEventListener(this);pending=null;}
                }
            }
        }};
        Toolkit.getDefaultToolkit().addAWTEventListener(pending,AWTEvent.CONTAINER_EVENT_MASK);
    }
    public static void disarm(){if(pending!=null){Toolkit.getDefaultToolkit().removeAWTEventListener(pending);pending=null;}}
    public static void install(final JFrame frame){
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("EDT required");
        if(!frame.isUndecorated())throw new IllegalStateException("Main window was not captured before peer creation");
        if(Boolean.TRUE.equals(frame.getRootPane().getClientProperty("trial.chrome")))return;
        frame.getRootPane().setWindowDecorationStyle(JRootPane.NONE);
        frame.getRootPane().putClientProperty("trial.chrome",Boolean.TRUE);
        frame.getRootPane().putClientProperty("trial.normalBounds",new Rectangle(frame.getBounds()));
        frame.addComponentListener(new ComponentAdapter(){private void remember(){if(frame.getExtendedState()==JFrame.NORMAL)frame.getRootPane().putClientProperty("trial.normalBounds",new Rectangle(frame.getBounds()));}public void componentMoved(ComponentEvent e){remember();}public void componentResized(ComponentEvent e){remember();round(frame);}});
        frame.addWindowStateListener(e->round(frame));
        final JMenuBar bar=frame.getJMenuBar();
        int height=bar.getPreferredSize().height;
        JComponent drag=(JComponent)Box.createHorizontalGlue();bar.add(drag);
        JLabel title=new JLabel(Boolean.TRUE.equals(frame.getRootPane().getClientProperty("modernization.production"))?"奎享雕刻 · 界面预装":"奎享雕刻 · 外观试验");title.setFont(new Font("Microsoft YaHei UI",Font.PLAIN,12));title.setForeground(UiPalette.MUTED);title.setBorder(BorderFactory.createEmptyBorder(0,10,0,18));bar.add(title);
        bar.add(new CaptionButton(frame,0,height));bar.add(new CaptionButton(frame,1,height));bar.add(new CaptionButton(frame,2,height));
        MouseInputAdapter move=new MouseInputAdapter(){Point anchor;
            public void mousePressed(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e))anchor=SwingUtilities.convertPoint(e.getComponent(),e.getPoint(),frame);}
            public void mouseDragged(MouseEvent e){if(anchor==null)return;Point screen=e.getLocationOnScreen();
                if((frame.getExtendedState()&JFrame.MAXIMIZED_BOTH)!=0){double fraction=(double)(screen.x-frame.getX())/Math.max(1,frame.getWidth());int top=anchor.y;restore(frame);anchor=new Point((int)(frame.getWidth()*fraction),top);frame.setLocation(screen.x-anchor.x,screen.y-anchor.y);}
                else frame.setLocation(screen.x-anchor.x,screen.y-anchor.y);
            }
            public void mouseReleased(MouseEvent e){anchor=null;}
            public void mouseClicked(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e)&&e.getClickCount()==2)toggleMaximize(frame);}
        };
        for(JComponent c:new JComponent[]{drag,title,bar}){c.addMouseListener(move);c.addMouseMotionListener(move);}
        frame.getRootPane().setBorder(new javax.swing.border.AbstractBorder(){public Insets getBorderInsets(Component c){return new Insets(1,1,1,1);}public void paintBorder(Component c,Graphics graphics,int x,int y,int w,int h){Graphics2D g=(Graphics2D)graphics.create();try{g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(UiPalette.GUIDE);g.drawRoundRect(x,y,w-1,h-1,(frame.getExtendedState()&JFrame.MAXIMIZED_BOTH)!=0?0:20,(frame.getExtendedState()&JFrame.MAXIMIZED_BOTH)!=0?0:20);}finally{g.dispose();}}});
        frame.setGlassPane(new ResizeEdges(frame));frame.getGlassPane().setVisible(true);
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F4,InputEvent.ALT_DOWN_MASK),"trial-close");
        frame.getRootPane().getActionMap().put("trial-close",new AbstractAction(){public void actionPerformed(ActionEvent e){close(frame);}});
        frame.validate();
        round(frame);
    }
    private static void round(JFrame frame){if(frame.getGraphicsConfiguration().getDevice().isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT))frame.setShape((frame.getExtendedState()&JFrame.MAXIMIZED_BOTH)!=0?null:new java.awt.geom.RoundRectangle2D.Double(0,0,frame.getWidth(),frame.getHeight(),20,20));}
    /** Start at the current monitor's usable resolution, with a larger centered restore size. */
    public static void fitScreenOnStartup(JFrame frame){
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("EDT required");
        if(!frame.isShowing()){
            frame.addWindowListener(new WindowAdapter(){public void windowOpened(WindowEvent e){frame.removeWindowListener(this);fitScreenOnStartup(frame);}});
            return;
        }
        Rectangle work=workArea(frame);
        int width=Math.max(1,(int)Math.round(work.width*0.94)),height=Math.max(1,(int)Math.round(work.height*0.94));
        Rectangle normal=new Rectangle(work.x+(work.width-width)/2,work.y+(work.height-height)/2,width,height);
        frame.setExtendedState(JFrame.NORMAL);
        frame.setBounds(normal);
        frame.getRootPane().putClientProperty("trial.normalBounds",new Rectangle(normal));
        frame.setMaximizedBounds(work);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        round(frame);
    }
    private static Rectangle workArea(JFrame frame){
        GraphicsConfiguration gc=frame.getGraphicsConfiguration();Rectangle r=new Rectangle(gc.getBounds());
        Insets i=Toolkit.getDefaultToolkit().getScreenInsets(gc);r.x+=i.left;r.y+=i.top;r.width-=i.left+i.right;r.height-=i.top+i.bottom;return r;
    }
    public static void toggleMaximize(JFrame frame){
        if((frame.getExtendedState()&JFrame.MAXIMIZED_BOTH)!=0)restore(frame);
        else{frame.getRootPane().putClientProperty("trial.normalBounds",new Rectangle(frame.getBounds()));frame.setMaximizedBounds(workArea(frame));frame.setExtendedState(JFrame.MAXIMIZED_BOTH);}
    }
    private static void restore(JFrame frame){Object normal=frame.getRootPane().getClientProperty("trial.normalBounds");frame.setExtendedState(JFrame.NORMAL);if(normal instanceof Rectangle)frame.setBounds(new Rectangle((Rectangle)normal));}
    static void close(JFrame frame){frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING));}
    private static final Color CLOSE_HOVER=new Color(0xC42B1C),CLOSE_PRESSED=new Color(0xB22618);
    private static final class CaptionButton extends JButton {
        final JFrame frame;final int kind;
        CaptionButton(JFrame f,int kind,int height){frame=f;this.kind=kind;String name=kind==0?"最小化":kind==1?"最大化 / 还原":"关闭";setToolTipText(name);getAccessibleContext().setAccessibleName(name);setFocusable(false);setBorderPainted(false);setContentAreaFilled(false);setOpaque(false);setPreferredSize(new Dimension(42,height));setMinimumSize(new Dimension(42,height));setMaximumSize(new Dimension(42,Integer.MAX_VALUE));
            addActionListener(e->{if(kind==0)frame.setExtendedState(frame.getExtendedState()|JFrame.ICONIFIED);else if(kind==1)toggleMaximize(frame);else close(frame);});}
        protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();try{g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);boolean pressed=getModel().isPressed(),hot=getModel().isRollover()||pressed;if(hot){g.setColor(kind==2?(pressed?CLOSE_PRESSED:CLOSE_HOVER):(pressed?UiPalette.SELECTION:UiPalette.HOVER));g.fillRect(0,0,getWidth(),Math.max(0,getHeight()-1));}g.setColor(kind==2&&hot?Color.WHITE:UiPalette.TEXT);int x=getWidth()/2-5,y=getHeight()/2-5;if(kind==0)g.drawLine(x,y+5,x+10,y+5);else if(kind==1){if((frame.getExtendedState()&JFrame.MAXIMIZED_BOTH)!=0){g.drawRect(x+2,y-1,8,8);g.setColor(hot?UiPalette.HOVER:UiPalette.SURFACE);g.fillRect(x,y+2,8,8);g.setColor(UiPalette.TEXT);}g.drawRect(x,y+2,8,8);}else{g.drawLine(x,y,x+10,y+10);g.drawLine(x+10,y,x,y+10);}}finally{g.dispose();}}
    }
    private static final class ResizeEdges extends JComponent {
        final JFrame frame;int edge;Point start;Rectangle bounds;
        ResizeEdges(JFrame frame){this.frame=frame;setOpaque(false);MouseInputAdapter mouse=new MouseInputAdapter(){
            public void mouseMoved(MouseEvent e){edge=hit(e.getX(),e.getY());setCursor(Cursor.getPredefinedCursor(cursor(edge)));}
            public void mouseExited(MouseEvent e){if(start==null)setCursor(Cursor.getDefaultCursor());}
            public void mousePressed(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e)){edge=hit(e.getX(),e.getY());if(edge!=0){start=e.getLocationOnScreen();bounds=frame.getBounds();}}}
            public void mouseDragged(MouseEvent e){if(start==null)return;int dx=e.getXOnScreen()-start.x,dy=e.getYOnScreen()-start.y;Rectangle next=resized(bounds,edge,dx,dy,frame.getMinimumSize());frame.setBounds(next);}
            public void mouseReleased(MouseEvent e){start=null;}
        };addMouseListener(mouse);addMouseMotionListener(mouse);}
        int hit(int x,int y){if(!frame.isResizable()||(frame.getExtendedState()&JFrame.MAXIMIZED_BOTH)!=0)return 0;return (x<5?1:x>=getWidth()-5?2:0)|(y<5?4:y>=getHeight()-5?8:0);}
        public boolean contains(int x,int y){return start!=null||hit(x,y)!=0;}
        static int cursor(int edge){switch(edge){case 1:case 2:return Cursor.E_RESIZE_CURSOR;case 4:case 8:return Cursor.N_RESIZE_CURSOR;case 5:case 10:return Cursor.NW_RESIZE_CURSOR;case 6:case 9:return Cursor.NE_RESIZE_CURSOR;default:return Cursor.DEFAULT_CURSOR;}}
    }
    public static Rectangle resized(Rectangle old,int edge,int dx,int dy,Dimension minimum){Rectangle r=new Rectangle(old);int minW=Math.max(320,minimum.width),minH=Math.max(240,minimum.height);if((edge&1)!=0){int d=Math.min(dx,old.width-minW);r.x+=d;r.width-=d;}if((edge&2)!=0)r.width=Math.max(minW,old.width+dx);if((edge&4)!=0){int d=Math.min(dy,old.height-minH);r.y+=d;r.height-=d;}if((edge&8)!=0)r.height=Math.max(minH,old.height+dy);return r;}
}
