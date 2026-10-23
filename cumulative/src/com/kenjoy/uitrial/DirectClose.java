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
        frame.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent event){System.exit(0);}});
        frame.getRootPane().putClientProperty("trial.directClose",Boolean.TRUE);
    }
}
