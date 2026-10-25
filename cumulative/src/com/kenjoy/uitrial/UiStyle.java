package com.kenjoy.uitrial;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.MenuBarUI;
import javax.swing.plaf.ComboBoxUI;
import javax.swing.plaf.ScrollBarUI;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.PanelUI;
import javax.swing.plaf.LabelUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicLabelUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/** Opt-in presentation only. No UIManager defaults, business classes, models or events. */
public final class UiStyle {
    private static final Font UI_FONT=modernFont();
    /** Section header: one step smaller, bold, muted - hierarchy without extra height. */
    private static final Font SECTION_FONT=UI_FONT.deriveFont(Font.BOLD,NativeDpi.font(UiMetrics.SECTION_SIZE));
    private static Font modernFont(){Font font=new Font("Microsoft YaHei UI",Font.PLAIN,NativeDpi.px(12));if(!font.getFamily(Locale.ROOT).equalsIgnoreCase("Microsoft YaHei UI"))throw new IllegalStateException("Microsoft YaHei UI is required by this trial");return font;}
    /** Match native Swing text rendering (LCD/grey AA per Windows setting) for custom-drawn text. */
    static void desktopText(Graphics2D g){g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);}
    private static void crisp(Graphics2D g){g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);}
    private static final LabelUI FLAT_LABEL=new FlatLabelUI();
    public static final Color SURFACE=UiPalette.SURFACE,PANEL=UiPalette.PANEL,
        INPUT=UiPalette.INPUT,TEXT=UiPalette.TEXT,MUTED=UiPalette.MUTED,
        EDGE=UiPalette.EDGE,ACCENT=UiPalette.ACCENT;
    private final IdentityHashMap<JComponent,Saved> saved=new IdentityHashMap<JComponent,Saved>();
    private final List<JComponent> order=new ArrayList<JComponent>();
    private static final Set<String> PLAIN=new HashSet<String>(Arrays.asList(
        "javax.swing.JPanel","javax.swing.JLabel","javax.swing.JButton","javax.swing.JToggleButton",
        "javax.swing.JCheckBox","javax.swing.JRadioButton","javax.swing.JFormattedTextField",
        "javax.swing.JTextField","javax.swing.JComboBox","javax.swing.JSeparator","javax.swing.JProgressBar"));

    public static Object field(Object app,String name)throws Exception {
        for(Class<?> type=app.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(app);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static void edt(){if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("EDT required");}
    public int count(){return saved.size();}
    public void apply(Object app)throws Exception {
        edt();
        JFrame frame=(JFrame)app.getClass().getMethod("f").invoke(app);
        menu(frame.getJMenuBar());
        JPanel host=(JPanel)field(app,"f");
        boolean firstHost=!saved.containsKey(host);
        shell(host);
        // Four stable shell children: mode chooser, separator, action row, separator.
        Component[] children=host.getComponents();
        for(int i=0;i<Math.min(4,children.length);i++)plain(children[i]);
        if(children.length>=4){
            if(children[0] instanceof JComboBox){Saved s=saved.get(children[0]);if(s!=null)((JComponent)children[0]).setPreferredSize(new Dimension(s.preferred.width,NativeDpi.px(32)));}
            if(children[1] instanceof JSeparator)children[1].setPreferredSize(new Dimension(0,0));
            if(firstHost&&children[2] instanceof JPanel){JPanel row=(JPanel)children[2];row.setLayout(new GridLayout(1,2,NativeDpi.px(5),0));row.setBorder(new EmptyBorder(0,NativeDpi.px(2),0,NativeDpi.px(2)));}
            if(children[3] instanceof JSeparator)children[3].setPreferredSize(new Dimension(0,0));
        }
        // Apply the same control treatment to every mode's ordinary sidebar controls.
        Object current=field(app,"g");
        if(current instanceof JComponent && current.getClass().getName().startsWith("com.kenjoy.kenjoycnc.processTabs.")) {
            JComponent tab=(JComponent)current;boolean note=current.getClass().getName().equals("com.kenjoy.kenjoycnc.processTabs.NoteTab");boolean firstNote=note&&!saved.containsKey(tab);shell(tab);if(firstNote)tab.setBorder(new EmptyBorder(NativeDpi.px(2),NativeDpi.px(1),NativeDpi.px(4),NativeDpi.px(1)));
            for(Component c:((Container)current).getComponents())plain(c);
            if(note){JComponent canvas=(JComponent)field(current,"notePanel");
                if(remember(canvas)){canvas.putClientProperty(UiCanvasColors.KEY,Boolean.TRUE);canvas.setBackground(UiPalette.CANVAS);}
                scrollAncestors(canvas);}
        }
        // The shell-only pass passed its position/state checks before this ordinary-control
        // extension. No device model, callback, enabled state or coordinate is touched.
        JComponent device=(JComponent)field(app,"y");shell(device);
        for(Component c:device.getComponents())plain(c);
        scrollAncestors(host);scrollAncestors(device);
        JComponent bottom=(JComponent)field(app,"G");shell(bottom);
        for(Component c:bottom.getComponents())plain(c);
        if(bottom.getParent() instanceof JComponent)shell((JComponent)bottom.getParent());
        style((JComponent)field(app,"H"));
        frame.repaint();
    }
    public void restore(){
        edt();for(int i=order.size()-1;i>=0;i--){JComponent c=order.get(i);saved.get(c).restore(c);}
        saved.clear();order.clear();
    }
    private boolean remember(JComponent c){
        if(saved.containsKey(c))return false;
        saved.put(c,new Saved(c));order.add(c);return true;
    }
    private void shell(JComponent c){if(remember(c))c.setBackground(SURFACE);}
    private void menu(JMenuBar bar){
        if(bar==null)return;
        if(remember(bar)){Saved old=saved.get(bar);bar.setUI(new SurfaceMenuBarUI((MenuBarUI)old.ui));bar.setLayout(old.layout);bar.setBackground(SURFACE);bar.setForeground(TEXT);bar.setBorder(new FixedInsetsBorder(new EmptyBorder(0,0,0,0),old.insets));}
        for(int i=0;i<bar.getMenuCount();i++){JMenu item=bar.getMenu(i);if(item!=null){style(item);menuFonts(item);Icon icon=item.getIcon();if(icon!=null)item.setIcon(new OutlineIcon(i,icon.getIconWidth(),icon.getIconHeight()));}}
    }
    private void menuFonts(JMenu menu){for(Component child:menu.getMenuComponents())if(child instanceof JMenuItem){JMenuItem item=(JMenuItem)child;if(remember(item))item.setFont(UI_FONT);if(item instanceof JMenu)menuFonts((JMenu)item);}}
    private void plain(Component component){
        if(!(component instanceof JComponent))return;
        JComponent c=(JComponent)component;
        boolean noteSection=c instanceof JPanel && c.getBorder() instanceof TitledBorder
            && c.getClass().getName().startsWith("com.kenjoy.kenjoycnc.processTabs.NoteTab$");
        if(c instanceof JScrollPane && ((JScrollPane)c).getViewport().getView()!=null && "NoteEditStatus".equals(((JScrollPane)c).getViewport().getView().getName())) {
            shell(c);c.setBorder(new TopHairlineBorder(saved.get(c).insets));shell(((JScrollPane)c).getViewport());
            JComponent view=(JComponent)((JScrollPane)c).getViewport().getView();style(view);view.setForeground(MUTED);return;
        }
        boolean noteFontLabel=c instanceof JLabel && "NoteSelectionFont".equals(c.getName());
        if(!PLAIN.contains(c.getClass().getName()) && !noteSection && !noteFontLabel){
            if(c instanceof JPanel)for(Component child:c.getComponents())plain(child);
            return;
        }
        style(c);
        // Combo editors/renderers and button internals are deliberately excluded.
        if(c.getClass()==JPanel.class || noteSection)for(Component child:c.getComponents())plain(child);
    }
    private void style(JComponent c){
        if(!remember(c))return;
        Saved old=saved.get(c);
        if(c.isEnabled())c.setForeground(TEXT);
        if(c instanceof JLabel && ("NoteSelectionCount".equals(c.getName())||"NoteSelectionFont".equals(c.getName())))c.setForeground(MUTED);
        boolean input=c instanceof JTextField || c instanceof JComboBox;
        c.setBackground(input?UiPalette.WELL:SURFACE);
        if(c instanceof JSeparator)c.setForeground(UiPalette.GUIDE);
        if(c instanceof AbstractButton){
            AbstractButton b=(AbstractButton)c;b.setContentAreaFilled(false);
            if((c instanceof JButton||c instanceof JToggleButton)&&!(c instanceof JMenuItem)&&!(c instanceof JCheckBox)&&!(c instanceof JRadioButton))b.setUI(new RoundedButtonUI((ButtonUI)old.ui));
            if(c instanceof JButton && !(c instanceof JMenuItem) && old.icon!=null){
                String t=b.getText();
                if("更新".equals(t)){b.setIcon(new OutlineIcon(7,14,14));b.setIconTextGap(5);}
                else if(t!=null&&t.startsWith("PDF")){b.setIcon(new OutlineIcon(8,14,14));b.setIconTextGap(5);}
            }
        }
        if(c instanceof JProgressBar){c.setForeground(ACCENT);c.setBorder(new ProgressBorder(old.insets,old.border));}
        if((c instanceof JCheckBox || c instanceof JRadioButton) && old.icon==null){Icon effective=UIManager.getIcon(c instanceof JRadioButton?"RadioButton.icon":"CheckBox.icon");if(effective!=null)((AbstractButton)c).setIcon(new ChoiceIcon(c instanceof JRadioButton,effective.getIconWidth(),effective.getIconHeight()));}
        if(c instanceof JComboBox){
            JComboBox box=(JComboBox)c;box.setUI(new ToneComboUI(old));box.setEditor(old.editor);box.setRenderer(new ToneRenderer(box,old.renderer));
            if(box.isEditable() && box.getEditor().getEditorComponent() instanceof JTextField)style((JComponent)box.getEditor().getEditorComponent());
        }
        if(c instanceof JLabel && ((JLabel)c).getText()!=null && ((JLabel)c).getText().startsWith("<html")) {
            // HTML owns its own styling; do not reinterpret it.
            old.restore(c);saved.remove(c);order.remove(c);return;
        }
        if(c instanceof AbstractButton || c instanceof JLabel || c instanceof javax.swing.text.JTextComponent || c instanceof JComboBox || c instanceof JProgressBar) {
            c.setFont(UI_FONT);
        }
        // Windows paints disabled labels as two offset passes (etched); on dark surfaces that reads
        // as smeared bold. BasicLabelUI shares Windows' layout maths, only disabled text differs.
        if(c instanceof JLabel)((JLabel)c).setUI(FLAT_LABEL);
        Border border=c.getBorder();
        if(border instanceof TitledBorder){
            TitledBorder t=(TitledBorder)border;
            boolean noteSection=c.getClass().getName().startsWith("com.kenjoy.kenjoycnc.processTabs.NoteTab$");
            Insets sectionInsets=noteSection?new Insets(NativeDpi.px(19),NativeDpi.px(1),NativeDpi.px(5),NativeDpi.px(1)):old.insets;
            if(c instanceof JPanel){((JPanel)c).setUI(new RoundedPanelUI((PanelUI)old.ui));c.setLayout(old.layout);c.setOpaque(false);}
            c.setBorder(new SectionTitleBorder(t.getTitle(),t.getTitleJustification(),sectionInsets,noteSection));
        }else if(c instanceof JTextField && (c.getParent() instanceof JComboBox)){
            c.setOpaque(false);
            c.setBorder(new EmptyBorder(old.insets.top,Math.max(old.insets.left,6),old.insets.bottom,old.insets.right));
        }else if(c instanceof JTextField){
            c.setBorder(new ControlBorder(new Insets(old.insets.top,Math.max(old.insets.left,6),old.insets.bottom,Math.max(old.insets.right,5))));
        }else if(c instanceof JComboBox || (c instanceof JButton && !(c instanceof JMenuItem)) || isPushToggle(c)) {
            c.setBorder(c instanceof JComboBox && old.border instanceof EmptyBorder?new ComboControlBorder(old.insets):new ControlBorder(old.insets));
        }
        // No size setters: dynamic labels/values must retain their original layout behavior.
        if(c instanceof JComboBox)((ToneComboUI)((JComboBox)c).getUI()).calibrate(c,old.minimum);
    }
    private void scrollAncestors(Component c){
        Container p=c.getParent();
        while(p!=null && !(p instanceof Window)){
            if(p instanceof JViewport || p instanceof JScrollPane || p.getClass().getName().equals("com.kenjoy.kenjoycnc.note.NoteSidebarView")){
                JComponent j=(JComponent)p;shell(j);if(c.getName()!=null&&c.getName().equals("NoteCharacterCanvas"))j.setBackground(UiPalette.CANVAS);
                if(j instanceof JScrollPane){JScrollPane pane=(JScrollPane)j;Saved old=saved.get(j);pane.setBorder(new FixedInsetsBorder(new EmptyBorder(0,0,0,0),old.insets));
                    scroll(pane.getVerticalScrollBar());scroll(pane.getHorizontalScrollBar());
                }
            }
            p=p.getParent();
        }
    }
    private void scroll(JScrollBar bar){if(bar!=null&&remember(bar)){Saved old=saved.get(bar);bar.setUI(new ToneScrollUI(old));bar.setBackground(SURFACE);}}
    private static boolean fits(JComponent c,String text,Font f){
        if(text==null||text.length()==0||c.getWidth()==0)return true;
        Insets in=c.getInsets();int width=c.getWidth()-in.left-in.right;
        if(c instanceof AbstractButton){AbstractButton b=(AbstractButton)c;Icon icon=b.getIcon();if(icon!=null)width-=icon.getIconWidth()+b.getIconTextGap();}
        return c.getFontMetrics(f).stringWidth(text)<=Math.max(0,width);
    }
    private static final class Saved {
        final Font font;final Color foreground,background;final Border border;
        final Dimension preferred,minimum,maximum;final boolean preferredSet,minimumSet,maximumSet,fontSet,foregroundSet,backgroundSet;
        final Insets insets;
        final javax.swing.plaf.ComponentUI ui;final LayoutManager layout;final ListCellRenderer renderer;final ComboBoxEditor editor;final boolean contentFilled,borderPainted,opaque;final Icon icon;final int iconTextGap;
        final Rectangle editorBounds,arrowBounds;final Dimension comboSize,arrowPreferred;
        final Saved editorState;final boolean leftToRight;final Object canvasPalette;
        Saved(JComponent c){font=c.getFont();foreground=c.getForeground();background=c.getBackground();border=c.getBorder();fontSet=c.isFontSet();foregroundSet=c.isForegroundSet();backgroundSet=c.isBackgroundSet();
            preferred=new Dimension(c.getPreferredSize());minimum=new Dimension(c.getMinimumSize());maximum=new Dimension(c.getMaximumSize());
            preferredSet=c.isPreferredSizeSet();minimumSet=c.isMinimumSizeSet();maximumSet=c.isMaximumSizeSet();insets=c.getInsets();
            canvasPalette=c.getClientProperty(UiCanvasColors.KEY);
            ui=c instanceof JMenuBar?((JMenuBar)c).getUI():c instanceof JComboBox?((JComboBox)c).getUI():c instanceof JScrollBar?((JScrollBar)c).getUI():c instanceof AbstractButton?((AbstractButton)c).getUI():c instanceof JPanel?((JPanel)c).getUI():c instanceof JLabel?((JLabel)c).getUI():null;layout=c.getLayout();renderer=c instanceof JComboBox?((JComboBox)c).getRenderer():null;editor=c instanceof JComboBox?((JComboBox)c).getEditor():null;
            comboSize=c.getSize();leftToRight=c.getComponentOrientation().isLeftToRight();editorBounds=editor==null?null:editor.getEditorComponent().getBounds();Rectangle ab=null;Dimension ap=null;if(c instanceof JComboBox)for(Component child:c.getComponents())if(child instanceof AbstractButton){ab=child.getBounds();ap=child.getPreferredSize();break;}arrowBounds=ab;arrowPreferred=ap;
            editorState=editor!=null&&editor.getEditorComponent() instanceof JComponent?new Saved((JComponent)editor.getEditorComponent()):null;
            opaque=c.isOpaque();contentFilled=c instanceof AbstractButton&&((AbstractButton)c).isContentAreaFilled();borderPainted=c instanceof AbstractButton&&((AbstractButton)c).isBorderPainted();icon=c instanceof AbstractButton?((AbstractButton)c).getIcon():null;iconTextGap=c instanceof AbstractButton?((AbstractButton)c).getIconTextGap():4;}
        void restore(JComponent c){c.putClientProperty(UiCanvasColors.KEY,canvasPalette);if(c instanceof JLabel&&ui instanceof LabelUI&&((JLabel)c).getUI()!=ui)((JLabel)c).setUI((LabelUI)ui);if(c instanceof JPanel){((JPanel)c).setUI((PanelUI)ui);c.setLayout(layout);}if(c instanceof AbstractButton)((AbstractButton)c).setUI((ButtonUI)ui);if(c instanceof JScrollBar){((JScrollBar)c).setUI((ScrollBarUI)ui);c.setLayout(layout);}if(c instanceof JMenuBar){((JMenuBar)c).setUI((MenuBarUI)ui);c.setLayout(layout);}if(c instanceof JComboBox){JComboBox box=(JComboBox)c;box.setUI((ComboBoxUI)ui);box.setEditor(editor);box.setRenderer(renderer);}
            if(c instanceof AbstractButton){AbstractButton b=(AbstractButton)c;b.setContentAreaFilled(contentFilled);b.setBorderPainted(borderPainted);b.setIcon(icon);b.setIconTextGap(iconTextGap);}
            c.setFont(fontSet?font:null);c.setForeground(foregroundSet?foreground:null);c.setBackground(backgroundSet?background:null);c.setBorder(border);
            c.setPreferredSize(preferredSet?preferred:null);c.setMinimumSize(minimumSet?minimum:null);c.setMaximumSize(maximumSet?maximum:null);c.setOpaque(opaque);if(editorState!=null)editorState.restore((JComponent)editor.getEditorComponent());c.repaint();}
    }
    private static final class TopHairlineBorder implements Border {
        final Insets insets;TopHairlineBorder(Insets i){this.insets=(Insets)i.clone();}
        public Insets getBorderInsets(Component c){return (Insets)insets.clone();}
        public boolean isBorderOpaque(){return false;}
        public void paintBorder(Component c,Graphics g,int x,int y,int w,int h){g.setColor(UiPalette.HAIRLINE);g.fillRect(x+2,y,Math.max(0,w-4),1);}
    }
    private static final class FixedInsetsBorder implements Border {
        final Border painted;final Insets insets;
        FixedInsetsBorder(Border painted,Insets insets){this.painted=painted;this.insets=(Insets)insets.clone();}
        public Insets getBorderInsets(Component c){return (Insets)insets.clone();}
        public boolean isBorderOpaque(){return false;}
        public void paintBorder(Component c,Graphics g,int x,int y,int w,int h){painted.paintBorder(c,g,x,y,w,h);}
    }
    static boolean isPrimaryButton(Component c){
        if(!(c instanceof JButton)||c instanceof JMenuItem)return false;
        String t=((JButton)c).getText();
        return t!=null&&(t.equals("更新")||t.startsWith("编辑文章")||t.equals("书写"));
    }
    private static final class ControlBorder implements Border {
        final Insets insets;
        ControlBorder(Insets i){insets=(Insets)i.clone();}
        public Insets getBorderInsets(Component c){return (Insets)insets.clone();}
        public boolean isBorderOpaque(){return false;}
        public void paintBorder(Component c,Graphics graphics,int x,int y,int w,int h){
            Graphics2D g=(Graphics2D)graphics.create();
            try {crisp(g);int arc=UiMetrics.CONTROL_ARC;
                if(c.getParent()!=null){Area corners=new Area(new Rectangle2D.Double(x,y,w,h));corners.subtract(new Area(new RoundRectangle2D.Double(x+1,y+1,w-2,h-2,arc,arc)));g.setColor(c.getParent().getBackground());g.fill(corners);}
                boolean well=c instanceof JTextField||c instanceof JComboBox;
                boolean focus=c.isEnabled()&&(c.hasFocus()||(c instanceof JComboBox&&((JComboBox)c).isEditable()&&((JComboBox)c).getEditor().getEditorComponent().hasFocus()));
                ButtonModel model=c instanceof AbstractButton?((AbstractButton)c).getModel():null;
                boolean hot=c.isEnabled()&&model!=null&&(model.isRollover()||model.isPressed()&&model.isArmed());
                boolean selected=c.isEnabled()&&model!=null&&model.isSelected();
                boolean primary=isPrimaryButton(c);
                if(well&&c.isEnabled()&&!focus){
                    g.setColor(new Color(0,0,0,52));
                    g.drawLine(x+4,y+2,Math.max(x+4,x+w-5),y+2);
                }
                Color edgeColor;
                if(!c.isEnabled())edgeColor=UiPalette.DISABLED_EDGE;
                else if(focus||selected)edgeColor=ACCENT;
                else if(well)edgeColor=UiPalette.WELL_EDGE;
                else if(primary)edgeColor=hot?ACCENT:UiPalette.PRIMARY_EDGE;
                else edgeColor=hot?EDGE:UiPalette.BUTTON_EDGE;
                g.setColor(edgeColor);
                g.setStroke(new BasicStroke(1f));
                g.draw(new RoundRectangle2D.Double(x+1.5,y+1.5,Math.max(0,w-3),Math.max(0,h-3),arc,arc));
            }finally{g.dispose();}
        }
    }
    static boolean isPushToggle(Component c){return c instanceof JToggleButton&&!(c instanceof JCheckBox)&&!(c instanceof JRadioButton);}
    static void styleDetached(JComponent c,Insets pad){
        c.putClientProperty("modernization.applicationTheme",Boolean.TRUE);
        c.setFont(UI_FONT);c.setForeground(TEXT);c.setBackground(SURFACE);
        if(c instanceof JLabel)((JLabel)c).setUI(FLAT_LABEL);
        if(c instanceof AbstractButton){
            AbstractButton b=(AbstractButton)c;b.setContentAreaFilled(false);
            b.setUI(new RoundedButtonUI(b.getUI()));
            b.setBorder(new ControlBorder(pad));
        }
    }
    /** Inspector section header: 11px bold muted below a 1px top hairline divider. */
    static final class SectionTitleBorder implements Border {
        final String title;final int justification;final Insets insets;final boolean compactX,drawLine;
        SectionTitleBorder(String title,int justification,Insets insets,boolean compactX){this(title,justification,insets,compactX,false);}
        SectionTitleBorder(String title,int justification,Insets insets,boolean compactX,boolean drawLine){this.title=title;this.justification=justification;this.insets=(Insets)insets.clone();this.compactX=compactX;this.drawLine=drawLine;}
        public Insets getBorderInsets(Component c){return (Insets)insets.clone();}
        public boolean isBorderOpaque(){return false;}
        public void paintBorder(Component c,Graphics graphics,int x,int y,int w,int h){
            if(title==null||title.isEmpty())return;
            Graphics2D g=(Graphics2D)graphics.create();
            try{
                if(drawLine){g.setColor(UiPalette.HAIRLINE);g.fillRect(x+2,y+1,Math.max(0,w-4),1);}
                desktopText(g);g.setFont(SECTION_FONT);FontMetrics fm=g.getFontMetrics();
                int baseline=y+(compactX?NativeDpi.px(14):Math.min(NativeDpi.px(15),Math.max(NativeDpi.px(11),insets.top-NativeDpi.px(3))));
                int width=fm.stringWidth(title);
                boolean ltr=c.getComponentOrientation().isLeftToRight();
                int padX=NativeDpi.px(compactX?4:UiMetrics.SECTION_TEXT_X);
                int tx=justification==TitledBorder.CENTER?x+(w-width)/2:(justification==TitledBorder.RIGHT||justification==TitledBorder.TRAILING&&ltr||justification==TitledBorder.LEADING&&!ltr)?x+w-padX-width:x+padX;
                g.setColor(c.isEnabled()?MUTED:UiPalette.DISABLED_TEXT);g.drawString(title,tx,baseline);
            }finally{g.dispose();}
        }
    }
    /** BasicLabelUI with a single-pass disabled text colour instead of the etched double draw. */
    private static final class FlatLabelUI extends BasicLabelUI {
        protected void paintEnabledText(JLabel l,Graphics graphics,String s,int textX,int textY){
            Graphics2D g=(Graphics2D)graphics.create();
            try{desktopText(g);g.setColor(l.getForeground());BasicGraphicsUtils.drawStringUnderlineCharAt(g,s,l.getDisplayedMnemonicIndex(),textX,textY);}finally{g.dispose();}
        }
        protected void paintDisabledText(JLabel l,Graphics graphics,String s,int textX,int textY){
            Graphics2D g=(Graphics2D)graphics.create();
            try{desktopText(g);g.setColor(UiPalette.DISABLED_TEXT);BasicGraphicsUtils.drawStringUnderlineCharAt(g,s,l.getDisplayedMnemonicIndex(),textX,textY);}finally{g.dispose();}
        }
    }
    // WindowsComboBoxUI uses instanceof EmptyBorder when computing height and arrow bounds.
    // Keep that type contract as well as the exact insets, rather than freezing its dimensions.
    private static final class ComboControlBorder extends EmptyBorder {
        final ControlBorder painted;ComboControlBorder(Insets insets){super(insets);painted=new ControlBorder(insets);}
        public void paintBorder(Component c,Graphics g,int x,int y,int w,int h){painted.paintBorder(c,g,x,y,w,h);}
    }
    private static final class SurfaceMenuBarUI extends MenuBarUI {
        final MenuBarUI original;SurfaceMenuBarUI(MenuBarUI ui){original=ui;}
        public void installUI(JComponent c){original.installUI(c);}public void uninstallUI(JComponent c){original.uninstallUI(c);}
        public Dimension getPreferredSize(JComponent c){return original.getPreferredSize(c);}
        public Dimension getMinimumSize(JComponent c){return original.getMinimumSize(c);}
        public Dimension getMaximumSize(JComponent c){return original.getMaximumSize(c);}
        public void paint(Graphics g,JComponent c){Window window=SwingUtilities.getWindowAncestor(c);g.setColor(window!=null&&!window.isActive()?PANEL:SURFACE);g.fillRect(0,0,c.getWidth(),c.getHeight());g.setColor(UiPalette.HAIRLINE);g.fillRect(0,c.getHeight()-1,c.getWidth(),1);}
    }
    private static final class RoundedPanelUI extends PanelUI {
        final PanelUI original;RoundedPanelUI(PanelUI ui){original=ui;}
        public void installUI(JComponent c){original.installUI(c);}public void uninstallUI(JComponent c){original.uninstallUI(c);}
        public Dimension getPreferredSize(JComponent c){return original.getPreferredSize(c);}public Dimension getMinimumSize(JComponent c){return original.getMinimumSize(c);}public Dimension getMaximumSize(JComponent c){return original.getMaximumSize(c);}
        public void paint(Graphics graphics,JComponent c){Graphics2D g=(Graphics2D)graphics.create();try{
            g.setColor(SURFACE);g.fillRect(0,0,c.getWidth(),c.getHeight());
            boolean topDeviceSection=c.getParent()!=null&&c.getParent().getComponentCount()>0&&c.getParent().getComponent(0)==c&&!c.getClass().getName().startsWith("com.kenjoy.kenjoycnc.processTabs.NoteTab$");
            if(!topDeviceSection){g.setColor(UiPalette.HAIRLINE);g.fillRect(2,1,Math.max(0,c.getWidth()-4),1);}
            original.paint(g,c);}finally{g.dispose();}}
    }
    private static final class RoundedButtonUI extends ButtonUI {
        private static final BasicButtonUI CONTENT=new BasicButtonUI(){
            protected void paintText(Graphics graphics,AbstractButton b,Rectangle rect,String text){
                Graphics2D g=(Graphics2D)graphics.create();
                try{desktopText(g);g.setColor(b.isEnabled()?b.getForeground():UiPalette.DISABLED_TEXT);BasicGraphicsUtils.drawStringUnderlineCharAt(g,text,b.getDisplayedMnemonicIndex(),rect.x,rect.y+g.getFontMetrics().getAscent());}finally{g.dispose();}
            }
        };
        final ButtonUI original;RoundedButtonUI(ButtonUI ui){original=ui;}
        public void installUI(JComponent c){original.installUI(c);}public void uninstallUI(JComponent c){original.uninstallUI(c);}
        public Dimension getPreferredSize(JComponent c){return original.getPreferredSize(c);}public Dimension getMinimumSize(JComponent c){return original.getMinimumSize(c);}public Dimension getMaximumSize(JComponent c){return original.getMaximumSize(c);}
        public void paint(Graphics graphics,JComponent c){Graphics2D g=(Graphics2D)graphics.create();try{AbstractButton b=(AbstractButton)c;ButtonModel model=b.getModel();crisp(g);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);int arc=UiMetrics.CONTROL_ARC;
            boolean primary=isPrimaryButton(c);
            Color fill=!c.isEnabled()?SURFACE:model.isPressed()&&model.isArmed()||model.isSelected()?UiPalette.SELECTION:primary?(model.isRollover()?UiPalette.PRIMARY_HOVER:UiPalette.PRIMARY):(model.isRollover()?UiPalette.HOVER:UiPalette.BUTTON);
            g.setColor(fill);g.fill(new RoundRectangle2D.Double(1,1,Math.max(0,c.getWidth()-2),Math.max(0,c.getHeight()-2),arc,arc));CONTENT.paint(g,c);}finally{g.dispose();}}
    }
    private static final class ToneRenderer implements ListCellRenderer {
        final JComboBox box;final ListCellRenderer original;final JLabel display=new JLabel();
        final IdentityHashMap<Object,Icon> modeIcons=new IdentityHashMap<Object,Icon>();
        ToneRenderer(JComboBox box,ListCellRenderer original){this.box=box;this.original=original;}
        public Component getListCellRendererComponent(JList list,Object value,int index,boolean selected,boolean focus){
            Component rendered=original.getListCellRendererComponent(list,value,index,selected,focus);
            if(!(rendered instanceof JLabel))return rendered;
            JLabel label=(JLabel)rendered;display.setText(label.getText());Icon icon=label.getIcon();
            boolean modeItem=icon!=null&&value!=null&&value.getClass().getName().startsWith("com.kenjoy.kenjoycnc.processTabs.");
            if(modeItem){Icon cached=modeIcons.get(value);if(cached==null){cached=value.getClass().getName().equals("com.kenjoy.kenjoycnc.processTabs.NoteTab")?new OutlineIcon(6,NativeDpi.px(18),NativeDpi.px(18)):new ModeIcon(icon,NativeDpi.px(18));modeIcons.put(value,cached);}icon=cached;}
            display.setIcon(icon);display.setFont(box.getFont());display.setBorder(label.getBorder());
            display.setHorizontalAlignment(label.getHorizontalAlignment());display.setVerticalAlignment(label.getVerticalAlignment());display.setHorizontalTextPosition(label.getHorizontalTextPosition());display.setVerticalTextPosition(label.getVerticalTextPosition());display.setIconTextGap(modeItem?6:label.getIconTextGap());display.setComponentOrientation(label.getComponentOrientation());
            display.setEnabled(box.isEnabled());display.setOpaque(true);display.setForeground(box.isEnabled()?TEXT:MUTED);display.setBackground(selected?UiPalette.SELECTION:UiPalette.WELL);
            Dimension pref=label.getPreferredSize();
            if(modeItem){Insets in=display.getInsets();FontMetrics fm=display.getFontMetrics(display.getFont());display.setPreferredSize(new Dimension(icon.getIconWidth()+display.getIconTextGap()+fm.stringWidth(display.getText())+in.left+in.right,Math.max(NativeDpi.px(28),fm.getHeight()+in.top+in.bottom+NativeDpi.px(4))));}
            else display.setPreferredSize(pref);
            return display;
        }
    }
    private static final class ModeIcon implements Icon {
        final BufferedImage pixels;final int size;
        ModeIcon(Icon original,int size){this.size=size;int w=Math.max(1,original.getIconWidth()),h=Math.max(1,original.getIconHeight());pixels=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);Graphics2D g=pixels.createGraphics();try{original.paintIcon(new JLabel(),g,0,0);}finally{g.dispose();}
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){int argb=pixels.getRGB(x,y),a=argb>>>24,r=(argb>>16)&255,green=(argb>>8)&255,b=argb&255;int ink=255-(r*54+green*183+b*19)/256;int alpha=a*Math.max(0,ink)/255;pixels.setRGB(x,y,(alpha<<24)|(UiPalette.TEXT.getRGB()&0xFFFFFF));}}
        public int getIconWidth(){return size;}public int getIconHeight(){return size;}
        public void paintIcon(Component c,Graphics graphics,int x,int y){Graphics2D g=(Graphics2D)graphics.create();try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(pixels,x,y,size,size,null);}finally{g.dispose();}}
    }
    private static final class ToneComboUI extends BasicComboBoxUI {
        final Saved old;int widthOffset,heightOffset;
        ToneComboUI(Saved old){this.old=old;}
        public void installUI(JComponent c){super.installUI(c);listBox.setBackground(UiPalette.WELL);listBox.setForeground(TEXT);listBox.setSelectionBackground(UiPalette.SELECTION);listBox.setSelectionForeground(TEXT);}
        void calibrate(JComponent c,Dimension target){Dimension base=super.getMinimumSize(c);widthOffset=target.width-base.width;heightOffset=target.height-base.height;}
        public Dimension getMinimumSize(JComponent c){Dimension base=new Dimension(super.getMinimumSize(c));base.width+=widthOffset;base.height+=heightOffset;return base;}
        protected JButton createArrowButton(){return new JButton(){
            {setFocusable(false);setBorderPainted(false);setContentAreaFilled(false);setOpaque(false);}
            public Dimension getPreferredSize(){return old.arrowPreferred==null?new Dimension(17,21):new Dimension(old.arrowPreferred);}
            protected void paintComponent(Graphics g){paintArrow(this,g,0,0,getWidth(),getHeight());}
        };}
        protected LayoutManager createLayoutManager(){final LayoutManager basic=super.createLayoutManager();return new LayoutManager(){
            public void addLayoutComponent(String n,Component c){basic.addLayoutComponent(n,c);}public void removeLayoutComponent(Component c){basic.removeLayoutComponent(c);}
            public Dimension preferredLayoutSize(Container p){return basic.preferredLayoutSize(p);}public Dimension minimumLayoutSize(Container p){return basic.minimumLayoutSize(p);}
            public void layoutContainer(Container p){basic.layoutContainer(p);
                boolean mirror=p.getComponentOrientation().isLeftToRight()!=old.leftToRight;
                if(old.arrowBounds!=null&&old.arrowBounds.width>0&&old.arrowBounds.height>0){Rectangle a=old.arrowBounds;int right=old.comboSize.width-a.x-a.width,bottom=old.comboSize.height-a.y-a.height;arrowButton.setBounds(mirror?right:Math.max(0,p.getWidth()-right-a.width),a.y,a.width,Math.max(0,p.getHeight()-a.y-bottom));}
                if(editor!=null&&old.editorBounds!=null&&old.editorBounds.width>0&&old.editorBounds.height>0){Rectangle e=old.editorBounds;int right=old.comboSize.width-e.x-e.width,bottom=old.comboSize.height-e.y-e.height;editor.setBounds(mirror?right:e.x,e.y,Math.max(0,p.getWidth()-e.x-right),Math.max(0,p.getHeight()-e.y-bottom));}
            }
        };}
        public void paintCurrentValueBackground(Graphics g,Rectangle bounds,boolean focus){g.setColor(UiPalette.WELL);g.fillRect(bounds.x,bounds.y,bounds.width,bounds.height);}
        public void paintCurrentValue(Graphics g,Rectangle bounds,boolean focus){
            Component value=comboBox.getRenderer().getListCellRendererComponent(listBox,comboBox.getSelectedItem(),-1,false,false);
            value.setFont(comboBox.getFont());value.setForeground(comboBox.isEnabled()?TEXT:MUTED);value.setBackground(UiPalette.WELL);
            int pad=4;int vx=comboBox.getComponentOrientation().isLeftToRight()?bounds.x+pad:bounds.x;int vw=Math.max(0,bounds.width-pad);
            currentValuePane.paintComponent(g,value,comboBox,vx,bounds.y,vw,bounds.height,value instanceof JPanel);
        }
        public void paint(Graphics g,JComponent c){g.setColor(UiPalette.WELL);g.fillRect(0,0,c.getWidth(),c.getHeight());super.paint(g,c);}
    }
    private static final class ToneScrollUI extends BasicScrollBarUI {
        final Saved old;ToneScrollUI(Saved old){this.old=old;}
        protected void configureScrollBarColors(){thumbColor=UiPalette.THUMB;trackColor=SURFACE;}
        public Dimension getPreferredSize(JComponent c){return new Dimension(old.preferred);}
        public Dimension getMinimumSize(JComponent c){return new Dimension(old.minimum);}
        public Dimension getMaximumSize(JComponent c){return new Dimension(old.maximum);}
        protected JButton createDecreaseButton(int direction){return arrow(direction);}
        protected JButton createIncreaseButton(int direction){return arrow(direction);}
        JButton arrow(final int direction){return new JButton(){
            {setBorder(new EmptyBorder(0,0,0,0));setFocusable(false);setOpaque(false);}
            public Dimension getPreferredSize(){int n=old.preferred.width<old.preferred.height?old.preferred.width:old.preferred.height;return new Dimension(n,n);}
            protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();try{g.setColor(SURFACE);g.fillRect(0,0,getWidth(),getHeight());g.setColor(UiPalette.THUMB);int x=getWidth()/2,y=getHeight()/2;if(direction==SwingConstants.NORTH||direction==SwingConstants.SOUTH){int d=direction==SwingConstants.NORTH?-1:1;g.drawLine(x-2,y-d,x,y+d);g.drawLine(x,y+d,x+2,y-d);}else{int d=direction==SwingConstants.WEST?-1:1;g.drawLine(x-d,y-2,x+d,y);g.drawLine(x+d,y,x-d,y+2);}}finally{g.dispose();}}
        };}
        protected void paintTrack(Graphics g,JComponent c,Rectangle b){g.setColor(SURFACE);g.fillRect(b.x,b.y,b.width,b.height);}
        protected void paintThumb(Graphics graphics,JComponent c,Rectangle b){if(b.isEmpty()||!c.isEnabled())return;Graphics2D g=(Graphics2D)graphics.create();try{g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(isDragging?MUTED:UiPalette.THUMB);boolean v=scrollbar.getOrientation()==JScrollBar.VERTICAL;g.fillRoundRect(b.x+(v?4:1),b.y+(v?1:4),Math.max(0,b.width-(v?8:2)),Math.max(0,b.height-(v?2:8)),6,6);}finally{g.dispose();}}
    }
    private static void paintArrow(Component c,Graphics graphics,int x,int y,int w,int h){Graphics2D g=(Graphics2D)graphics.create();try{crisp(g);ButtonModel model=((AbstractButton)c).getModel();if(c.isEnabled()&&(model.isPressed()||model.isRollover())){g.setColor(UiPalette.HOVER);int arc=UiMetrics.PILL_ARC;g.fill(new RoundRectangle2D.Double(x+2,y+3,Math.max(0,w-5),Math.max(0,h-6),arc,arc));}g.setColor(c.isEnabled()?TEXT:MUTED);g.setStroke(new BasicStroke(1.3f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));int cx=x+w/2,cy=y+h/2;g.drawLine(cx-3,cy-1,cx,cy+2);g.drawLine(cx,cy+2,cx+3,cy-1);}finally{g.dispose();}}
    private static final class ProgressBorder implements Border {
        final Insets insets;final Border original;ProgressBorder(Insets i,Border original){insets=(Insets)i.clone();this.original=original;}
        public Insets getBorderInsets(Component c){return (Insets)insets.clone();}public boolean isBorderOpaque(){return false;}
        public void paintBorder(Component c,Graphics graphics,int x,int y,int w,int h){JProgressBar bar=(JProgressBar)c;if(bar.isIndeterminate()){if(original!=null)original.paintBorder(c,graphics,x,y,w,h);return;}Graphics2D g=(Graphics2D)graphics.create();try{
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(c.getParent()==null?SURFACE:c.getParent().getBackground());g.fillRect(x,y,w,h);g.setColor(INPUT);g.fillRoundRect(x+1,y+2,Math.max(0,w-2),Math.max(0,h-4),7,7);
            int fill=(int)Math.round(Math.max(0,Math.min(1,bar.getPercentComplete()))*Math.max(0,w-2));int fillX=bar.getComponentOrientation().isLeftToRight()?x+1:x+w-1-fill;g.setColor(ACCENT);g.fillRoundRect(fillX,y+2,fill,Math.max(0,h-4),7,7);
            if(bar.isStringPainted()){String text=bar.getString();g.setFont(bar.getFont());FontMetrics fm=g.getFontMetrics();int tx=x+(w-fm.stringWidth(text))/2,ty=y+(h-fm.getHeight())/2+fm.getAscent();g.setColor(TEXT);g.drawString(text,tx,ty);g.clipRect(fillX,y+2,fill,Math.max(0,h-4));g.setColor(SURFACE);g.drawString(text,tx,ty);}
        }finally{g.dispose();}}
    }
    /** Original monochrome navigation drawings, with the exact original icon slot dimensions. */
    private static final class OutlineIcon implements Icon {
        final int kind,width,height;OutlineIcon(int kind,int width,int height){this.kind=kind;this.width=width;this.height=height;}
        public int getIconWidth(){return width;}public int getIconHeight(){return height;}
        public void paintIcon(Component c,Graphics graphics,int x,int y){Graphics2D g=(Graphics2D)graphics.create();try{double scale=Math.min(width,height)/28.0;g.translate(x+(width-24*scale)/2,y+(height-24*scale)/2);g.scale(scale,scale);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(c.isEnabled()?TEXT:MUTED);g.setStroke(new BasicStroke(1.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            if(kind==0){java.awt.geom.Path2D p=new java.awt.geom.Path2D.Double();p.moveTo(3,7);p.lineTo(9,7);p.lineTo(11,9);p.lineTo(21,9);p.lineTo(19,19);p.lineTo(3,19);p.closePath();g.draw(p);}
            else if(kind==1){for(int k=0;k<3;k++){int yy=6+k*6,xx=k==1?15:8;g.drawLine(3,yy,xx-3,yy);g.drawLine(xx+3,yy,21,yy);g.drawOval(xx-3,yy-3,6,6);}}
            else if(kind==2){g.drawRoundRect(3,4,18,16,2,2);g.drawLine(3,9,21,9);g.drawLine(10,9,10,20);}
            else if(kind==3){g.drawOval(3,3,18,18);g.drawLine(12,7,12,12);g.drawLine(12,12,16,15);}
            else if(kind==4){java.awt.geom.Path2D p=new java.awt.geom.Path2D.Double();p.moveTo(15,3);p.curveTo(19,2,22,6,20,10);p.lineTo(10,20);p.curveTo(7,23,3,19,6,16);p.lineTo(16,6);p.closePath();g.draw(p);}
            else if(kind==5){g.drawOval(3,3,18,18);java.awt.geom.Path2D p=new java.awt.geom.Path2D.Double();p.moveTo(9,9);p.curveTo(9,5,16,5,15,10);p.curveTo(15,12,12,12,12,15);g.draw(p);g.fillOval(11,17,2,2);}
            else if(kind==7){g.setColor(c.isEnabled()?ACCENT:MUTED);g.setStroke(new BasicStroke(2.2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));java.awt.geom.Path2D p=new java.awt.geom.Path2D.Double();p.moveTo(4,12.5);p.lineTo(9.5,18);p.lineTo(20,6.5);g.draw(p);}
            else if(kind==8){g.setColor(c.isEnabled()?MUTED:UiPalette.DISABLED_TEXT);java.awt.geom.Path2D p=new java.awt.geom.Path2D.Double();p.moveTo(5,3);p.lineTo(14,3);p.lineTo(19,8);p.lineTo(19,21);p.lineTo(5,21);p.closePath();g.draw(p);g.drawLine(14,3,14,8);g.drawLine(14,8,19,8);g.drawLine(8,13,16,13);g.drawLine(8,17,14,17);}
            else{g.drawRoundRect(5,2,14,20,2,2);g.drawLine(8,8,16,8);g.drawLine(8,12,16,12);g.drawLine(8,16,14,16);}
        }finally{g.dispose();}}
    }
    private static final class ChoiceIcon implements Icon {
        final boolean radio;final int width,height;ChoiceIcon(boolean radio,int width,int height){this.radio=radio;this.width=width;this.height=height;}
        public int getIconWidth(){return width;}public int getIconHeight(){return height;}
        public void paintIcon(Component c,Graphics graphics,int x,int y){Graphics2D g=(Graphics2D)graphics.create();try{g.translate(x,y);g.scale(width/13.0,height/13.0);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(c.isEnabled()?EDGE:UiPalette.DISABLED_EDGE);if(radio)g.drawOval(1,1,10,10);else g.drawRoundRect(1,1,10,10,3,3);if(((AbstractButton)c).isSelected()){g.setColor(c.isEnabled()?ACCENT:MUTED);if(radio)g.fillOval(4,4,5,5);else{g.drawLine(3,6,5,9);g.drawLine(5,9,10,3);}}}finally{g.dispose();}}
    }
}
