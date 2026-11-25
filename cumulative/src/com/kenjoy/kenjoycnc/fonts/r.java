/*
 * Selected font previews and weights share one scrolling area.
 * The original dialog entry points are also used by the font catalogue.
 */
package com.kenjoy.kenjoycnc.fonts;

import com.kenjoy.kenjoycnc.data.FontsDialogData;
import com.kenjoy.kenjoycnc.data.Storage;
import com.kenjoy.kenjoycnc.L;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.NumberFormatter;

public class r
extends JDialog
implements ActionListener,
ChangeListener {
    private JTabbedPane a;
    private static r b;
    private JPanel c;
    private JButton d;
    private JButton e;
    private JButton f;
    private JList g;
    private JLabel h;
    private DefaultListModel j;
    private DefaultListModel k;
    private z l;
    private NetFontPanel m;
    private h n;
    private t o;
    private FontsDialogData p;
    private JPanel q;
    private final List<JFormattedTextField> r = new ArrayList<>();

    /* 新增：右侧整列与滚动容器，仅本类使用。 */
    private JPanel selectedPanel;
    private JScrollPane selectedScroll;
    private SelectedFontView selectedView;
    private FontFolderFilter folderFilter;

    public r() {
        this.setIconImage(Toolkit.getDefaultToolkit().getImage(L.b("download.png")));
        this.setTitle("字体 · 分类与喜好");
        this.setSize(1000, 559);
        this.setModal(true);
        this.getContentPane().setLayout(new GridLayout(1, 2, 0, 0));
        this.a = new JTabbedPane(1);
        this.a.addChangeListener(this);
        this.getContentPane().add(this.a);
        this.c = new JPanel(new BorderLayout(0, 5));
        this.getContentPane().add(this.c);

        this.selectedPanel = new JPanel(new BorderLayout());
        this.c.add(this.selectedPanel, BorderLayout.CENTER);
        this.h = new JLabel("已选择字体 · 权重");
        this.h.setToolTipText("权重越大，该字体被随机选中的概率越高。");
        this.selectedPanel.add(this.h, BorderLayout.NORTH);

        this.j = new DefaultListModel();
        this.g = new JList(this.j);
        this.g.setFixedCellHeight(108);
        this.g.setCellRenderer(new com.kenjoy.kenjoycnc.l.a());
        this.q = new JPanel(new GridLayout(0, 1));
        this.selectedView = new SelectedFontView(this.g, this.q);
        this.selectedScroll = new JScrollPane(this.selectedView);
        this.selectedScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        this.selectedScroll.getVerticalScrollBar().setUnitIncrement(18);
        this.selectedScroll.getVerticalScrollBar().setBlockIncrement(90);
        this.selectedPanel.add(this.selectedScroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING));
        this.c.add(actions, BorderLayout.SOUTH);
        this.d = new JButton("→");
        this.d.setToolTipText("加入字体（也可双击左侧字体）");
        this.d.addActionListener(this);
        actions.add(this.d);
        this.e = new JButton("←");
        this.e.setToolTipText("移除字体（也可双击已选字体）");
        this.e.addActionListener(this);
        actions.add(this.e);
        this.f = new JButton("确定选择");
        this.f.setEnabled(false);
        this.f.addActionListener(this);
        actions.add(this.f);

        this.installWheelForwarder(this.selectedPanel);
        this.installWheelForwarder(this.h);

        this.l = new z(this);
        this.g.setCellRenderer(this.l.listFonts.getCellRenderer());
        this.m = new NetFontPanel();
        this.n = new h();
        this.a.addTab("\u672c\u5730\u5b57\u4f53", this.l);
        this.a.addTab("\u4e0b\u8f7d\u5b57\u4f53", this.n);
        this.addWindowListener(new s(this));
        /* 必须等 this.l 与 this.r 初始化后再安装，监听器会访问它们。 */
        this.installDoubleClicks();
        this.folderFilter = new FontFolderFilter(this.l);
    }

    private static final class SelectedFontView extends JPanel implements Scrollable {
        private final JList fonts;
        private final JPanel weights;

        SelectedFontView(JList fonts, JPanel weights) {
            super(new BorderLayout());
            this.fonts = fonts;
            this.weights = weights;
            JPanel weightColumn = new JPanel(new BorderLayout());
            weightColumn.add(weights, BorderLayout.NORTH);
            add(fonts, BorderLayout.CENTER);
            add(weightColumn, BorderLayout.EAST);
        }

        void addWeight(JFormattedTextField field) {
            JPanel row = new JPanel(new GridBagLayout());
            row.add(field);
            row.setPreferredSize(new Dimension(row.getPreferredSize().width + 12, fonts.getFixedCellHeight()));
            weights.add(row);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return 90;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /**
     * 滚轮转发：JScrollPane 自身处理列表/预览/文字区域；本转发只接管
     * 不在滚动容器内的表头与右列空白区，避免同一事件滚动两次。
     */
    private void installWheelForwarder(Component component) {
        component.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                if (event.isConsumed() || r.this.selectedScroll == null) {
                    return;
                }
                Component source = event.getComponent();
                if (source != null && SwingUtilities.isDescendingFrom(source, r.this.selectedScroll)) {
                    return;
                }
                JScrollBar bar = r.this.selectedScroll.getVerticalScrollBar();
                if (bar == null || !bar.isVisible()) {
                    return;
                }
                int max = Math.max(bar.getMinimum(), bar.getMaximum() - bar.getVisibleAmount());
                int next = bar.getValue() + event.getUnitsToScroll() * bar.getUnitIncrement();
                next = Math.max(bar.getMinimum(), Math.min(max, next));
                if (next != bar.getValue()) {
                    bar.setValue(next);
                }
                event.consume();
            }
        });
    }

    private void installDoubleClicks() {
        this.l.listFonts.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || event.getClickCount() != 2) {
                    return;
                }
                JList list = r.this.l.listFonts;
                int index = list.locationToIndex(event.getPoint());
                if (index < 0) {
                    return;
                }
                Rectangle bounds = list.getCellBounds(index, index);
                if (bounds == null || !bounds.contains(event.getPoint())) {
                    return;
                }
                list.setSelectedIndex(index);
                r.this.addSelected((v)list.getModel().getElementAt(index));
            }
        });
        this.g.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (!r.this.isLocalFonts() || !SwingUtilities.isLeftMouseButton(event) || event.getClickCount() != 2) {
                    return;
                }
                int index = r.this.g.locationToIndex(event.getPoint());
                if (index < 0) {
                    return;
                }
                Rectangle bounds = r.this.g.getCellBounds(index, index);
                if (bounds == null || !bounds.contains(event.getPoint())) {
                    return;
                }
                r.this.g.setSelectedIndex(index);
                r.this.removeAt(index);
            }
        });
    }

    private void addSelected(v candidate) {
        if (!this.isLocalFonts() || candidate == null) {
            return;
        }
        if (!this.contains(candidate)) {
            this.j.addElement(candidate);
            this.a(0);
        }
        this.d();
    }

    private boolean contains(v candidate) {
        if (candidate == null) {
            return false;
        }
        int n2 = 0;
        while (n2 < this.j.size()) {
            v existing = (v)this.j.getElementAt(n2);
            if (existing == candidate
                    || (existing.f != null && candidate.f != null && existing.f.equals(candidate.f))) {
                return true;
            }
            ++n2;
        }
        return false;
    }

    private void removeAt(int index) {
        if (!this.isLocalFonts() || index < 0 || index >= this.j.size()) {
            return;
        }
        this.j.remove(index);
        if (index < this.r.size()) {
            JFormattedTextField field = this.r.remove(index);
            this.q.remove(field.getParent());
            this.q.revalidate();
            this.q.repaint();
        }
        this.d();
    }

    private void b() {
        this.l.cancel();
        this.m.clear();
        this.n.clear();
        this.j.clear();
        this.clearPriorityFields();
        this.d();
        // Retain the local catalog and sample cache for the next opening.
    }

    @Override
    public void setVisible(boolean bl) {
        if (bl) {
            this.c();
            if (this.folderFilter != null) this.folderFilter.refresh();
        }
        super.setVisible(bl);
    }

    public final void a(FontsDialogData fontsDialogData) {
        this.p = fontsDialogData;
        if (this.k != null) {
            this.a(this.k);
        }
        this.setVisible(true);
    }

    public final void a() {
        this.a((FontsDialogData)Storage.getStore(FontsDialogData.class));
    }

    final void a(DefaultListModel defaultListModel) {
        // z reports completion both on the EDT and again on its loader thread.
        // Keep the model and its priority controls on the same Swing thread.
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> {
                // The preceding EDT completion has already restored this model.
                if (this.k != defaultListModel) this.a(defaultListModel);
            });
            return;
        }
        this.k = defaultListModel;
        if (this.folderFilter != null) this.folderFilter.setSource(defaultListModel);
        this.j.clear();
        this.clearPriorityFields();
        if (this.p == null || this.p.fontsName == null) {
            this.d();
            return;
        }
        for (int selectedIndex=0;selectedIndex<this.p.fontsName.length;selectedIndex++) {
            String name=this.p.fontsName[selectedIndex];
            for(int n=0;n<defaultListModel.size();n++) {
                v font=(v)defaultListModel.get(n);
                if(font.f!=null&&font.f.equals(name)) {
                    this.j.addElement(font);
                    this.a(this.p.chance!=null&&selectedIndex<this.p.chance.length?this.p.chance[selectedIndex]:1);
                    break;
                }
            }
        }
        this.d();
    }

    final void refreshFolders(){if(folderFilter!=null)folderFilter.refreshDirectories();}
    final void filterLocal(DefaultListModel model){if(folderFilter!=null)folderFilter.setSource(model);else l.listFonts.setModel(model);}

    final void catalogueChanged(DefaultListModel model) {
        if (this.k == null) { this.a(model); return; }
        FontsDialogData saved=this.p;
        String[] names=new String[this.j.size()];int[] weights=new int[names.length];
        Map<String, String> drafts = new HashMap<>();
        for (int index = 0; index < names.length; index++) {
            names[index] = ((v)this.j.get(index)).f;
            JFormattedTextField field = this.r.get(index);
            weights[index] = ((Number)field.getValue()).intValue();
            drafts.put(names[index], field.getText());
        }
        this.p = new FontsDialogData(names, weights);
        try {
            this.a(model);
            for (int index = 0; index < this.j.size(); index++) {
                String draft = drafts.get(((v)this.j.get(index)).f);
                this.r.get(index).setText(draft);
            }
        } finally {
            this.p = saved;
        }
    }

    private void clearPriorityFields() {
        this.q.removeAll();
        this.r.clear();
    }

    private void a(int n2) {
        NumberFormat format = NumberFormat.getIntegerInstance();
        JFormattedTextField jFormattedTextField = new JFormattedTextField(new NumberFormatter(format) {
            @Override
            public Object stringToValue(String input) throws ParseException {
                String text = input.trim();
                ParsePosition position = new ParsePosition(0);
                Number weight = format.parse(text, position);
                if (weight == null || position.getIndex() != text.length()
                        || weight.longValue() < 0 || weight.doubleValue() > Integer.MAX_VALUE) {
                    throw new ParseException("Invalid font weight", position.getIndex());
                }
                return Integer.valueOf(weight.intValue());
            }
        });
        jFormattedTextField.setFocusLostBehavior(JFormattedTextField.PERSIST);
        jFormattedTextField.setValue(n2);
        jFormattedTextField.setEnabled(this.a == null || this.a.getSelectedComponent() instanceof z);
        jFormattedTextField.setColumns(5);
        v font = (v)this.j.lastElement();
        jFormattedTextField.getAccessibleContext().setAccessibleName(font.b + "的权重");
        this.selectedView.addWeight(jFormattedTextField);
        this.r.add(jFormattedTextField);
        this.q.revalidate();
        this.q.repaint();
    }

    public static r a(t t2) {
        if (b == null) {
            b = new r();
            L.a(b);
        }
        com.kenjoy.kenjoycnc.fonts.r.b.o = t2;
        return b;
    }

    @Override
    public void stateChanged(ChangeEvent changeEvent) {
        if (changeEvent.getSource() == this.a) {
            this.c();
        }
    }

    private void c() {
        J j2 = (J)this.a.getSelectedComponent();
        j2.load();
        boolean localFonts = j2 instanceof z;
        if (this.c != null) {
            int n2 = 0;
            while (n2 < this.c.getComponentCount()) {
                this.c.getComponent(n2).setEnabled(localFonts);
                ++n2;
            }
        }
        if (this.d != null) {
            this.d.setEnabled(localFonts);
        }
        if (this.e != null) {
            this.e.setEnabled(localFonts);
        }
        this.g.setEnabled(localFonts);
        for (JFormattedTextField field : this.r) {
            field.setEnabled(localFonts);
        }
        this.d();
    }

    private boolean isLocalFonts() {
        return this.a.getSelectedComponent() instanceof z;
    }

    private void d() {
        // Model callbacks must preserve the selected tab's editing state.
        this.f.setEnabled(this.isLocalFonts() && !this.j.isEmpty());
        this.g.revalidate();
        this.g.repaint();
        this.q.revalidate();
        this.q.repaint();
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        if (!this.isLocalFonts()) {
            return;
        }
        Object source = actionEvent.getSource();
        if (source == this.d) {
            this.addSelected(this.l.getSelected());
            return;
        }
        if (source == this.e) {
            this.removeAt(this.g.getSelectedIndex());
            return;
        }
        if (source == this.f) {
            v[] selected = new v[this.j.getSize()];
            int n3 = 0;
            while (n3 < selected.length) {
                selected[n3] = (v)this.j.getElementAt(n3);
                ++n3;
            }
            if (selected.length == 0) {
                L.e("\u8bf7\u81f3\u5c11\u9009\u62e9\u4e00\u4e2a\u5b57\u4f53");
                return;
            }
            /* 原版此处限制最多 5 个；已按 fontcap v2 的语义解除。 */
            String[] names = new String[selected.length];
            int[] weights = new int[selected.length];
            for (int index = 0; index < selected.length; index++) {
                names[index] = selected[index].f;
                JFormattedTextField field = this.r.get(index);
                try {
                    weights[index] = (Integer)field.getFormatter().stringToValue(field.getText());
                } catch (ParseException error) {
                    this.g.ensureIndexIsVisible(index);
                    L.e("请输入 0 到 2147483647 之间的整数作为字体权重");
                    field.requestFocusInWindow();
                    return;
                }
            }
            this.p.fontsName = names;
            this.p.chance = weights;
            Storage.flashStore(this.p);
            if (this.o != null) {
                this.o.onFontSelected(selected, this.p.chance);
            }
            this.b();
            this.setVisible(false);
        }
    }

    static /* synthetic */ void a(r r2) {
        r2.b();
    }
}
