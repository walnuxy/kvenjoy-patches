package com.kenjoy.uitrial;

import java.awt.*;
import javax.swing.*;

/** Original production startup/activation remains in control; these hooks only attach the reviewed UI. */
public final class ProductionHooks {
    private static final String SESSION="modernization.productionStyle";
    private ProductionHooks(){}
    public static void begin(){onEdt(()->{
        try{ApplicationTheme.setup();}
        catch(Exception e){throw new IllegalStateException("Unable to initialize the reviewed Windows UI theme",e);}
    });}
    public static void frame(JFrame frame){frame.setUndecorated(true);frame.getRootPane().setWindowDecorationStyle(JRootPane.NONE);}
    public static void ready(final Object app){onEdt(()->{
        try{
            JFrame frame=(JFrame)app.getClass().getMethod("f").invoke(app);
            if(frame.getRootPane().getClientProperty(SESSION)!=null)return;
            frame.getRootPane().putClientProperty("modernization.production",Boolean.TRUE);
            TrialChrome.install(frame);
            DirectClose.install(frame);
            UiStyle style=new UiStyle();
            OpenFileWorkflow.install(app);
            NoteExportDialogWorkflow.install();
            ApplicationTheme.install(frame);
            ResponsiveWorkspace.install(app);
            MachinePanelWorkflow.install(app);
            Class.forName("com.kenjoy.kenjoycnc.connection.ConnectionService").getMethod("install",Object.class).invoke(null,app);
            NativeDpi.tree(frame.getRootPane());
            style.apply(app);
            MachinePanelWorkflow.refresh(app);
            TrialChrome.fitScreenOnStartup(frame);
            frame.getRootPane().putClientProperty(SESSION,style);
            frame.getRootPane().putClientProperty("modernization.preinstalled",Boolean.TRUE);
            System.out.println("UI_PREINSTALL_READY version=0.018");
        }catch(Exception e){throw new IllegalStateException("Unable to initialize preinstalled UI",e);}
    });}
    public static void refresh(final Object app){onEdt(()->{
        try{
            JFrame frame=(JFrame)app.getClass().getMethod("f").invoke(app);
            Object style=frame.getRootPane().getClientProperty(SESSION);
            if(!(style instanceof UiStyle))return;
            OpenFileWorkflow.refresh(app);
            ApplicationTheme.applyWindow(frame);
            ResponsiveWorkspace.install(app);
            MachinePanelWorkflow.refresh(app);
            NativeDpi.tree(frame.getRootPane());
            ((UiStyle)style).apply(app);
        }catch(Exception e){throw new IllegalStateException("Unable to refresh preinstalled UI",e);}
    });}
    private static void onEdt(Runnable action){
        if(SwingUtilities.isEventDispatchThread())action.run();
        else try{EventQueue.invokeAndWait(action);}catch(Exception e){throw new IllegalStateException(e);}
    }
}
