package com.kenjoy.kenjoycnc.note;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;

/** A modeless overview so the article preview stays available while managing rules. */
public final class NoteForbiddenZoneManager {
    public enum Kind { RULE, PAGE_ZONE, EXCEPTION }
    public static final class Row {
        public final Kind kind;
        public final String id;
        public final int page;
        private final String[] values;
        Row(Kind kind,String id,int page,String... values){this.kind=kind;this.id=id;this.page=page;this.values=values;}
        String key(){return kind+":"+page+":"+id;}
    }
    public interface Host {
        NoteDocument document();
        boolean busy();
        String selectedId();
        void preview(Row row);
        void edit(Row row);
        void delete(Row row);
        void addRule();
        void addPageZone();
        void pageException();
    }
    private final Host host;
    private final JDialog dialog;
    private final JTable table;
    private final Model model=new Model();
    private final JLabel summary=new JLabel();
    private final JButton edit,delete,locate,add,addPage,exception;
    private boolean refreshing,notifying;
    private static final class ManagerDialog extends JDialog {
        ManagerDialog(java.awt.Window owner){super(owner,"管理全部禁写区规则",Dialog.ModalityType.MODELESS);}
    }

    public NoteForbiddenZoneManager(Component parent,Host host){
        this.host=host;dialog=new ManagerDialog(SwingUtilities.getWindowAncestor(parent));
        dialog.setName("NoteForbiddenZoneManager");dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel body=new JPanel(new BorderLayout(8,8));body.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        body.add(summary,BorderLayout.NORTH);
        table=new JTable(model);table.setName("NoteForbiddenZoneManagerTable");table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setFillsViewportHeight(true);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(Math.max(table.getRowHeight(),table.getFontMetrics(table.getFont()).getHeight()+10));
        int[] widths={100,165,155,75,75,75,75,90,95,230};
        double uiScale=1;try{uiScale=((Number)Class.forName("com.kenjoy.uitrial.NativeDpi").getMethod("scale").invoke(null)).doubleValue();}catch(ReflectiveOperationException ignored){}
        for(int i=0;i<widths.length;i++)table.getColumnModel().getColumn(i).setPreferredWidth((int)Math.round(widths[i]*uiScale));
        body.add(new JScrollPane(table),BorderLayout.CENTER);
        JPanel actions=new JPanel(new GridLayout(0,3,6,6));
        add=button("新增规则…","NoteForbiddenManagerAdd",()->host.addRule());
        addPage=button("添加本页区域…","NoteForbiddenManagerAddPage",()->host.addPageZone());
        edit=button("编辑选中…","NoteForbiddenManagerEdit",()->{Row row=selected();if(row!=null&&!host.busy())host.edit(row);});
        delete=button("删除选中","NoteForbiddenManagerDelete",()->{Row row=selected();if(row!=null&&!host.busy())host.delete(row);});
        locate=button("在预览中定位","NoteForbiddenManagerLocate",()->previewSelected());
        exception=button("当前页例外…","NoteForbiddenManagerException",()->host.pageException());
        for(JButton button:new JButton[]{add,addPage,edit,delete,locate,exception})actions.add(button);
        JButton close=button("关闭","NoteForbiddenManagerClose",()->dialog.dispose());
        JPanel closeWrap=new JPanel(new GridBagLayout());GridBagConstraints cg=new GridBagConstraints();cg.anchor=GridBagConstraints.SOUTH;closeWrap.add(close,cg);
        JPanel footer=new JPanel(new BorderLayout(8,8));footer.add(actions,BorderLayout.CENTER);footer.add(closeWrap,BorderLayout.EAST);
        JLabel hint=new JLabel("选中区域会在纸张预览中高亮；双击表格行编辑。修改支持撤销，单页例外也列在此处。");
        hint.setName("NoteForbiddenManagerHint");
        JPanel bottom=new JPanel(new BorderLayout(0,10));bottom.add(hint,BorderLayout.NORTH);bottom.add(footer,BorderLayout.CENTER);body.add(bottom,BorderLayout.SOUTH);
        table.getSelectionModel().addListSelectionListener(event->{if(!event.getValueIsAdjusting()&&!refreshing){updateButtons();previewSelected();}});
        table.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent event){if(event.getButton()==MouseEvent.BUTTON1&&event.getClickCount()==2){Row row=selected();if(row!=null&&!host.busy())host.edit(row);}}});
        dialog.setContentPane(body);Rectangle bounds=parent.getGraphicsConfiguration()==null?GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds():parent.getGraphicsConfiguration().getBounds();
        dialog.setSize(Math.min(1160,Math.max(600,bounds.width-80)),Math.min(570,Math.max(300,bounds.height-100)));dialog.setLocationRelativeTo(parent);
        refresh();
    }
    private JButton button(String text,String name,Runnable action){JButton button=new JButton(text);button.setName(name);button.addActionListener(event->action.run());return button;}
    public boolean isDisplayable(){return dialog.isDisplayable();}
    public void close(){dialog.dispose();}
    public void updateState(){updateButtons();}
    public void show(){refresh();dialog.setVisible(true);dialog.toFront();}
    public void select(String id){
        if(refreshing||notifying)return;refreshing=true;
        try{table.clearSelection();if(id!=null)for(int i=0;i<model.rows.size();i++)if(id.equals(model.rows.get(i).id)){int view=table.convertRowIndexToView(i);table.setRowSelectionInterval(view,view);table.scrollRectToVisible(table.getCellRect(view,0,true));break;}}
        finally{refreshing=false;updateButtons();}
    }
    public void refresh(){
        if(refreshing||notifying)return;Row previous=selected();String key=previous==null?null:previous.key();String id=host.selectedId();
        refreshing=true;
        try{
            NoteDocument document=host.document();model.rows=rows(document);model.fireTableDataChanged();
            int target=-1;for(int i=0;i<model.rows.size();i++){Row row=model.rows.get(i);if(key!=null&&key.equals(row.key()))target=i;}
            if(id!=null)for(int i=0;i<model.rows.size();i++)if(id.equals(model.rows.get(i).id)){target=i;break;}
            if(target>=0){int view=table.convertRowIndexToView(target);table.setRowSelectionInterval(view,view);}
            summary.setText("整篇文章："+document.forbiddenZones.rules.size()+" 条页面规则，"+model.rows.stream().filter(row->row.kind==Kind.PAGE_ZONE).count()+" 个单页区域，"+model.rows.stream().filter(row->row.kind==Kind.EXCEPTION).count()+" 组页面例外");
        }finally{refreshing=false;updateButtons();}
    }
    private Row selected(){int view=table.getSelectedRow();return view<0?null:model.rows.get(table.convertRowIndexToModel(view));}
    private void previewSelected(){if(refreshing||notifying)return;Row row=selected();if(row==null)return;notifying=true;try{host.preview(row);}finally{notifying=false;}}
    private void updateButtons(){boolean idle=!host.busy(),chosen=selected()!=null;edit.setEnabled(idle&&chosen);delete.setEnabled(idle&&chosen);locate.setEnabled(chosen);add.setEnabled(idle);addPage.setEnabled(idle);exception.setEnabled(idle);}
    public static List<Row> rows(NoteDocument document){
        List<Row> result=new ArrayList<Row>();Map<String,String> names=new HashMap<String,String>();
        for(NoteForbiddenZones.Rule rule:document.forbiddenZones.rules){
            names.put(rule.id,rule.name);String scope=rule.pageMode==NoteForbiddenZones.PageMode.CUSTOM?"指定页 "+pages(rule.customPages):rule.pageMode.toString();
            result.add(new Row(Kind.RULE,rule.id,0,"页面规则",rule.name,scope,number(rule.x),number(rule.y),number(rule.width),number(rule.height),number(rule.clearanceMm),rule.mirrorEven?"偶数页镜像":"关闭",""));
        }
        for(NoteForbiddenZones.PageException exception:document.forbiddenZones.exceptions){
            if(!exception.disabledRuleIds.isEmpty()){
                List<String> disabled=new ArrayList<String>();for(String id:exception.disabledRuleIds)disabled.add(names.containsKey(id)?names.get(id):id);
                result.add(new Row(Kind.EXCEPTION,"exception:"+exception.page,exception.page,"单页例外","停用继承规则","第 "+exception.page+" 页","—","—","—","—","—","—",String.join("、",disabled)));
            }
            for(NoteForbiddenZones.Rect rect:exception.additionalZones)result.add(new Row(Kind.PAGE_ZONE,rect.id,exception.page,"单页区域",rect.name,"第 "+exception.page+" 页",number(rect.x),number(rect.y),number(rect.width),number(rect.height),number(rect.clearanceMm),"—",""));
        }
        return result;
    }
    private static String number(float value){return String.format(Locale.ROOT,"%.2f",value);}
    private static String pages(List<Integer> values){List<String> parts=new ArrayList<String>();for(Integer value:values)parts.add(String.valueOf(value));return String.join(",",parts);}
    private static final class Model extends AbstractTableModel {
        private final String[] titles={"类型","名称","适用页面","X / mm","Y / mm","宽 / mm","高 / mm","安全边距 / mm","偶数页处理","例外停用的规则"};
        private List<Row> rows=new ArrayList<Row>();
        public int getRowCount(){return rows.size();}public int getColumnCount(){return titles.length;}
        public String getColumnName(int column){return titles[column];}public Object getValueAt(int row,int column){return rows.get(row).values[column];}
        public boolean isCellEditable(int row,int column){return false;}
    }
}
