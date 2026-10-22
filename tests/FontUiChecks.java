/*
 * Swing 应用内检查：不注入操作系统鼠标/键盘，只向 Swing 组件派发事件。
 * 覆盖：右侧滚动容器、整列滚轮、双击加/删、单击只选中、箭头、优先级、
 *       6 个字体仍可“确定选择”（5 个上限已解除）、重复加入不增加。
 */
package com.kenjoy.kenjoycnc.fonts;

import com.kenjoy.kenjoycnc.data.FontsDialogData;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class FontUiChecks {
    private static final List<String> results = new ArrayList<String>();
    private static int failures;
    private static Path evidencePath;

    public static void main(String[] args) throws Exception {
        Path evidence = Paths.get(args.length > 0 ? args[0] : "font-ui/evidence/font-ui-checks.txt");
        evidencePath = evidence;
        System.setProperty("java.awt.headless", "false");
        Files.createDirectories(evidence.getParent());
        try {
            run();
        } catch (Throwable error) {
            failures++;
            results.add("FAIL 未捕获异常: " + error);
            error.printStackTrace();
        }
        results.add("SUMMARY " + (failures == 0 ? "PASS" : "FAIL") + " failures=" + failures);
        StringBuilder text = new StringBuilder();
        for (String line : results) {
            text.append(line).append('\n');
        }
        Files.createDirectories(evidence.getParent());
        Files.write(evidence, text.toString().getBytes(StandardCharsets.UTF_8));
        System.out.print(text.toString());
        System.out.flush();
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void run() throws Exception {
        for (int i = 1; i <= 3; i++) {
            String name = "com.kenjoy.kenjoycnc.fonts.r$" + i;
            try {
                Class.forName(name);
                check("匿名内部类可加载 " + name, true);
            } catch (Throwable error) {
                failures++;
                results.add("FAIL 匿名内部类可加载 " + name + ": " + error);
            }
        }
        final r[] holder = new r[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                }
                holder[0] = new r();
            }
        });
        final r dialog = holder[0];

        /* 停掉真实的字体目录扫描，换成固定 40 个模拟字体，保证检查可重复。 */
        final z local = (z)get(dialog, "l");
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                set(local, "canceled", Boolean.TRUE);
                set(local, "loading", Boolean.TRUE);
                set(local, "g", Boolean.TRUE);
            }
        });
        Thread.sleep(600L);
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                DefaultListModel left = new DefaultListModel();
                for (int i = 0; i < 40; i++) {
                    left.addElement(mock(i));
                }
                local.fontListModel = left;
                local.listFonts.setModel(left);
                dialog.setModal(false);
                dialog.setLocation(80, 80);
                dialog.setVisible(true);
                dialog.validate();
            }
        });

        final DefaultListModel right = (DefaultListModel)get(dialog, "j");
        final JList rightList = (JList)get(dialog, "g");
        final JList leftList = local.listFonts;
        final List priorityFields = (List)get(dialog, "r");
        final JPanel priorityPanel = (JPanel)get(dialog, "q");
        final JButton add = (JButton)get(dialog, "d");
        final JButton remove = (JButton)get(dialog, "e");
        final JButton confirm = (JButton)get(dialog, "f");
        final JScrollPane scroll = (JScrollPane)get(dialog, "selectedScroll");
        final JPanel selectedPanel = (JPanel)get(dialog, "selectedPanel");
        final JLabel header = (JLabel)get(dialog, "h");

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                check("右侧列表在 JScrollPane 内", SwingUtilities.isDescendingFrom(rightList, scroll));
                check("纵向滚动条按需出现", scroll.getVerticalScrollBarPolicy() == JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
                check("滚轮滚动启用", scroll.isWheelScrollingEnabled());
                check("滚轮步长=18", scroll.getVerticalScrollBar().getUnitIncrement() == 18);
                check("初始右侧为空", right.size() == 0);
                check("初始确定按钮禁用", !confirm.isEnabled());

                click(leftList, 0, 1);
                check("单击左侧只选中，不加入", right.size() == 0 && leftList.getSelectedIndex() == 0);

                click(leftList, 0, 2);
                check("双击左侧加入 1 个", right.size() == 1);
                check("双击加入后优先级框=1", priorityFields.size() == 1);
                check("双击加入后确定按钮可用", confirm.isEnabled());

                click(leftList, 0, 2);
                check("重复双击同一字体不重复加入", right.size() == 1);
                check("重复双击后优先级框仍=1", priorityFields.size() == 1);

                click(leftList, 1, 1);
                check("单击左侧第 2 项只选中", right.size() == 1 && leftList.getSelectedIndex() == 1);

                add.doClick();
                check("→ 箭头仍可加入", right.size() == 2 && priorityFields.size() == 2);

                rightList.setSelectedIndex(0);
                remove.doClick();
                check("← 箭头仍可移除", right.size() == 1 && priorityFields.size() == 1);

                click(rightList, 0, 1);
                check("单击右侧只选中，不移除", right.size() == 1);

                click(rightList, 0, 2);
                check("双击右侧移除", right.size() == 0 && priorityFields.size() == 0);
                check("清空后确定按钮禁用", !confirm.isEnabled());

                rawClick(rightList, new Point(5, 5), 2);
                check("空列表双击不抛异常", right.size() == 0);
            }
        });

        /* 滚轮：先加 40 项让视口溢出。 */
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 40; i++) {
                    click(leftList, i, 2);
                }
                dialog.validate();
                rightList.validate();
                check("40 项后优先级框=40", priorityFields.size() == 40);
                JScrollBar bar = scroll.getVerticalScrollBar();
                check("内容超视口出现纵向滚动条", bar.isVisible());
                check("可滚动范围 > 0", bar.getMaximum() - bar.getVisibleAmount() > 0);

                BufferedImage image = new BufferedImage(Math.max(1, rightList.getWidth()),
                        Math.max(1, rightList.getHeight()), BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics();
                rightList.paint(graphics);
                graphics.dispose();

                Rectangle first = rightList.getCellBounds(0, 0);
                Component deep = SwingUtilities.getDeepestComponentAt(rightList, 10,
                        first == null ? 10 : first.y + Math.max(1, first.height / 2));
                results.add("INFO 滚轮目标(预览/文字)=" + (deep == null ? "null" : deep.getClass().getName()));

                bar.setValue(0);
                wheel(header, 1);
                int headerValue = bar.getValue();
                check("鼠标在表头/空白区滚轮生效", headerValue > 0);
                check("表头滚轮只滚动一次(54px)", headerValue == 54);

                bar.setValue(0);
                wheel(deep == null ? rightList : deep, 1);
                int deepValue = bar.getValue();
                check("鼠标在预览/文字区域滚轮生效", deepValue > 0);
                check("预览/文字滚轮未重复滚动", deepValue == 54);

                bar.setValue(0);
                wheel(selectedPanel, 1);
                check("鼠标在右列空白面板滚轮生效", bar.getValue() == 54);

                bar.setValue(200);
                wheel(header, -1);
                check("滚轮可向上浏览", bar.getValue() < 200);
                bar.setValue(0);

                try {
                    BufferedImage shot = new BufferedImage(Math.max(1, dialog.getWidth()),
                            Math.max(1, dialog.getHeight()), BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics2 = shot.createGraphics();
                    dialog.paint(graphics2);
                    graphics2.dispose();
                    Path png = evidencePath.resolveSibling("font-dialog.png");
                    ImageIO.write(shot, "png", png.toFile());
                    check("导出 Swing 绘制截图", Files.isRegularFile(png));
                } catch (Throwable error) {
                    failures++;
                    results.add("FAIL 导出 Swing 绘制截图: " + error);
                }
            }
        });

        /* 6 个字体确认：验证 5 个上限已解除，且优先级数组长度正确。 */
        final int[] callbackCount = new int[1];
        final String[] callbackNames = new String[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                right.clear();
                for (Object field : new ArrayList(priorityFields)) {
                    priorityPanel.remove((Component)field);
                }
                priorityFields.clear();
                priorityPanel.revalidate();
                priorityPanel.repaint();
                for (int i = 0; i < 6; i++) {
                    click(leftList, i, 2);
                }
                check("可加入 6 个字体", right.size() == 6);
                check("6 个字体对应 6 个优先级框", priorityFields.size() == 6);
                for (Object field : priorityFields) {
                    ((JFormattedTextField)field).setValue(Integer.valueOf(1));
                }
                FontsDialogData data = new FontsDialogData();
                set(dialog, "p", data);
                set(dialog, "o", new t() {
                    @Override
                    public void onFontSelected(v[] fonts, int[] chances) {
                        callbackCount[0] = fonts.length;
                        callbackNames[0] = fonts.length > 0 ? fonts[0].f : "";
                        if (chances.length != fonts.length) {
                            failures++;
                            results.add("FAIL 回调 chance 长度与字体数不一致");
                        }
                    }
                });
                confirm.doClick();
                check("6 个字体确定选择未被 5 个上限拦截", callbackCount[0] == 6);
                check("保存 fontsName 长度=6", data.fontsName != null && data.fontsName.length == 6);
                check("优先权数组长度=6", data.chance != null && data.chance.length == 6);
                check("确定后对话框关闭", !dialog.isVisible());
            }
        });

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                /* Reopen with a newly loaded model, as z does after confirm clears it. */
                local.fontListModel = new DefaultListModel();
                for (int i = 0; i < 8; i++) local.fontListModel.addElement(mock(i));
                local.listFonts.setModel(local.fontListModel);
                set(local, "loading", Boolean.TRUE);
                set(local, "g", Boolean.TRUE);
                dialog.a((FontsDialogData)get(dialog, "p"));
                dialog.a(local.fontListModel);
                check("确认后重开恢复六个已选字体", right.size() == 6);
                check("重开后字体顺序保留", ((v)right.get(0)).f.equals("mock-font-0.gfont")
                        && ((v)right.get(5)).f.equals("mock-font-5.gfont"));
                check("重开后优先级与字体对应", priorityFields.size() == 6
                        && ((Number)((JFormattedTextField)priorityFields.get(5)).getValue()).intValue() == 1);
            }
        });
        final boolean[] offEdtUpdate = new boolean[1];
        final int[] reloadEvents = new int[1];
        right.addListDataListener(new javax.swing.event.ListDataListener() {
            private void updated() { reloadEvents[0]++; if (!SwingUtilities.isEventDispatchThread()) offEdtUpdate[0] = true; }
            public void intervalAdded(javax.swing.event.ListDataEvent e) { updated(); }
            public void intervalRemoved(javax.swing.event.ListDataEvent e) { updated(); }
            public void contentsChanged(javax.swing.event.ListDataEvent e) { updated(); }
        });
        /* Existing z loader calls a(model) on the EDT, then repeats it on its worker. */
        Thread loader = new Thread(new Runnable() {
            public void run() { dialog.a(local.fontListModel); }
        }, "font-reopen-loader");
        loader.start(); loader.join(5000L);
        if (loader.isAlive()) throw new AssertionError("font reload callback did not settle");
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                check("重开后台回调不跨线程修改已选列表", !offEdtUpdate[0]);
                check("重复回调不会清空已恢复的列表", reloadEvents[0] == 0);
                check("重复加载回调后六个字体仍保留", right.size() == 6 && priorityFields.size() == 6);
                check("重复加载后确认按钮可用", confirm.isEnabled());
                leftList.setSelectedIndex(6);
                add.doClick();
                ((JFormattedTextField)priorityFields.get(6)).setValue(Integer.valueOf(7));
            }
        });
        Thread lateLoader = new Thread(new Runnable() {
            public void run() { dialog.a(local.fontListModel); }
        }, "late-font-loader");
        lateLoader.start(); lateLoader.join(5000L);
        if (lateLoader.isAlive()) throw new AssertionError("late font callback did not settle");
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                check("迟到的重复回调不覆盖新加入字体", right.size() == 7
                        && ((v)right.get(6)).f.equals("mock-font-6.gfont"));
                check("迟到的重复回调保留新优先级", priorityFields.size() == 7
                        && ((Number)((JFormattedTextField)priorityFields.get(6)).getValue()).intValue() == 7);
                dialog.dispose();
            }
        });
    }

    private static v mock(int index) {
        v font = new v();
        font.f = "mock-font-" + index + ".gfont";
        font.b = "Mock " + index;
        font.a = "tester";
        font.c = "font-ui check";
        font.e = "kvenjoy";
        font.d = 32;
        return font;
    }

    private static void click(JList list, int index, int count) {
        Rectangle bounds = list.getCellBounds(index, index);
        if (bounds == null) {
            failures++;
            results.add("FAIL 取不到单元格 " + index);
            return;
        }
        Point point = new Point(bounds.x + Math.max(1, bounds.width / 2), bounds.y + Math.max(1, bounds.height / 2));
        for (int n = 1; n <= count; n++) {
            dispatch(list, java.awt.event.MouseEvent.MOUSE_PRESSED, point, n);
            dispatch(list, java.awt.event.MouseEvent.MOUSE_RELEASED, point, n);
            dispatch(list, java.awt.event.MouseEvent.MOUSE_CLICKED, point, n);
        }
    }

    private static void dispatch(JList list, int id, Point point, int count) {
        java.awt.event.MouseEvent event = new java.awt.event.MouseEvent(list,
                id, System.currentTimeMillis(), 0, point.x, point.y, count, false,
                java.awt.event.MouseEvent.BUTTON1);
        list.dispatchEvent(event);
    }

    private static void rawClick(JList list, Point point, int count) {
        for (int n = 1; n <= count; n++) {
            dispatch(list, java.awt.event.MouseEvent.MOUSE_PRESSED, point, n);
            dispatch(list, java.awt.event.MouseEvent.MOUSE_RELEASED, point, n);
            dispatch(list, java.awt.event.MouseEvent.MOUSE_CLICKED, point, n);
        }
    }

    private static void wheel(Component target, int rotation) {
        Point point = new Point(Math.max(1, target.getWidth() / 2), Math.max(1, target.getHeight() / 2));
        java.awt.event.MouseWheelEvent event = new java.awt.event.MouseWheelEvent(target,
                java.awt.event.MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
                point.x, point.y, 0, false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, rotation);
        target.dispatchEvent(event);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            results.add("PASS " + name);
        } else {
            failures++;
            results.add("FAIL " + name);
        }
    }

    private static Object get(Object target, String name) throws RuntimeException {
        try {
            return field(target.getClass(), name).get(target);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    private static void set(Object target, String name, Object value) throws RuntimeException {
        try {
            field(target.getClass(), name).set(target, value);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }
}
