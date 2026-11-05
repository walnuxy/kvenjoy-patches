package com.kenjoy.uitrial;

import java.awt.event.*;
import javax.swing.*;

/** Replace only the main window's generic exit confirmation; normal JVM shutdown remains. */
public final class DirectClose {
    private DirectClose(){}
    public static void install(JFrame frame){
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("EDT required");
        if(Boolean.TRUE.equals(frame.getRootPane().getClientProperty("trial.directClose")))return;
        WindowListener confirmation=null;
        for(WindowListener listener:frame.getWindowListeners())if(listener.getClass().getName().equals("com.kenjoy.kenjoycnc.u")){
            if(confirmation!=null)throw new IllegalStateException("Unexpected duplicate exit confirmation");
            confirmation=listener;
        }
        if(confirmation==null)throw new IllegalStateException("Expected main-window confirmation listener not found");
        frame.removeWindowListener(confirmation);
        frame.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent event){if(prepareClose(frame))System.exit(0);}});
        frame.getRootPane().putClientProperty("trial.directClose",Boolean.TRUE);
    }
    static boolean prepareClose(JFrame frame){
        try{
            Object app=Class.forName("com.kenjoy.kenjoycnc.KenjoyDraw").getMethod("a").invoke(null);
            Object mode=app.getClass().getMethod("d").invoke(app);
            if(mode!=null&&mode.getClass().getName().equals("com.kenjoy.kenjoycnc.processTabs.NoteTab"))return prepareMode(mode);
            JComboBox modes=(JComboBox)UiStyle.field(app,"e");
            for(int i=0;i<modes.getItemCount();i++){Object item=modes.getItemAt(i);if(item!=null&&item.getClass().getName().equals("com.kenjoy.kenjoycnc.processTabs.NoteTab"))return prepareMode(item);}
            return true;
        }catch(Exception error){
            Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();
            JOptionPane.showMessageDialog(frame,"无法完成文章关闭处理："+String.valueOf(cause.getMessage()),"关闭",JOptionPane.ERROR_MESSAGE);return false;
        }
    }
    static boolean prepareMode(Object mode)throws Exception{
        if(mode==null||!mode.getClass().getName().equals("com.kenjoy.kenjoycnc.processTabs.NoteTab"))return true;
        return Boolean.TRUE.equals(mode.getClass().getMethod("prepareEditorClose").invoke(mode));
    }
}
