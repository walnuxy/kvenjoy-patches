package com.kenjoy.uitrial;

import java.awt.Component;
import java.awt.EventQueue;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Keeps the vendor main class ABI while preparing fallible mode canvases first. */
public final class MainWindowSafety {
    private static final ThreadLocal<Component> PREPARED=new ThreadLocal<Component>();
    private MainWindowSafety(){}

    public static Component preparedCanvas(Object mode) throws Exception {
        Component canvas=PREPARED.get();
        if(canvas!=null)return canvas;
        return (Component)mode.getClass().getMethod("createCanvas").invoke(mode);
    }

    public static void switchMode(Object app,Object target) {
        if(!SwingUtilities.isEventDispatchThread()){
            try{EventQueue.invokeAndWait(()->switchMode(app,target));}
            catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("模式切换被中断",error);}
            catch(InvocationTargetException error){throw new IllegalStateException("无法在 EDT 切换模式",error.getCause());}
            return;
        }
        Object previous=null;
        try {
            previous=field(app,"g");
            if(target==previous)return;
            JComboBox modes=(JComboBox)field(app,"e");
            if(previous!=null&&MachinePanelWorkflow.sourceLocked(app)){
                modes.setSelectedItem(previous);
                JOptionPane.showMessageDialog((Component)app.getClass().getMethod("f").invoke(app),"正在准备或执行书写，请等待任务结束后切换模式。","切换模式",JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            Component canvas=(Component)target.getClass().getMethod("createCanvas").invoke(target);
            if(canvas==null)throw new IllegalStateException("功能面板没有提供画布");
            PREPARED.set(canvas);
            Method original=app.getClass().getDeclaredMethod("modernizationSwitchMode",Class.forName("com.kenjoy.kenjoycnc.processTabs.IProcessTab"));
            original.setAccessible(true);
            original.invoke(app,target);
        }catch(Exception error){
            Throwable cause=error instanceof InvocationTargetException?((InvocationTargetException)error).getCause():error;
            try{JComboBox modes=(JComboBox)field(app,"e");if(previous!=null&&modes.getSelectedItem()!=previous)modes.setSelectedItem(previous);}catch(Exception ignored){}
            JOptionPane.showMessageDialog(null,"切换模式失败："+(cause.getMessage()==null?cause.getClass().getSimpleName():cause.getMessage()),"切换模式",JOptionPane.ERROR_MESSAGE);
        }finally{PREPARED.remove();}
    }

    public static void firmwareEvent(Object app,Object event){
        if(!SwingUtilities.isEventDispatchThread()){EventQueue.invokeLater(()->firmwareEvent(app,event));return;}
        try{
            Method original=app.getClass().getDeclaredMethod("modernizationOnEvent",Class.forName("flash.events.Event"));
            original.setAccessible(true);original.invoke(app,event);
        }catch(InvocationTargetException error){Throwable cause=error.getCause();if(cause instanceof RuntimeException)throw (RuntimeException)cause;if(cause instanceof Error)throw (Error)cause;throw new IllegalStateException(cause);}
        catch(Exception error){throw new IllegalStateException("无法处理设备状态事件",error);}
    }
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
}
