package com.kenjoy.uitrial;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;

/** Keeps the per-copy random option beside the export copy count. */
public final class NoteExportDialogWorkflow {
    private static final String KEY="modernization.noteExportOption";
    private static boolean listening;
    private NoteExportDialogWorkflow(){}
    public static void install(){
        if(listening)return;listening=true;
        Toolkit.getDefaultToolkit().addAWTEventListener(event->{
            if(event instanceof WindowEvent&&event.getID()==WindowEvent.WINDOW_OPENED){Window window=((WindowEvent)event).getWindow();if(window instanceof JDialog)attach((JDialog)window);}
        },AWTEvent.WINDOW_EVENT_MASK);
    }
    public static void attach(JDialog dialog){
        if(!dialog.getClass().getName().equals("com.kenjoy.kenjoycnc.m")||dialog.getRootPane().getClientProperty(KEY)!=null)return;
        try{
            Object app=Class.forName("com.kenjoy.kenjoycnc.KenjoyDraw").getMethod("a").invoke(null);
            Object mode=app.getClass().getMethod("d").invoke(app);
            if(mode==null||!mode.getClass().getName().equals("com.kenjoy.kenjoycnc.processTabs.NoteTab"))return;
            JPanel copies=findCopies(dialog.getContentPane());if(copies==null)return;
            JCheckBox option=new JCheckBox("每份重新随机字形");option.setName("NoteRandomEachCopy");
            option.setToolTipText("每份导出重新随机未指定字；手动指定字体保持不变。若页数变化，导出会停止。");
            final boolean original=Boolean.TRUE.equals(mode.getClass().getMethod("randomCopies").invoke(mode));
            option.setSelected(original);
            option.addActionListener(e->{try{mode.getClass().getMethod("setRandomCopies",boolean.class).invoke(mode,option.isSelected());}catch(Exception failure){throw new IllegalStateException(failure);}});
            JPanel countRow=null;for(Component child:copies.getComponents())if(child instanceof JPanel){countRow=(JPanel)child;break;}
            if(countRow==null)return;
            countRow.add(option);dialog.getRootPane().putClientProperty(KEY,option);countRow.revalidate();countRow.repaint();
            final boolean[] submitted={false};
            JButton export=(JButton)UiStyle.field(dialog,"i");
            for(ActionListener listener:export.getActionListeners())if(listener==dialog){
                export.removeActionListener(listener);
                export.addActionListener(e->{
                    listener.actionPerformed(e);
                    try{submitted[0]=Boolean.TRUE.equals(UiStyle.field(dialog,"y"));}
                    catch(Exception failure){throw new IllegalStateException(failure);}
                });
            }
            Runnable cancel=()->{if(!submitted[0])try{mode.getClass().getMethod("setRandomCopies",boolean.class).invoke(mode,original);}catch(Exception failure){throw new IllegalStateException(failure);}};
            dialog.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){cancel.run();}public void windowClosed(WindowEvent e){cancel.run();}});
            dialog.addComponentListener(new ComponentAdapter(){public void componentHidden(ComponentEvent e){cancel.run();}});
        }catch(Exception error){throw new IllegalStateException("无法显示笔记批量导出选项",error);}
    }
    private static JPanel findCopies(Container parent){
        for(Component child:parent.getComponents()){
            if(child instanceof JPanel){JPanel panel=(JPanel)child;if(panel.getBorder() instanceof TitledBorder&&"份数".equals(((TitledBorder)panel.getBorder()).getTitle()))return panel;}
            if(child instanceof Container){JPanel found=findCopies((Container)child);if(found!=null)return found;}
        }
        return null;
    }
}
