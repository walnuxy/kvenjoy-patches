package com.kenjoy.uitrial;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.JTextComponent;

/** Application-wide presentation. Never invokes actions or changes document/device models. */
public final class ApplicationTheme {
    private static final String MARK="modernization.applicationTheme";
    private static final String ROUNDED="modernization.roundedDialog";
    private static final Font FONT=new Font("Microsoft YaHei UI",Font.PLAIN,NativeDpi.px(12));
    private static final Font BOLD=FONT.deriveFont(Font.BOLD);
    private static final Border CELL_EDGE=new LineBorder(UiPalette.HAIRLINE),CELL_SELECTED=new LineBorder(UiPalette.ACCENT);
    private static boolean installed;
    private static JFrame main;
    private static boolean queued;
    private static final Set<Component> pending=Collections.newSetFromMap(new IdentityHashMap<Component,Boolean>());
    private static final Map<Icon,Boolean> MONO_CACHE=Collections.synchronizedMap(new WeakHashMap<Icon,Boolean>());
    private static final Map<Icon,Icon> ICON_CACHE=Collections.synchronizedMap(new WeakHashMap<Icon,Icon>());
    private ApplicationTheme(){}
    public static final class DarkLaf extends FlatDarkLaf {
        public DarkLaf(){}
        @Override public boolean getSupportsWindowDecorations(){return true;}
    }

    public static void setup(){
        if(UIManager.getLookAndFeel() instanceof FlatDarkLaf)return;
        System.setProperty("flatlaf.useNativeLibrary","false");
        Map<String,String> p=new LinkedHashMap<String,String>();
        p.put("@background","#171A1D");p.put("@foreground","#D3D6DB");
        p.put("@componentBackground","#121518");p.put("@disabledBackground","#171A1D");
        p.put("@disabledForeground","#757980");p.put("@selectionBackground","#2D3F55");
        p.put("@selectionForeground","#D3D6DB");p.put("@accentColor","#84ADDB");
        p.put("Component.focusColor","#84ADDB");p.put("Component.borderColor","#2D323B");
        p.put("Component.arc","14");p.put("Component.focusWidth","0");
        p.put("Component.innerFocusWidth","0");p.put("Component.hideMnemonics","false");
        p.put("Button.arc","14");p.put("Button.background","#252830");
        p.put("Button.borderColor","#343942");p.put("Button.hoverBackground","#343940");
        p.put("Button.default.background","#25384E");p.put("Button.default.foreground","#D3D6DB");
        p.put("Button.default.borderColor","#4C78A8");p.put("Button.minimumWidth","0");
        p.put("Button.margin","2,8,2,8");p.put("ToggleButton.background","#252830");
        p.put("TextComponent.arc","14");p.put("TextComponent.margin","2,6,2,6");
        p.put("ComboBox.background","#121518");p.put("ComboBox.buttonBackground","#121518");
        p.put("ComboBox.buttonEditableBackground","#121518");p.put("ComboBox.minimumWidth","0");
        p.put("Spinner.background","#121518");p.put("Spinner.buttonBackground","#121518");
        p.put("TabbedPane.background","#171A1D");p.put("TabbedPane.contentAreaColor","#2F3239");
        p.put("TabbedPane.underlineColor","#84ADDB");p.put("TabbedPane.selectedBackground","#23252A");
        p.put("MenuItem.selectionBackground","#2D3F55");p.put("PopupMenu.background","#23252A");
        p.put("PopupMenu.borderColor","#343942");p.put("MenuItem.background","#23252A");
        p.put("ScrollBar.track","#171A1D");p.put("ScrollBar.thumb","#525760");
        p.put("ScrollBar.thumbArc","8");p.put("ScrollBar.showButtons","false");
        p.put("Table.background","#121518");p.put("TableHeader.background","#23252A");
        p.put("Table.gridColor","#2F3239");p.put("List.background","#121518");
        p.put("Tree.background","#121518");p.put("ToolTip.background","#23252A");
        p.put("TitlePane.background","#171A1D");p.put("TitlePane.foreground","#D3D6DB");
        p.put("TitlePane.inactiveBackground","#171A1D");p.put("TitlePane.inactiveForeground","#A8ADB5");
        p.put("TitlePane.unifiedBackground","false");p.put("TitlePane.showIcon","false");
        FlatLaf.setGlobalExtraDefaults(p);
        try{UIManager.setLookAndFeel(new DarkLaf());}catch(UnsupportedLookAndFeelException e){throw new IllegalStateException(e);}
        UIManager.put("defaultFont",new javax.swing.plaf.FontUIResource(FONT));
        UIManager.put("ClassLoader",ApplicationTheme.class.getClassLoader());
        JDialog.setDefaultLookAndFeelDecorated(true);
        JFrame.setDefaultLookAndFeelDecorated(true);
        // Keep file browsing usable on the vendor's old modified Java 8 runtime.
        UIManager.put("FileChooser.noPlacesBar",Boolean.TRUE);
        listen();
    }

    public static void install(JFrame frame){
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("EDT required");
        main=frame;
        listen();
        applyWindow(frame);
    }
    private static void listen(){
        if(!installed){
            installed=true;
            Toolkit.getDefaultToolkit().addAWTEventListener(event->{
                if(event instanceof WindowEvent && event.getID()==WindowEvent.WINDOW_OPENED){
                    Window w=((WindowEvent)event).getWindow();if(ours(w))applyWindow(w);
                }else if(event instanceof ComponentEvent && event.getID()==ComponentEvent.COMPONENT_SHOWN){
                    Component c=((ComponentEvent)event).getComponent();if(c instanceof JPopupMenu&&ours(SwingUtilities.getWindowAncestor(((JPopupMenu)c).getInvoker())))applyTree(c);
                    else if(c instanceof Window&&ours((Window)c))applyWindow((Window)c);
                }else if(event instanceof ContainerEvent&&event.getID()==ContainerEvent.COMPONENT_ADDED){
                    Component c=((ContainerEvent)event).getChild();Window w=SwingUtilities.getWindowAncestor(c);
                    if(ours(w))schedule(c);
                }
            },AWTEvent.WINDOW_EVENT_MASK|AWTEvent.CONTAINER_EVENT_MASK|AWTEvent.COMPONENT_EVENT_MASK);
        }
    }
    private static boolean ours(Window w){
        if(w==null)return false;
        if(w==main||w instanceof JDialog||w.getClass().getName().startsWith("com.kenjoy."))return true;
        for(Window owner=w.getOwner();owner!=null;owner=owner.getOwner())if(owner==main||owner.getClass().getName().startsWith("com.kenjoy."))return true;
        if(w instanceof RootPaneContainer){JRootPane root=((RootPaneContainer)w).getRootPane();return Boolean.TRUE.equals(root.getClientProperty(MARK));}
        return false;
    }
    private static void schedule(Component child){
        if(!SwingUtilities.isEventDispatchThread()){EventQueue.invokeLater(()->schedule(child));return;}
        // Renderer stamps and combo delegate internals are handled by their owners.
        for(Container p=child.getParent();p!=null;p=p.getParent())if(p instanceof CellRendererPane||p instanceof JComboBox)return;
        for(Component c:pending)if(c instanceof Container&&SwingUtilities.isDescendingFrom(child,(Container)c))return;
        pending.removeIf(c->child instanceof Container&&SwingUtilities.isDescendingFrom(c,(Container)child));pending.add(child);
        if(queued)return;queued=true;
        EventQueue.invokeLater(()->{
            queued=false;Component[] changed=pending.toArray(new Component[0]);pending.clear();
            Set<Window> windows=Collections.newSetFromMap(new IdentityHashMap<Window,Boolean>());
            for(Component c:changed){
                Window w=SwingUtilities.getWindowAncestor(c);
                if(ours(w)){applyTree(c);NativeDpi.tree(c);c.revalidate();c.repaint();windows.add(w);}
            }
            for(Window w:windows)if(w instanceof JDialog)compactDialog((JDialog)w);
        });
    }
    public static void applyWindow(Window w){
        if(w instanceof JDialog){ensureDecoratedDialog((JDialog)w);NativeDpi.dialog((JDialog)w);}
        else if(w instanceof JFrame&&w!=main)ensureDecoratedFrame((JFrame)w);
        if(w instanceof RootPaneContainer){
            JRootPane root=((RootPaneContainer)w).getRootPane();root.putClientProperty(MARK,Boolean.TRUE);
            applyTree(root);
            NativeDpi.tree(root);
            if(w instanceof JDialog)compactDialog((JDialog)w);
        }
        w.setBackground(UiPalette.SURFACE);
        if(w instanceof JDialog||w instanceof JFrame&&w!=main)roundWindow(w);
        w.repaint();
    }
    private static void roundWindow(Window window){
        if(!(window instanceof RootPaneContainer)||!window.getGraphicsConfiguration().getDevice().isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT))return;
        if(window instanceof JDialog&&!((JDialog)window).isUndecorated()||window instanceof JFrame&&!((JFrame)window).isUndecorated())return;
        JRootPane root=((RootPaneContainer)window).getRootPane();
        if(!Boolean.TRUE.equals(root.getClientProperty(ROUNDED))){
            root.putClientProperty(ROUNDED,Boolean.TRUE);
            window.addComponentListener(new ComponentAdapter(){@Override public void componentResized(ComponentEvent event){shapeWindow(window);}});
        }
        shapeWindow(window);
    }
    private static void shapeWindow(Window window){
        if(window.getWidth()>0&&window.getHeight()>0)window.setShape(new RoundRectangle2D.Double(0,0,window.getWidth(),window.getHeight(),20,20));
    }
    private static void ensureDecoratedFrame(JFrame frame){
        JRootPane root=frame.getRootPane();
        if(root==null)return;
        if(!frame.isUndecorated()||root.getWindowDecorationStyle()==JRootPane.NONE){
            boolean vis=frame.isVisible(),disp=frame.isDisplayable();
            if(vis)frame.setVisible(false);
            if(disp)frame.dispose();
            frame.setUndecorated(true);
            root.setWindowDecorationStyle(JRootPane.FRAME);
            root.updateUI();
            if(vis)frame.setVisible(true);
        }
    }
    private static void ensureDecoratedDialog(JDialog d){
        JRootPane root=d.getRootPane();
        if(root==null)return;
        if(!d.isUndecorated()||root.getWindowDecorationStyle()==JRootPane.NONE){
            boolean vis=d.isVisible();
            boolean disp=d.isDisplayable();
            if(vis)d.setVisible(false);
            if(disp)d.dispose();
            d.setUndecorated(true);
            root.setWindowDecorationStyle(JRootPane.PLAIN_DIALOG);
            root.updateUI();
            if(vis)d.setVisible(true);
        }
    }
    private static void compactDialog(final JDialog d){
        Container content=d.getContentPane();
        if(content==null)return;
        Container vf=hasLayout(content,"com.kenjoy.kenjoycnc.h.a")?content:findCenterVerticalFlow(content);
        if(vf==null)return;
        JRootPane root=d.getRootPane();
        root.putClientProperty("modernization.compactSettings",Boolean.TRUE);
        if(content==vf&&content instanceof JComponent&&((JComponent)content).getBorder()==null){
            ((JComponent)content).setBorder(new EmptyBorder(6,12,8,12));
        }
        polishVerticalFlowTree(d,content);
        alignDialogFormRows(content);
        applyTree(root);
        root.invalidate();
        d.validate();
        layoutTree(root);
        Dimension rootPref=root.getPreferredSize();
        int targetW=Math.max(d.getWidth(),Math.min(rootPref.width+NativeDpi.px(16),NativeDpi.px(680)));
        int targetH=Math.max(NativeDpi.px(120),rootPref.height+NativeDpi.px(8));
        if(d.getWidth()!=targetW||d.getHeight()!=targetH){
            d.setSize(targetW,targetH);
            d.validate();
            layoutTree(root);
        }
        int maxBottom=0;
        for(Component ch:vf.getComponents())if(ch.isVisible())maxBottom=Math.max(maxBottom,ch.getY()+ch.getHeight());
        if(maxBottom>0){
            int pad=NativeDpi.px(content==vf?8:6);
            int neededVfH=maxBottom+vf.getInsets().bottom+pad;
            int delta=neededVfH-vf.getHeight();
            if(Math.abs(delta)>3){
                d.setSize(targetW,Math.max(NativeDpi.px(120),d.getHeight()+delta));
                d.validate();
                layoutTree(root);
            }
        }
    }
    private static Container findCenterVerticalFlow(Container content){
        if(!(content.getLayout() instanceof BorderLayout))return null;
        Component center=((BorderLayout)content.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        return center instanceof Container&&hasLayout((Container)center,"com.kenjoy.kenjoycnc.h.a")?(Container)center:null;
    }
    private static boolean hasLayout(Container c,String name){
        return c!=null&&c.getLayout()!=null&&c.getLayout().getClass().getName().equals(name);
    }
    private static void layoutTree(Container c){
        c.doLayout();
        for(Component x:c.getComponents())if(x instanceof Container)layoutTree((Container)x);
    }
    private static void polishVerticalFlowTree(final JDialog d,Container c){
        if(hasLayout(c,"com.kenjoy.kenjoycnc.h.a")){
            for(Component ch:c.getComponents()){
                if(ch instanceof JComponent&&!Boolean.TRUE.equals(((JComponent)ch).getClientProperty("modernization.vfWatch"))){
                    ((JComponent)ch).putClientProperty("modernization.vfWatch",Boolean.TRUE);
                    ch.addComponentListener(new ComponentAdapter(){
                        @Override public void componentShown(ComponentEvent e){compactDialog(d);}
                        @Override public void componentHidden(ComponentEvent e){compactDialog(d);}
                    });
                }
                if(ch instanceof JPanel){
                    JPanel p=(JPanel)ch;
                    if(p.getLayout() instanceof FlowLayout&&p.getComponentCount()==1&&p.getComponent(0) instanceof JPanel){
                        Component inner=p.getComponent(0);
                        p.setLayout(new BorderLayout());
                        p.add(inner,BorderLayout.CENTER);
                    }
                }
            }
        }
        for(Component ch:c.getComponents())if(ch instanceof Container)polishVerticalFlowTree(d,(Container)ch);
    }
    private static void alignDialogFormRows(Container root){
        java.util.List<JPanel> rows=new ArrayList<JPanel>();
        collectFormRows(root,rows);
        if(rows.size()<2)return;
        int maxLabelW=0,maxFieldW=0;
        for(JPanel row:rows){
            JLabel lbl=(JLabel)row.getComponent(0);
            Integer natL=(Integer)lbl.getClientProperty("modernization.naturalLabelW");
            if(natL==null){lbl.setPreferredSize(null);natL=lbl.getPreferredSize().width;lbl.putClientProperty("modernization.naturalLabelW",natL);}
            maxLabelW=Math.max(maxLabelW,natL);
            if(row.getComponentCount()==2){
                JComponent fld=(JComponent)row.getComponent(1);
                Integer natF=(Integer)fld.getClientProperty("modernization.naturalFieldW");
                if(natF==null){fld.setPreferredSize(null);natF=fld.getPreferredSize().width;fld.putClientProperty("modernization.naturalFieldW",natF);}
                maxFieldW=Math.max(maxFieldW,Math.min(NativeDpi.px(210),Math.max(NativeDpi.px(120),natF)));
            }
        }
        boolean centerRows=hasLayout(root,"com.kenjoy.kenjoycnc.h.a");
        for(JPanel row:rows){
            if(row.getLayout() instanceof FlowLayout){
                FlowLayout fl=(FlowLayout)row.getLayout();
                if(centerRows&&row.getComponentCount()==2)fl.setAlignment(FlowLayout.CENTER);
                if(fl.getHgap()<NativeDpi.px(6))fl.setHgap(NativeDpi.px(6));
                if(fl.getVgap()>NativeDpi.px(3))fl.setVgap(NativeDpi.px(3));
            }
            JLabel lbl=(JLabel)row.getComponent(0);
            lbl.setHorizontalAlignment(SwingConstants.RIGHT);
            Dimension lp=lbl.getPreferredSize();
            lbl.setPreferredSize(new Dimension(maxLabelW+NativeDpi.px(4),Math.max(NativeDpi.px(22),lp.height)));
            if(row.getComponentCount()==2&&maxFieldW>0){
                JComponent fld=(JComponent)row.getComponent(1);
                Dimension fp=fld.getPreferredSize();
                fld.setPreferredSize(new Dimension(maxFieldW,Math.max(NativeDpi.px(26),fp.height)));
            }
        }
    }
    private static void collectFormRows(Container c,java.util.List<JPanel> out){
        if(hasLayout(c,"com.kenjoy.kenjoycnc.h.a")){
            for(Component ch:c.getComponents()){
                if(ch instanceof JPanel&&ch.isVisible()){
                    JPanel row=(JPanel)ch;
                    int n=row.getComponentCount();
                    if((n==2||n==3)&&row.getComponent(0) instanceof JLabel&&isFormInput(row.getComponent(1))&&(n==2||row.getComponent(2) instanceof JLabel)){
                        out.add(row);
                        continue;
                    }
                }
                if(ch instanceof Container)collectFormRows((Container)ch,out);
            }
            return;
        }
        for(Component ch:c.getComponents())if(ch instanceof Container)collectFormRows((Container)ch,out);
    }
    private static boolean isFormInput(Component c){
        return c instanceof JTextField||c instanceof JComboBox||c instanceof JSpinner;
    }
    /** Includes custom JPanel subclasses, while keeping canvas and renderer internals intact. */
    public static void applyTree(Component c){
        if(!(c instanceof JComponent))return;
        JComponent j=(JComponent)c;
        boolean canvas=hasSuperclass(j,"com.kenjoy.kenjoycnc.h")||hasSuperclass(j,"com.jogamp.opengl.awt.GLJPanel");
        if(canvas){j.putClientProperty(UiCanvasColors.KEY,Boolean.TRUE);j.setBackground(UiPalette.CANVAS);return;}
        boolean first=!Boolean.TRUE.equals(j.getClientProperty(MARK));
        j.putClientProperty(MARK,Boolean.TRUE);
        if(first){
            // Other modes are constructed before the main frame. Their old Windows/Nimbus
            // delegates paint white even after setBackground; migrate those delegates too.
            javax.swing.plaf.ComponentUI delegate=delegate(j);if(delegate!=null){String ui=delegate.getClass().getName();if(!ui.startsWith("com.formdev.")&&!ui.startsWith("com.kenjoy.uitrial."))j.updateUI();}
            if(j instanceof JTextComponent){
                JTextComponent text=(JTextComponent)j;
                // Preserve styled documents, processing font sizes and their formatting.
                if(!(text instanceof JTextPane)&&!(text instanceof JEditorPane))text.setFont(FONT);
                text.setBackground(UiPalette.WELL);text.setForeground(UiPalette.TEXT);
                text.setCaretColor(UiPalette.TEXT);text.setSelectionColor(UiPalette.SELECTION);text.setSelectedTextColor(UiPalette.TEXT);text.setDisabledTextColor(UiPalette.DISABLED_TEXT);
                // The legacy placeholder field resets its text to BLACK/GRAY on focus.
                text.addPropertyChangeListener("foreground",e->{Color color=(Color)e.getNewValue();if(Color.BLACK.equals(color))text.setForeground(UiPalette.TEXT);else if(Color.GRAY.equals(color))text.setForeground(UiPalette.MUTED);});
                if(text instanceof JFormattedTextField)installFormattedCaretFix((JFormattedTextField)text);
            }else if(j instanceof JLabel){
                JLabel label=(JLabel)j;label.setFont(label.getFont()!=null&&label.getFont().isBold()?BOLD:FONT);label.setForeground(semantic(label.getForeground()));
                Window owner=SwingUtilities.getWindowAncestor(label);if(owner!=null&&owner.getClass().getName().equals("com.kenjoy.kenjoycnc.a")&&label.getIcon() instanceof ImageIcon)label.setIcon(cachedBrandIcon(label.getIcon()));
            }else if(j instanceof JPanel||j instanceof JViewport||j instanceof JScrollPane||j instanceof JSplitPane||j instanceof JToolBar||j instanceof JTabbedPane||j instanceof JOptionPane){j.setBackground(UiPalette.SURFACE);j.setForeground(UiPalette.TEXT);j.setFont(FONT);}
            if(j instanceof AbstractButton){
                AbstractButton b=(AbstractButton)j;b.setFont(FONT);b.setForeground(UiPalette.TEXT);
                if(b instanceof JButton&&!(b instanceof JMenuItem)&&isDialogPrimary((JButton)b)){
                    b.setBackground(UiPalette.PRIMARY);
                    b.putClientProperty("FlatLaf.style","background:#25384E;borderColor:#4C78A8;hoverBackground:#2E4661;focusedBorderColor:#84ADDB");
                }
                if(b instanceof JMenuItem&&!(b.getParent() instanceof JMenuBar)&&b.getIcon()!=null&&!(b.getIcon() instanceof MenuIcon))b.setIcon(cachedMenuIcon(b.getIcon(),16,16));
                else if(!(b instanceof JMenuItem)&&b.getIcon() instanceof ImageIcon&&monochrome((ImageIcon)b.getIcon()))b.setIcon(cachedMenuIcon(b.getIcon(),b.getIcon().getIconWidth(),b.getIcon().getIconHeight()));
            }
            if(j instanceof JComboBox){j.setForeground(UiPalette.TEXT);j.setBackground(UiPalette.WELL);j.setFont(FONT);}
            if(j instanceof JList){JList list=(JList)j;list.setBackground(UiPalette.WELL);list.setForeground(UiPalette.TEXT);list.setSelectionBackground(UiPalette.SELECTION);list.setSelectionForeground(UiPalette.TEXT);list.setFont(FONT);}
            if(j instanceof JTable){JTable t=(JTable)j;t.setBackground(UiPalette.WELL);t.setForeground(UiPalette.TEXT);t.setGridColor(UiPalette.HAIRLINE);t.setSelectionBackground(UiPalette.SELECTION);t.setSelectionForeground(UiPalette.TEXT);t.setFont(FONT);if(t.getRowHeight()<NativeDpi.px(22))t.setRowHeight(NativeDpi.px(22));}
            if(j instanceof JTree){j.setBackground(UiPalette.WELL);j.setForeground(UiPalette.TEXT);j.setFont(FONT);}
            if(j instanceof JSlider||j instanceof JSpinner){j.setBackground(UiPalette.SURFACE);j.setForeground(UiPalette.TEXT);j.setFont(FONT);}
            Border border=j.getBorder();
            if(border instanceof TitledBorder){TitledBorder t=(TitledBorder)border;j.setBorder(new UiStyle.SectionTitleBorder(t.getTitle(),t.getTitleJustification(),border.getBorderInsets(j),false,true));}
            else if(border instanceof LineBorder||border instanceof BevelBorder||border instanceof EtchedBorder)j.setBorder(new LineBorder(UiPalette.HAIRLINE));
            if(j instanceof JList||j instanceof JComboBox)j.addPropertyChangeListener(j instanceof JList?"cellRenderer":"renderer",e->{Object renderer=e.getNewValue();if(renderer!=null&&!isThemedRenderer(renderer)){if(SwingUtilities.isEventDispatchThread())applyTree(j);else EventQueue.invokeLater(()->applyTree(j));}});
        }
        if(j instanceof JList){JList list=(JList)j;ListCellRenderer r=list.getCellRenderer();if(r!=null&&!isThemedRenderer(r))list.setCellRenderer(new DarkRenderer(r));return;}
        if(j instanceof JComboBox){JComboBox combo=(JComboBox)j;ListCellRenderer r=combo.getRenderer();if(r!=null&&!isThemedRenderer(r))combo.setRenderer(new DarkRenderer(r));if(combo.isEditable())applyTree(combo.getEditor().getEditorComponent());return;}
        if(j instanceof JMenu){for(Component child:((JMenu)j).getMenuComponents())applyTree(child);applyTree(((JMenu)j).getPopupMenu());}
        for(Component child:j.getComponents())applyTree(child);
    }
    private static void installFormattedCaretFix(final JFormattedTextField ftf){
        if(Boolean.TRUE.equals(ftf.getClientProperty("modernization.caretFix")))return;
        ftf.putClientProperty("modernization.caretFix",Boolean.TRUE);
        final Point[] press={null},drag={null};
        final long[] time={0L};
        final Runnable restore=()->{
            if(press[0]==null||System.currentTimeMillis()-time[0]>800L)return;
            int len=ftf.getDocument()!=null?ftf.getDocument().getLength():0;
            int dot=Math.max(0,Math.min(len,ftf.viewToModel(press[0])));
            if(drag[0]!=null&&drag[0].distance(press[0])>2.0){
                int mark=Math.max(0,Math.min(len,ftf.viewToModel(drag[0])));
                ftf.setCaretPosition(dot);
                ftf.moveCaretPosition(mark);
            }else{
                ftf.setCaretPosition(dot);
            }
        };
        MouseAdapter mouse=new MouseAdapter(){
            @Override public void mousePressed(MouseEvent e){
                if(!SwingUtilities.isLeftMouseButton(e)||!ftf.isEnabled())return;
                if(e.getClickCount()>1){press[0]=null;drag[0]=null;return;}
                press[0]=e.getPoint();drag[0]=null;time[0]=System.currentTimeMillis();
                EventQueue.invokeLater(restore);
            }
            @Override public void mouseDragged(MouseEvent e){
                if(!SwingUtilities.isLeftMouseButton(e)||press[0]==null)return;
                drag[0]=e.getPoint();time[0]=System.currentTimeMillis();
            }
            @Override public void mouseReleased(MouseEvent e){
                if(!SwingUtilities.isLeftMouseButton(e)||press[0]==null)return;
                if(drag[0]!=null&&drag[0].distance(press[0])>2.0)drag[0]=e.getPoint();
                else drag[0]=null;
                time[0]=System.currentTimeMillis();
                EventQueue.invokeLater(restore);
            }
        };
        ftf.addMouseListener(mouse);
        ftf.addMouseMotionListener(mouse);
        ftf.addFocusListener(new FocusAdapter(){
            @Override public void focusGained(FocusEvent e){
                if(press[0]!=null&&System.currentTimeMillis()-time[0]<=800L)EventQueue.invokeLater(restore);
            }
        });
    }
    private static boolean isThemedRenderer(Object r){return r instanceof DarkRenderer||r.getClass().getName().startsWith("com.kenjoy.uitrial.");}
    private static boolean isDialogPrimary(JButton b){
        String t=b.getText();
        if(t==null)return false;
        if(t.equals("确定选择")||t.equals("保存并关闭")||t.equals("保存设置")||t.equals("开始"))return true;
        Window w=SwingUtilities.getWindowAncestor(b);
        return w instanceof JDialog&&(t.equals("应用")||t.equals("保存")||t.equals("确定"));
    }
    private static Icon cachedMenuIcon(Icon original,int w,int h){
        Icon cached=ICON_CACHE.get(original);
        if(cached instanceof MenuIcon&&cached.getIconWidth()==w&&cached.getIconHeight()==h)return cached;
        Icon created=new MenuIcon(original,w,h);ICON_CACHE.put(original,created);return created;
    }
    private static Icon cachedBrandIcon(Icon original){
        Icon cached=ICON_CACHE.get(original);
        if(cached instanceof BrandIcon)return cached;
        Icon created=new BrandIcon(original);ICON_CACHE.put(original,created);return created;
    }
    private static javax.swing.plaf.ComponentUI delegate(JComponent j){
        return j instanceof AbstractButton?((AbstractButton)j).getUI():j instanceof JComboBox?((JComboBox)j).getUI():j instanceof JSlider?((JSlider)j).getUI():j instanceof JSpinner?((JSpinner)j).getUI():j instanceof JTabbedPane?((JTabbedPane)j).getUI():j instanceof JTextComponent?((JTextComponent)j).getUI():j instanceof JList?((JList)j).getUI():j instanceof JTable?((JTable)j).getUI():j instanceof JTree?((JTree)j).getUI():j instanceof JScrollBar?((JScrollBar)j).getUI():j instanceof JScrollPane?((JScrollPane)j).getUI():j instanceof JLabel?((JLabel)j).getUI():j instanceof JPanel?((JPanel)j).getUI():null;
    }
    private static boolean hasSuperclass(Component c,String name){for(Class<?> k=c.getClass();k!=null;k=k.getSuperclass())if(k.getName().equals(name))return true;return false;}
    private static Color semantic(Color c){if(c!=null&&c.getRed()>150&&c.getGreen()<100&&c.getBlue()<100)return new Color(0xF09A94);return UiPalette.TEXT;}
    private static boolean monochrome(ImageIcon icon){
        if(icon.getIconWidth()<1||icon.getIconHeight()<1)return false;
        Boolean cached=MONO_CACHE.get(icon);if(cached!=null)return cached;
        BufferedImage b=new BufferedImage(icon.getIconWidth(),icon.getIconHeight(),BufferedImage.TYPE_INT_ARGB);Graphics2D g=b.createGraphics();try{icon.paintIcon(new JLabel(),g,0,0);}finally{g.dispose();}
        int visible=0,colored=0;for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++){int v=b.getRGB(x,y);if((v>>>24)<32)continue;visible++;int r=(v>>16)&255,gg=(v>>8)&255,bb=v&255;if(Math.max(r,Math.max(gg,bb))-Math.min(r,Math.min(gg,bb))>30)colored++;}
        boolean result=visible>0&&colored*20<visible;MONO_CACHE.put(icon,result);return result;
    }
    private static final class MenuIcon implements Icon {
        final BufferedImage pixels;final int width,height;
        MenuIcon(Icon original,int width,int height){this.width=width;this.height=height;pixels=new BufferedImage(Math.max(1,original.getIconWidth()),Math.max(1,original.getIconHeight()),BufferedImage.TYPE_INT_ARGB);Graphics2D g=pixels.createGraphics();try{original.paintIcon(new JLabel(),g,0,0);}finally{g.dispose();}for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++){int argb=pixels.getRGB(x,y);pixels.setRGB(x,y,(argb&0xFF000000)|(UiPalette.MUTED.getRGB()&0xFFFFFF));}}
        public int getIconWidth(){return width;}public int getIconHeight(){return height;}
        public void paintIcon(Component c,Graphics graphics,int x,int y){Graphics2D g=(Graphics2D)graphics.create();try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);double s=Math.min((double)width/pixels.getWidth(),(double)height/pixels.getHeight());int w=(int)Math.round(pixels.getWidth()*s),h=(int)Math.round(pixels.getHeight()*s);if(!c.isEnabled())g.setComposite(AlphaComposite.SrcOver.derive(0.5f));g.drawImage(pixels,x+(width-w)/2,y+(height-h)/2,w,h,null);}finally{g.dispose();}}
    }
    /** The About logo contains baked-in black lettering. Preserve colored artwork and alpha. */
    private static final class BrandIcon implements Icon {
        final BufferedImage pixels;
        BrandIcon(Icon original){pixels=new BufferedImage(Math.max(1,original.getIconWidth()),Math.max(1,original.getIconHeight()),BufferedImage.TYPE_INT_ARGB);Graphics2D g=pixels.createGraphics();try{original.paintIcon(new JLabel(),g,0,0);}finally{g.dispose();}for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++){int v=pixels.getRGB(x,y),r=(v>>16)&255,gg=(v>>8)&255,b=v&255;if(Math.max(r,Math.max(gg,b))<100&&Math.max(r,Math.max(gg,b))-Math.min(r,Math.min(gg,b))<25)pixels.setRGB(x,y,(v&0xFF000000)|(UiPalette.TEXT.getRGB()&0xFFFFFF));}}
        public int getIconWidth(){return pixels.getWidth();}public int getIconHeight(){return pixels.getHeight();}
        public void paintIcon(Component c,Graphics g,int x,int y){g.drawImage(pixels,x,y,null);}
    }
    private static final class DarkRenderer implements ListCellRenderer {
        final ListCellRenderer original;
        DarkRenderer(ListCellRenderer original){this.original=original;}
        public Component getListCellRendererComponent(JList list,Object value,int index,boolean selected,boolean focus){
            Component c=original.getListCellRendererComponent(list,value,index,selected,focus);
            render(c,selected?UiPalette.SELECTION:UiPalette.WELL);
            if(c instanceof JComponent&&((JComponent)c).getBorder() instanceof LineBorder)((JComponent)c).setBorder(selected?CELL_SELECTED:CELL_EDGE);
            return c;
        }
        private void render(Component c,Color bg){
            c.setForeground(UiPalette.TEXT);c.setBackground(bg);
            if(c instanceof JLabel){JLabel l=(JLabel)c;l.setFont(l.getFont()!=null&&l.getFont().isBold()?BOLD:FONT);}
            if(c instanceof JComponent)((JComponent)c).putClientProperty(UiCanvasColors.KEY,Boolean.TRUE);
            if(c instanceof Container)for(Component child:((Container)c).getComponents())render(child,bg);
        }
    }
}
