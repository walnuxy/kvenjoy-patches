package com.kenjoy.kenjoycnc.fonts;
import com.kenjoy.kenjoycnc.L;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

/** Local list is immediately reusable; explicit refresh performs a merged incremental scan. */
public final class z extends J {
    private final Map<v,File> b=new HashMap<>(); // retained for FontFolderFilter ABI
    private final r owner;private final JTextField query=new JTextField(12);
    private final JLabel placeholder=new JLabel();private final JLabel status=new JLabel("正在读取字库…");private final FontCatalogue cache;
    private final Map<v,FontCatalogue.Entry> entries=new IdentityHashMap<>();
    private long generation;private boolean initialized;private FontCatalogue.Snapshot applied;
    public z(r owner){this.owner=owner;cache=FontCatalogue.at(L.c());setLayout(new BorderLayout(0,4));
        JPanel search=new JPanel();search.add(query);button(search,"搜索",()->filter());button(search,"清除",()->{query.setText("");filter();});add(search,BorderLayout.NORTH);
        add(new JScrollPane(listFonts),BorderLayout.CENTER);JPanel actions=new JPanel();button(actions,"导入",()->importFont());button(actions,"删除",()->deleteFont());button(actions,"云同步",()->com.kenjoy.kenjoycnc.fonts.b.a(()->refresh()).setVisible(true));button(actions,"取消加载",()->cancel());actions.add(status);add(actions,BorderLayout.SOUTH);
        final ListCellRenderer delegate=new com.kenjoy.kenjoycnc.l.a();
        listFonts.setFixedCellHeight(108);listFonts.setCellRenderer((list,value,index,selected,focus)->{FontCatalogue.Entry e=entries.get(value);v f=(v)value;if(e!=null){v sample=e.preview();if(sample!=null)f=sample;else {if(index>=0&&list.isShowing()&&list.getVisibleRect().intersects(new Rectangle(0,index*108,1,108)))e.preview(()->{owner.repaint();if(e.previewError!=null)status.setText("预览失败："+e.header.b+"；可刷新重试");});placeholder.setOpaque(true);placeholder.setBackground(selected?list.getSelectionBackground():list.getBackground());placeholder.setForeground(selected?list.getSelectionForeground():list.getForeground());placeholder.setText(f.b+" ("+f.b()+"字) · 预览加载中");return placeholder;}}return delegate.getListCellRendererComponent(list,f,index,selected,focus);});
        SwingUtilities.invokeLater(this::load);
    }
    private void button(JPanel p,String label,Runnable task){JButton b=new JButton(label);b.addActionListener(e->task.run());p.add(b);}
    public void actionPerformed(ActionEvent e){}
    @Override public void clear(){cancel();} // dialog close must not destroy cached glyphs
    @Override public void cancel(){canceled=true;loading=false;generation++;status.setText("已取消加载；保留原列表");}
    @Override public void load(){canceled=false;if(initialized){if(cache.current()!=applied)apply(cache.current());else status.setText("已复用字库缓存");return;}FontCatalogue.Snapshot s=cache.current();if(s!=null){apply(s);return;}refresh();}
    public void refresh(){if(loading)return;loading=true;canceled=false;long ticket=++generation;status.setText("正在后台刷新…");cache.refresh().whenComplete((s,error)->SwingUtilities.invokeLater(()->{if(ticket!=generation)return;loading=false;if(error!=null){status.setText("刷新失败，保留原列表："+error.getCause());return;}apply(s);}));}
    private void apply(FontCatalogue.Snapshot s){applied=s;long n=System.nanoTime();b.clear();entries.clear();for(FontCatalogue.Entry e:s.entries){b.put(e.header,e.path.toFile());entries.put(e.header,e);}initialized=true;DefaultListModel all=new DefaultListModel();for(FontCatalogue.Entry e:s.entries)all.addElement(e.header);fontListModel=all;owner.catalogueChanged(all);owner.refreshFolders();filter();status.setText(s.errors.isEmpty()?s.entries.size()+" 个字体，复用 "+s.reused+" 个":"已载入 "+s.entries.size()+" 个；损坏 "+s.errors.size()+" 个");status.setToolTipText(s.errors.toString());System.out.println("FONT_CATALOG scan_ns="+s.scanNanos+" parse_ns="+s.parseNanos+" ui_ns="+(System.nanoTime()-n));}
    private void filter(){FontCatalogue.Snapshot s=cache.current();if(s==null)return;String text=query.getText().trim().toLowerCase(Locale.ROOT);DefaultListModel next=new DefaultListModel();for(FontCatalogue.Entry e:s.entries)if(e.header.b.toLowerCase(Locale.ROOT).contains(text))next.addElement(e.header);owner.filterLocal(next);}
    private void importFont(){File f=L.a(this,"导入字体",r.class.getName(),new String[]{"gfont"});if(f==null)return;FontCatalogue.WORK.execute(()->{try{Files.copy(f.toPath(),new File(L.c(),f.getName()).toPath(),StandardCopyOption.REPLACE_EXISTING);SwingUtilities.invokeLater(()->refresh());}catch(Exception e){SwingUtilities.invokeLater(()->status.setText("导入失败："+e.getMessage()));}});}
    private void deleteFont(){File f=b.get(getSelected());if(f==null)return;if(JOptionPane.showConfirmDialog(this,"删除字体文件："+f.getName()+"？","删除字体",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;FontCatalogue.WORK.execute(()->{try{Files.delete(f.toPath());SwingUtilities.invokeLater(()->refresh());}catch(Exception e){SwingUtilities.invokeLater(()->status.setText("删除失败："+e.getMessage()));}});}
}
