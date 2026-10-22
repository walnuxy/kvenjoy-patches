/*
 * 字体选择对话框：在原反编译逻辑上增加
 *  - 右侧“已选择字体”纵向滚动（整列滚轮可用）
 *  - 双击左侧字体加入、双击右侧条目移除
 *  - 取消“最多选择 5 个字体”的上限（与 fontcap v2 语义一致）
 * 其余字段/方法签名保持不变，供原有 s（窗口关闭）、z（字体列表）调用。
 */
package com.kenjoy.kenjoycnc.fonts;

import com.kenjoy.kenjoycnc.data.FontsDialogData;
import com.kenjoy.kenjoycnc.data.Storage;
import com.kenjoy.kenjoycnc.L;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

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
    private JTextPane i;
    private DefaultListModel j;
    private DefaultListModel k;
    private z l;
    private NetFontPanel m;
    private h n;
    private t o;
    private FontsDialogData p;
    private JPanel q;
    private List r;
    private JTextPane s;

    /* 新增：右侧整列与滚动容器，仅本类使用。 */
    private JPanel selectedPanel;
    private JScrollPane selectedScroll;
    private JScrollPane priorityScroll;
    private FontFolderFilter folderFilter;

    public r() {
        this.setIconImage(Toolkit.getDefaultToolkit().getImage(L.b("download.png")));
        this.setTitle("\u5b57\u4f53");
        this.setSize(1000, 559);
        this.setModal(true);
        this.getContentPane().setLayout(new GridLayout(1, 2, 0, 0));
        this.a = new JTabbedPane(1);
        this.a.addChangeListener(this);
        this.getContentPane().add(this.a);
        JPanel jPanel = new JPanel();
        this.getContentPane().add(jPanel);
        jPanel.setLayout(new GridLayout(0, 2, 0, 0));
        this.c = new JPanel(new BorderLayout(0, 5));
        jPanel.add(this.c);
        JPanel details = new JPanel(new BorderLayout(0, 5));
        this.c.add(details, BorderLayout.CENTER);
        this.q = new JPanel(new com.kenjoy.kenjoycnc.h.a());
        this.q.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "\u5b57\u4f53\u4f18\u5148\u7ea7", 4, 2, null, new Color(0, 0, 0)));
        this.priorityScroll = new JScrollPane(this.q);
        this.priorityScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        this.priorityScroll.getVerticalScrollBar().setUnitIncrement(18);
        details.add(this.priorityScroll, BorderLayout.CENTER);
        JPanel notes = new JPanel(new com.kenjoy.kenjoycnc.h.a());
        details.add(notes, BorderLayout.SOUTH);
        this.s = new JTextPane();
        this.s.setForeground(Color.RED);
        this.s.setText("优先级：出现概率 = 当前值 / 所有值之和。");
        this.s.setEditable(false);
        notes.add(this.s);
        this.i = new JTextPane();
        this.i.setText("双击左侧加入，右侧移除。\n可多选；完成后点“确定选择”。");
        notes.add(this.i);
        JPanel actions = new JPanel(new GridLayout(3, 1, 0, 5));
        this.c.add(actions, BorderLayout.SOUTH);
        this.d = new JButton("\u2192");
        this.d.addActionListener(this);
        actions.add(this.d);
        this.e = new JButton("\u2190");
        this.e.addActionListener(this);
        actions.add(this.e);
        this.f = new JButton("\u786e\u5b9a\u9009\u62e9");
        this.f.setEnabled(false);
        this.f.addActionListener(this);
        actions.add(this.f);

        this.selectedPanel = new JPanel(new BorderLayout(0, 0));
        jPanel.add(this.selectedPanel);
        this.h = new JLabel("\u5df2\u9009\u62e9\u5b57\u4f53");
        this.selectedPanel.add(this.h, BorderLayout.NORTH);
        this.g = new JList();
        this.j = new DefaultListModel();
        this.g.setModel(this.j);
        this.g.setCellRenderer(new com.kenjoy.kenjoycnc.l.a());
        this.selectedScroll = new JScrollPane(this.g);
        this.selectedScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        this.selectedScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        this.selectedScroll.setWheelScrollingEnabled(true);
        this.selectedScroll.getVerticalScrollBar().setUnitIncrement(18);
        this.selectedScroll.getVerticalScrollBar().setBlockIncrement(90);
        this.selectedPanel.add(this.selectedScroll, BorderLayout.CENTER);

        this.installWheelForwarder(this.selectedPanel);
        this.installWheelForwarder(this.h);

        /* 先初始化优先级控件列表：z.load() 的后台任务可能很快回调 a(DefaultListModel)。 */
        this.r = new ArrayList();
        this.l = new z(this);
        this.m = new NetFontPanel();
        this.n = new h();
        this.a.addTab("\u672c\u5730\u5b57\u4f53", this.l);
        this.a.addTab("\u4e0b\u8f7d\u5b57\u4f53", this.n);
        this.addWindowListener(new s(this));
        /* 必须等 this.l 与 this.r 初始化后再安装，监听器会访问它们。 */
        this.installDoubleClicks();
        this.folderFilter = new FontFolderFilter(this.l);
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
                if (!SwingUtilities.isLeftMouseButton(event) || event.getClickCount() != 2) {
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
        if (candidate == null) {
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
        if (index < 0 || index >= this.j.size()) {
            return;
        }
        this.j.remove(index);
        if (index < this.r.size()) {
            JFormattedTextField field = (JFormattedTextField)this.r.remove(index);
            this.q.remove(field);
            this.q.revalidate();
            this.q.repaint();
        }
        this.d();
    }

    private void b() {
        this.l.clear();
        this.m.clear();
        this.n.clear();
        this.j.clear();
        this.k = null;
        System.gc();
        System.runFinalization();
    }

    @Override
    public void setVisible(boolean bl) {
        if (bl) {
            this.c();
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
        if (this.p == null || this.p.fontsName == null) {
            return;
        }
        for (String name : this.p.fontsName) {
            int n2 = 0;
            while (n2 < defaultListModel.size()) {
                v v2 = (v)defaultListModel.getElementAt(n2);
                if (v2.f != null && v2.f.equals(name)) {
                    this.j.addElement(v2);
                    break;
                }
                ++n2;
            }
        }
        for (Object field : this.r) {
            this.q.remove((Component)field);
        }
        this.r.clear();
        if (this.p.chance == null || this.p.chance.length != this.j.size()) {
            this.p.chance = new int[this.j.size()];
        }
        for (int chance : this.p.chance) {
            this.a(chance);
        }
        this.d();
    }

    private void a(int n2) {
        JFormattedTextField jFormattedTextField = new JFormattedTextField(NumberFormat.getIntegerInstance());
        jFormattedTextField.setValue(n2);
        this.q.add(jFormattedTextField);
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
        /* 切回“本地字体”时，确定按钮仍应按已选数量决定是否可用。 */
        if (localFonts) {
            this.d();
        } else {
            this.f.setEnabled(false);
        }
    }

    private void d() {
        this.f.setEnabled(!this.j.isEmpty());
        this.g.revalidate();
        this.g.repaint();
        this.q.revalidate();
        this.q.repaint();
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
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
            this.p.fontsName = new String[selected.length];
            n3 = 0;
            while (n3 < selected.length) {
                this.p.fontsName[n3] = selected[n3].f;
                ++n3;
            }
            this.p.chance = new int[this.r.size()];
            n3 = 0;
            while (n3 < this.r.size()) {
                JFormattedTextField jFormattedTextField = (JFormattedTextField)this.r.get(n3);
                this.p.chance[n3] = L.a(jFormattedTextField, false);
                if (this.p.chance[n3] < 0) {
                    L.e("\u8bf7\u4e3a\u4f18\u5148\u7ea7\u8f93\u5165\u6b63\u6574\u6570");
                    return;
                }
                ++n3;
            }
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
