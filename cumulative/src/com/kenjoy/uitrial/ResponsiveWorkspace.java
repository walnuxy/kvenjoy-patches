package com.kenjoy.uitrial;

import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.lang.reflect.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Responsive presentation for the existing main window. Processing DPI and document data stay untouched. */
public final class ResponsiveWorkspace {
    public static final String KEY="modernization.responsive";
    public enum View { WIDTH, PAGE, MANUAL }
    private final Object app;
    private final JFrame frame;
    private final JScrollPane viewportPane,leftPane,rightPane;
    private final JPanel workspace=new JPanel(new BorderLayout(0,0));
    private final JPanel footer=new JPanel(new BorderLayout(8,0)),zoomTools=new JPanel(new FlowLayout(FlowLayout.LEADING,4,3));
    private final JToggleButton fitPage=new JToggleButton("整页"),fitWidth=new JToggleButton("页宽");
    private final JButton actual=new JButton("1:1"),minus=new JButton("−"),plus=new JButton("+");
    private final JLabel percent=new JLabel("100%",SwingConstants.CENTER);
    private final JComponent rotations,device;
    private final javax.swing.Timer timer;
    private final Map<Object,ZoomState> states=new WeakHashMap<Object,ZoomState>();
    private CanvasHost host;
    private JComponent canvas;
    private CanvasAccess access;
    private Object currentMode;
    private MouseWheelListener[] originalWheels=new MouseWheelListener[0];
    private boolean updating,stacked;
    private Dimension extent=new Dimension();
    private Point panOrigin,panView;
    private final PropertyChangeListener canvasGeometry=e->{if(!updating)schedule();};
    private final MouseWheelListener manualWheel=this::wheel;
    private final MouseAdapter middlePan=new MouseAdapter(){
        public void mousePressed(MouseEvent e){if(SwingUtilities.isMiddleMouseButton(e)){panOrigin=e.getLocationOnScreen();panView=viewportPane.getViewport().getViewPosition();}}
        public void mouseReleased(MouseEvent e){if(SwingUtilities.isMiddleMouseButton(e)){panOrigin=null;panView=null;}}
        public void mouseDragged(MouseEvent e){
            if((e.getModifiersEx()&InputEvent.BUTTON2_DOWN_MASK)==0||panOrigin==null)return;
            Point now=e.getLocationOnScreen();
            viewportPane.getHorizontalScrollBar().setValue(panView.x+panOrigin.x-now.x);
            viewportPane.getVerticalScrollBar().setValue(panView.y+panOrigin.y-now.y);
        }
    };
    private void wheel(MouseWheelEvent e){
        if(updating||canvas==null)return;
        if(e.isControlDown()){
            state().view=View.MANUAL;timer.stop();
            for(MouseWheelListener listener:originalWheels)listener.mouseWheelMoved(e);
            if(access!=null)state().manualScale=access.scale();updateControls();
        }else{
            JScrollBar bar=e.isShiftDown()?viewportPane.getHorizontalScrollBar():viewportPane.getVerticalScrollBar();
            int direction=e.getWheelRotation()<0?-1:1;
            int distance=e.getScrollType()==MouseWheelEvent.WHEEL_BLOCK_SCROLL?e.getWheelRotation()*bar.getBlockIncrement(direction):e.getUnitsToScroll()*Math.max(16,bar.getUnitIncrement(direction));
            bar.setValue(bar.getValue()+distance);
        }
        e.consume();
    }
    private static final class ZoomState { View view=View.WIDTH;float manualScale=1; }

    public static ResponsiveWorkspace install(Object app)throws Exception{
        edt();JFrame frame=(JFrame)app.getClass().getMethod("f").invoke(app);
        Object existing=frame.getRootPane().getClientProperty(KEY);
        if(existing instanceof ResponsiveWorkspace){ResponsiveWorkspace r=(ResponsiveWorkspace)existing;r.refresh();return r;}
        ResponsiveWorkspace r=new ResponsiveWorkspace(app,frame);frame.getRootPane().putClientProperty(KEY,r);r.refresh();return r;
    }
    private ResponsiveWorkspace(Object app,JFrame frame)throws Exception{
        this.app=app;this.frame=frame;
        viewportPane=(JScrollPane)app.getClass().getMethod("b").invoke(app);
        JComponent left=(JComponent)UiStyle.field(app,"f");leftPane=ancestor(left);
        device=(JComponent)UiStyle.field(app,"y");rightPane=ancestor(device);
        rotations=(JComponent)UiStyle.field(app,"G");
        for(Component item:rotations.getComponents())if(item instanceof JButton){JButton button=(JButton)item;String label=button.getText();if(label!=null&&label.startsWith("旋转")){button.setText("加工方向 "+label.substring(2));button.setToolTipText("改变画布与输出刀路的加工方向；与显示缩放不同");}}
        timer=new javax.swing.Timer(55,e->adapt());timer.setRepeats(false);
        Container centerParent=viewportPane.getParent();centerParent.remove(viewportPane);workspace.add(viewportPane,BorderLayout.CENTER);
        footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,UiPalette.HAIRLINE),new EmptyBorder(2,6,2,6)));
        footer.setBackground(UiPalette.SURFACE);zoomTools.setOpaque(false);
        ButtonGroup group=new ButtonGroup();group.add(fitPage);group.add(fitWidth);
        fitPage.setName("ResponsiveFitPage");fitWidth.setName("ResponsiveFitWidth");actual.setName("ResponsiveActualSize");minus.setName("ResponsiveZoomOut");plus.setName("ResponsiveZoomIn");percent.setName("ResponsiveZoomPercent");
        fitWidth.setToolTipText("随工作区宽度自动缩放；页面过长时上下滚动");fitPage.setToolTipText("完整显示当前纸张；窗口变化时自动调整");actual.setToolTipText("恢复100%显示缩放，不改变加工尺寸");
        Insets pillPad=new Insets(3,10,3,10),stepPad=new Insets(3,6,3,6);
        UiStyle.styleDetached(fitPage,pillPad);UiStyle.styleDetached(fitWidth,pillPad);UiStyle.styleDetached(actual,pillPad);
        UiStyle.styleDetached(minus,stepPad);UiStyle.styleDetached(plus,stepPad);UiStyle.styleDetached(percent,new Insets(0,0,0,0));
        percent.setPreferredSize(new Dimension(48,25));minus.setPreferredSize(new Dimension(28,25));plus.setPreferredSize(new Dimension(28,25));
        for(JComponent c:new JComponent[]{fitPage,fitWidth,actual,minus,percent,plus})zoomTools.add(c);
        fitPage.addActionListener(e->setView(View.PAGE));fitWidth.addActionListener(e->setView(View.WIDTH));actual.addActionListener(e->zoomTo(1));minus.addActionListener(e->step(-1));plus.addActionListener(e->step(1));
        Container prior=rotations.getParent();prior.remove(rotations);prior.revalidate();rotations.setPreferredSize(null);
        footer.add(zoomTools,BorderLayout.WEST);footer.add(rotations,BorderLayout.EAST);workspace.add(footer,BorderLayout.SOUTH);centerParent.add(workspace,BorderLayout.CENTER);
        // Keep the device controls at their natural height, with vertical scrolling at small sizes.
        // The vendor's vertical flow layout starts a new column when the window is short.
        // A scrolling sidebar must keep one column regardless of viewport height.
        left.setLayout(new SidebarStack());
        leftPane.setViewportView(new SidebarColumn(left));
        rightPane.setViewportView(new SidebarColumn(device));
        JPanel jog=(JPanel)UiStyle.field(device,"l");if(jog.getLayout()==null)jog.setLayout(new AnchoredControls(jog));
        Component connection=device.getComponentCount()>0?device.getComponent(0):null;
        if(connection instanceof Container){
            Container conn=(Container)connection;
            for(Component row:conn.getComponents())if(row instanceof Container){
                LayoutManager lm=((Container)row).getLayout();
                if(lm instanceof FlowLayout)((FlowLayout)lm).setAlignment(FlowLayout.CENTER);
                for(Component item:((Container)row).getComponents())if(item instanceof JComboBox&&((JComboBox)item).isEditable())NativeDpi.preferred((JComponent)item,new Dimension(113,25));
            }
            Dimension naturalConn=conn.getLayout()!=null?conn.getLayout().preferredLayoutSize(conn):conn.getPreferredSize();
            conn.setPreferredSize(new Dimension(conn.getPreferredSize().width,Math.max(116,naturalConn.height)));
        }
        viewportPane.getViewport().setBackground(UiPalette.SURFACE);
        viewportPane.getViewport().addChangeListener(e->{if(updating)return;Component view=viewportPane.getViewport().getView();if(view!=host)attach();Dimension now=viewportPane.getViewport().getExtentSize();if(!now.equals(extent)){extent=new Dimension(now);schedule();}});
        frame.addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent e){schedule();}});
        frame.addWindowStateListener(e->schedule());
        frame.addWindowListener(new WindowAdapter(){public void windowClosed(WindowEvent e){timer.stop();detach();states.clear();}});
        ApplicationTheme.applyTree(workspace);frame.revalidate();
    }
    private static JScrollPane ancestor(Component c){JScrollPane pane=(JScrollPane)SwingUtilities.getAncestorOfClass(JScrollPane.class,c);if(pane==null)throw new IllegalStateException("Missing sidebar scroll pane");return pane;}
    private static void edt(){if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("EDT required");}
    private void schedule(){if(!updating)timer.restart();}
    public void refresh(){edt();restoreShell();if(((BorderLayout)workspace.getLayout()).getLayoutComponent(BorderLayout.CENTER)==viewportPane)attach();else{detach();updateControls();}schedule();}
    private void restoreShell(){
        Container content=frame.getContentPane();
        Component current=((BorderLayout)content.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        if(current!=null&&current!=workspace){
            content.remove(current);
            Component prior=((BorderLayout)workspace.getLayout()).getLayoutComponent(BorderLayout.CENTER);
            if(prior!=null)workspace.remove(prior);
            workspace.add(current,BorderLayout.CENTER);
            content.add(workspace,BorderLayout.CENTER);
            content.revalidate();
        }
    }
    private void detach(){if(canvas!=null){canvas.removePropertyChangeListener("preferredSize",canvasGeometry);canvas.removeMouseWheelListener(manualWheel);canvas.removeMouseListener(middlePan);canvas.removeMouseMotionListener(middlePan);for(MouseWheelListener listener:originalWheels)canvas.addMouseWheelListener(listener);}originalWheels=new MouseWheelListener[0];canvas=null;access=null;host=null;currentMode=null;panOrigin=null;panView=null;}
    private void attach(){
        if(updating)return;Component view=viewportPane.getViewport().getView();if((view==host&&host!=null)||(view==canvas&&access==null))return;
        updating=true;
        try{
            detach();
            if(!(view instanceof JComponent)){updateControls();return;}
            canvas=(JComponent)view;try{currentMode=UiStyle.field(app,"g");}catch(Exception e){throw new IllegalStateException(e);}access=CanvasAccess.forCanvas(canvas);
            if(access==null){updateControls();return;}
            host=new CanvasHost(canvas,viewportPane.getViewport());viewportPane.setViewportView(host);
            canvas.addPropertyChangeListener("preferredSize",canvasGeometry);originalWheels=canvas.getMouseWheelListeners();for(MouseWheelListener listener:originalWheels)canvas.removeMouseWheelListener(listener);canvas.addMouseWheelListener(manualWheel);
            canvas.addMouseListener(middlePan);canvas.addMouseMotionListener(middlePan);
            canvas.setToolTipText("滚轮滚动 · Ctrl+滚轮缩放 · 中键拖拽平移 · 底栏切换整页或页宽");
            if(state().view==View.MANUAL)access.zoom(state().manualScale);
            updateControls();
        }finally{updating=false;}
    }
    private ZoomState state(){ZoomState s=states.get(currentMode);if(s==null){s=new ZoomState();states.put(currentMode,s);}return s;}
    public void setView(View mode){edt();if(access==null)return;state().view=mode;if(mode==View.MANUAL)updateControls();else adapt();}
    public View view(){return access==null?View.MANUAL:state().view;}
    public float scale(){return access==null?1:access.scale();}
    public JComponent canvas(){return canvas;}
    public JScrollPane leftPane(){return leftPane;}public JScrollPane rightPane(){return rightPane;}public JPanel workspace(){return workspace;}
    public void zoomTo(float scale){edt();if(access==null)return;state().view=View.MANUAL;updating=true;try{access.zoom(clamp(scale,.1f,8f));state().manualScale=access.scale();if(host!=null)host.revalidate();updateControls();}finally{updating=false;}}
    private void step(int direction){if(access!=null)zoomTo(scale()*(direction>0?1.15f:1/1.15f));}
    public void adapt(){
        edt();if(updating||!frame.isDisplayable())return;updating=true;
        try{
            int width=frame.getContentPane().getWidth();
            int left=clamp(Math.round(width*.155f),NativeDpi.px(220),NativeDpi.px(330)),right=clamp(Math.round(width*.16f),NativeDpi.px(270),NativeDpi.px(350));
            if(width<NativeDpi.px(1100)){left=NativeDpi.px(220);right=NativeDpi.px(270);}
            sidebar(leftPane,left);sidebar(rightPane,right);
            // The right panel had a hard-coded 800px height, leaving empty sections.
            int natural=device.getLayout()!=null?device.getLayout().preferredLayoutSize(device).height:10;
            if(natural<=10){natural=10;for(Component c:device.getComponents())if(c.isVisible())natural+=c.getPreferredSize().height+5;}
            Dimension d=device.getPreferredSize();if(d.height!=natural||d.width!=right-4)device.setPreferredSize(new Dimension(right-4,natural));
            frame.getContentPane().doLayout();workspace.doLayout();viewportPane.doLayout();
            int toolsWidth=zoomTools.getPreferredSize().width+rotations.getPreferredSize().width+20;
            boolean needsStack=workspace.getWidth()<toolsWidth;
            if(needsStack!=stacked){
                stacked=needsStack;footer.removeAll();footer.setLayout(stacked?new GridLayout(2,1,0,1):new BorderLayout(8,0));
                footer.add(zoomTools,stacked?"":BorderLayout.WEST);footer.add(rotations,stacked?"":BorderLayout.EAST);
                workspace.doLayout();footer.doLayout();zoomTools.doLayout();rotations.doLayout();viewportPane.doLayout();
            }
            if(access!=null&&host!=null&&state().view!=View.MANUAL){
                Dimension available=viewportPane.getViewport().getExtentSize();float current=access.scale();Dimension paper=access.paper();Dimension preferred=canvas.getPreferredSize();
                if(available.width>80&&available.height>80&&paper.width>0&&paper.height>0){
                    float marginX=Math.max(60,preferred.width-paper.width*current),marginY=Math.max(60,preferred.height-paper.height*current);
                    float target=(available.width-CanvasHost.PAD*2-marginX)/paper.width;
                    if(state().view==View.PAGE)target=Math.min(target,(available.height-CanvasHost.PAD*2-marginY)/paper.height);
                    target=clamp(target,.1f,8f);
                    if(Math.abs(target-current)>.0005f){access.zoom(target);host.revalidate();}
                }
            }
            if(host!=null){viewportPane.doLayout();host.doLayout();host.repaint();}
            updateControls();
        }finally{updating=false;}
        // A newly visible vertical scrollbar changes the available width once.
        Dimension now=viewportPane.getViewport().getExtentSize();if(!now.equals(extent)){extent=new Dimension(now);schedule();}
    }
    private static void sidebar(JScrollPane pane,int width){Dimension p=pane.getPreferredSize();if(p.width!=width){pane.setPreferredSize(new Dimension(width,p.height));pane.revalidate();}}
    private void updateControls(){boolean enabled=access!=null;for(AbstractButton b:new AbstractButton[]{fitPage,fitWidth,actual,minus,plus})b.setEnabled(enabled);fitPage.setSelected(enabled&&state().view==View.PAGE);fitWidth.setSelected(enabled&&state().view==View.WIDTH);percent.setText(enabled?Math.round(access.scale()*100)+"%":"视图");}
    private static float clamp(float n,float low,float high){return Math.max(low,Math.min(high,n));}private static int clamp(int n,int low,int high){return Math.max(low,Math.min(high,n));}
    private static final class CanvasAccess {
        final JComponent canvas;final Field zoom,width,height,angle;final Method rotate;
        CanvasAccess(JComponent canvas,Class<?> base)throws ReflectiveOperationException{this.canvas=canvas;zoom=base.getDeclaredField("a");width=base.getDeclaredField("h");height=base.getDeclaredField("i");angle=base.getDeclaredField("g");for(Field f:new Field[]{zoom,width,height,angle})f.setAccessible(true);rotate=base.getDeclaredMethod("a",float.class);rotate.setAccessible(true);}
        static CanvasAccess forCanvas(JComponent canvas){for(Class<?> k=canvas.getClass();k!=null;k=k.getSuperclass())if(k.getName().equals("com.kenjoy.kenjoycnc.h"))try{return new CanvasAccess(canvas,k);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}return null;}
        float scale(){try{return zoom.getFloat(canvas);}catch(IllegalAccessException e){throw new IllegalStateException(e);}}
        Dimension paper(){try{boolean rotated=Math.abs(angle.getFloat(canvas)%180)>.01f;return new Dimension(Math.round(rotated?height.getFloat(canvas):width.getFloat(canvas)),Math.round(rotated?width.getFloat(canvas):height.getFloat(canvas)));}catch(IllegalAccessException e){throw new IllegalStateException(e);}}
        void zoom(float scale){try{zoom.setFloat(canvas,scale);rotate.invoke(canvas,angle.getFloat(canvas));}catch(ReflectiveOperationException e){throw new IllegalStateException("Unable to adjust display scale",e);}}
    }
    private static final class CanvasHost extends JPanel implements Scrollable {
        static final int PAD=18;final JComponent canvas;final JViewport viewport;
        CanvasHost(JComponent canvas,JViewport viewport){super(null);this.canvas=canvas;this.viewport=viewport;setBackground(UiPalette.SURFACE);add(canvas);}
        public Dimension getPreferredSize(){Dimension p=canvas.getPreferredSize();return new Dimension(p.width+PAD*2,p.height+PAD*2);}
        public void doLayout(){Dimension p=canvas.getPreferredSize();canvas.setBounds(Math.max(PAD,(getWidth()-p.width)/2),Math.max(PAD,(getHeight()-p.height)/2),Math.max(1,p.width),Math.max(1,p.height));}
        protected void paintChildren(Graphics g){super.paintChildren(g);Rectangle r=canvas.getBounds();g.setColor(UiPalette.HAIRLINE);g.drawRect(r.x-1,r.y-1,r.width+1,r.height+1);}
        public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}public int getScrollableUnitIncrement(Rectangle r,int o,int d){return 24;}public int getScrollableBlockIncrement(Rectangle r,int o,int d){return Math.max(24,(o==SwingConstants.VERTICAL?r.height:r.width)-48);}
        public boolean getScrollableTracksViewportWidth(){return getPreferredSize().width<=viewport.getExtentSize().width;}public boolean getScrollableTracksViewportHeight(){return getPreferredSize().height<=viewport.getExtentSize().height;}
    }
    private static final class SidebarColumn extends JPanel implements Scrollable {
        SidebarColumn(JComponent child){super(new BorderLayout());setBackground(UiPalette.SURFACE);add(child,BorderLayout.NORTH);}
        public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}public int getScrollableUnitIncrement(Rectangle r,int o,int d){return 24;}public int getScrollableBlockIncrement(Rectangle r,int o,int d){return Math.max(24,r.height-48);}public boolean getScrollableTracksViewportWidth(){return true;}public boolean getScrollableTracksViewportHeight(){return false;}
    }
    private static final class SidebarStack implements LayoutManager {
        public void addLayoutComponent(String name,Component component){}public void removeLayoutComponent(Component component){}
        public Dimension preferredLayoutSize(Container parent){return measure(parent,false);}
        public Dimension minimumLayoutSize(Container parent){return measure(parent,true);}
        private Dimension measure(Container parent,boolean minimum){Insets in=parent.getInsets();int width=0,height=in.top+in.bottom;
            for(Component child:parent.getComponents())if(child.isVisible()){Dimension d=minimum?child.getMinimumSize():child.getPreferredSize();width=Math.max(width,d.width);height+=d.height+NativeDpi.px(5);}
            return new Dimension(width+in.left+in.right+NativeDpi.px(10),height);
        }
        public void layoutContainer(Container parent){Insets in=parent.getInsets();int gap=NativeDpi.px(5),y=in.top+gap,width=Math.max(1,parent.getWidth()-in.left-in.right-gap*2);
            for(Component child:parent.getComponents())if(child.isVisible()){int height=child.getPreferredSize().height;child.setBounds(in.left+gap,y,width,height);y+=height+gap;}
        }
    }
    /** Resize existing absolute-position device controls; does not create or activate any action. */
    private static final class AnchoredControls implements LayoutManager {
        final IdentityHashMap<Component,Rectangle> bounds=new IdentityHashMap<Component,Rectangle>();
        AnchoredControls(JPanel panel){for(Component c:panel.getComponents())bounds.put(c,c.getBounds());}
        public void addLayoutComponent(String n,Component c){}public void removeLayoutComponent(Component c){bounds.remove(c);}
        public Dimension preferredLayoutSize(Container p){return new Dimension(NativeDpi.px(300),NativeDpi.px(300));}public Dimension minimumLayoutSize(Container p){return new Dimension(NativeDpi.px(250),NativeDpi.px(300));}
        public void layoutContainer(Container p){
            double sx=Math.max(.8,(p.getWidth()-NativeDpi.px(4))/(296.0*NativeDpi.scale()));
            int labelRight=0;
            for(Component c:p.getComponents())if(c instanceof JLabel){
                Rectangle r=bounds.get(c);
                if(r!=null){
                    int x=(int)Math.round(r.x*sx);
                    int w=Math.max((int)Math.round(r.width*sx),Math.max(r.width,c.getPreferredSize().width));
                    c.setBounds(x,r.y,w,r.height);
                    labelRight=Math.max(labelRight,x+w);
                }
            }
            for(Component c:p.getComponents())if(!(c instanceof JLabel)){
                Rectangle r=bounds.get(c);
                if(r!=null){
                    int x=(int)Math.round(r.x*sx);
                    int w=(int)Math.round(r.width*sx);
                    if(c instanceof JComboBox&&x<labelRight+6){
                        int rightEdge=x+w;
                        x=labelRight+6;
                        w=Math.max(60,rightEdge-x);
                    }
                    c.setBounds(x,r.y,w,r.height);
                }
            }
        }
    }
}
