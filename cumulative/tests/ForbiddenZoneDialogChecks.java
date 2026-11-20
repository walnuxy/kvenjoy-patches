import com.kenjoy.kenjoycnc.note.*;
import com.kenjoy.kenjoycnc.processTabs.NoteTab;
import java.awt.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

/** Drives only this isolated JVM's Swing dialogs; no system input or devices. */
public final class ForbiddenZoneDialogChecks {
    private static final List<String> results=new ArrayList<String>();
    private static void check(String name,boolean ok){
        results.add((ok?"PASS ":"FAIL ")+name);NoteStateChecks.check(name,ok);
        if(!ok)throw new AssertionError(name);
    }
    private static <T>List<T> children(Container root,Class<T> type){
        List<T> found=new ArrayList<T>();
        for(Component child:root.getComponents()){
            if(type.isInstance(child))found.add(type.cast(child));
            if(child instanceof Container)found.addAll(children((Container)child,type));
        }
        return found;
    }
    private static JOptionPane pane(JDialog dialog){List<JOptionPane> found=children(dialog,JOptionPane.class);return found.isEmpty()?null:found.get(0);}
    private static void geometry(NoteTab tab,boolean rule)throws Exception{
        String title=rule?"添加禁写区规则":"本页额外禁写区";
        int[] submitted={0},errors={0};Throwable[] failure={null};JDialog[] original={null};
        long deadline=System.nanoTime()+10_000_000_000L;
        javax.swing.Timer timer=new javax.swing.Timer(30,event->{
            if(System.nanoTime()>deadline){failure[0]=new AssertionError("dialog timeout: "+title);for(Window w:Window.getWindows())if(w instanceof JDialog)w.dispose();return;}
            for(Window window:Window.getWindows())if(window instanceof JDialog&&window.isShowing()){
                JDialog dialog=(JDialog)window;JOptionPane options=pane(dialog);if(options==null)continue;
                if(options.getMessageType()==JOptionPane.ERROR_MESSAGE){errors[0]++;options.setValue(JOptionPane.OK_OPTION);return;}
                if(!title.equals(dialog.getTitle()))continue;
                try{
                    if(original[0]==null)original[0]=dialog;else check(title+" reuses dialog and retains input "+submitted[0],original[0]==dialog);
                    List<JFormattedTextField> numbers=children(dialog,JFormattedTextField.class);
                    JTextField name=null;for(JTextField field:children(dialog,JTextField.class))if(!(field instanceof JFormattedTextField)){name=field;break;}
                    if(name==null||numbers.size()!=5)throw new AssertionError("missing geometry fields");
                    if(submitted[0]>0){
                        check(title+" keeps name after geometry error "+submitted[0],"retained name".equals(name.getText()));
                        check(title+" reported the previous geometry error "+submitted[0],errors[0]==submitted[0]);
                        int index=submitted[0]==1?2:submitted[0]==2?4:0;
                        float expected=submitted[0]==1?-1:submitted[0]==2?26:205;
                        check(title+" keeps invalid numeric input "+submitted[0],((Number)numbers.get(index).getValue()).floatValue()==expected);
                    }
                    name.setText("retained name");
                    float[] values={10,10,30,20,.5f};for(int i=0;i<values.length;i++)numbers.get(i).setValue(values[i]);
                    if(submitted[0]==0)numbers.get(2).setValue(-1);
                    else if(submitted[0]==1)numbers.get(4).setValue(26);
                    else if(submitted[0]==2)numbers.get(0).setValue(205);
                    submitted[0]++;options.setValue(JOptionPane.OK_OPTION);return;
                }catch(Throwable error){failure[0]=error;options.setValue(JOptionPane.CANCEL_OPTION);return;}
            }
        });
        Method method=NoteTab.class.getDeclaredMethod(rule?"forbiddenRuleDialog":"forbiddenRectDialog",rule?NoteForbiddenZones.Rule.class:NoteForbiddenZones.Rect.class,float.class,float.class,float.class,float.class);method.setAccessible(true);
        String before=tab.document().encode();int undo=tab.session().undoSize();Object result;
        timer.start();try{result=method.invoke(tab,null,10f,10f,30f,20f);}finally{timer.stop();}
        if(failure[0]!=null)throw new AssertionError(title,failure[0]);
        check(title+" rejects three invalid geometries before returning",errors[0]==3&&submitted[0]==4);
        check(title+" accepts correction",result instanceof NoteForbiddenZones.Rect&&((NoteForbiddenZones.Rect)result).width==30&&"retained name".equals(((NoteForbiddenZones.Rect)result).name));
        check(title+" leaves document and history unchanged until commit",before.equals(tab.document().encode())&&undo==tab.session().undoSize());
    }
    private static void exceptionDialog(NoteTab tab,boolean uncheck)throws Exception{
        boolean[] answered={false};Throwable[] failure={null};long deadline=System.nanoTime()+5_000_000_000L;
        javax.swing.Timer timer=new javax.swing.Timer(30,event->{
            for(Window window:Window.getWindows())if(window instanceof JDialog&&window.isShowing()&&"设置当前页例外".equals(((JDialog)window).getTitle())){
                JDialog dialog=(JDialog)window;JOptionPane options=pane(dialog);if(options==null)continue;
                try{List<JCheckBox> boxes=children(dialog,JCheckBox.class);check("exception dialog only shows applicable rule",boxes.size()==1);if(uncheck)boxes.get(0).setSelected(false);answered[0]=true;options.setValue(JOptionPane.OK_OPTION);}
                catch(Throwable error){failure[0]=error;options.setValue(JOptionPane.CANCEL_OPTION);}return;
            }
            if(System.nanoTime()>deadline){failure[0]=new AssertionError("exception dialog timeout");for(Window w:Window.getWindows())if(w instanceof JDialog)w.dispose();}
        });
        Method method=NoteTab.class.getDeclaredMethod("editForbiddenZoneException",int.class);method.setAccessible(true);
        timer.start();try{method.invoke(tab,1);}finally{timer.stop();}
        if(failure[0]!=null)throw new AssertionError("exception dialog",failure[0]);check("exception dialog accepted",answered[0]);
    }
    public static void run()throws Exception{
        NoteStateChecks.create();NoteStateChecks.edt(()->null);NoteStateChecks.waitDone();
        final NoteTab tab=NoteStateChecks.tab;
        NoteDocument document=NoteStateChecks.document("");
        NoteForbiddenZones.Rule hidden=new NoteForbiddenZones.Rule("hidden odd-page exception",10,10,30,20,0);hidden.pageMode=NoteForbiddenZones.PageMode.EVEN;
        NoteForbiddenZones.Rule shown=new NoteForbiddenZones.Rule("visible exception",50,10,30,20,0);
        document.forbiddenZones.rules.add(hidden);document.forbiddenZones.rules.add(shown);
        document.forbiddenZones.setException(1,Arrays.asList(hidden.id,shown.id));
        NoteStateChecks.edt(()->{tab.setDocument(document);return null;});NoteStateChecks.waitDone();
        try{
            NoteStateChecks.edt(()->{geometry(tab,true);geometry(tab,false);return null;});
            String before=NoteStateChecks.edt(()->tab.document().encode());int undo=NoteStateChecks.edt(()->tab.session().undoSize());
            NoteStateChecks.edt(()->{exceptionDialog(tab,false);return null;});NoteStateChecks.waitDone();
            check("accepting unchanged exceptions preserves document and undo",NoteStateChecks.edt(()->before.equals(tab.document().encode())&&undo==tab.session().undoSize()));
            NoteStateChecks.edt(()->{exceptionDialog(tab,true);return null;});NoteStateChecks.waitDone();
            check("unchecking a visible rule retains hidden exception",NoteStateChecks.edt(()->{List<String> ids=tab.document().forbiddenZones.exception(1).disabledRuleIds;return ids.contains(hidden.id)&&!ids.contains(shown.id)&&tab.session().undoSize()==undo+1;}));
            NoteStateChecks.edt(()->{tab.history(false);return null;});NoteStateChecks.waitDone();
            check("exception edit undo restores both records",NoteStateChecks.edt(()->tab.document().forbiddenZones.exception(1).disabledRuleIds.containsAll(Arrays.asList(hidden.id,shown.id))));
            NoteStateChecks.edt(()->{tab.history(true);return null;});NoteStateChecks.waitDone();
            check("exception redo retains hidden record",NoteStateChecks.edt(()->{List<String> ids=tab.document().forbiddenZones.exception(1).disabledRuleIds;return ids.contains(hidden.id)&&!ids.contains(shown.id);}));
            NoteDocument all=NoteStateChecks.edt(()->tab.document().copy());all.forbiddenZones.rules.get(0).pageMode=NoteForbiddenZones.PageMode.ALL;
            check("restoring all-page scope keeps the old single-page exclusion",all.forbiddenZones.resolve(1,210,297).stream().noneMatch(zone->zone.id.equals(hidden.id)));
            System.out.println("FORBIDDEN_ZONE_DIALOG_CHECKS_COMPLETE "+results.size());
        }finally{NoteStateChecks.stop();for(Window window:Window.getWindows())window.dispose();}
    }
}
