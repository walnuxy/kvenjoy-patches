package com.kenjoy.uitrial;

import java.lang.reflect.Field;
import java.awt.Component;
import java.awt.Container;
import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JOptionPane;

/** Runs immediately before every original PDF preview render, including DPI and page changes. */
public final class PdfRenderSafety {
    private static final double MAX_PIXELS=4_000_000.0;
    private static final Map<Object,State> LAST_RENDER=new WeakHashMap<Object,State>();
    private static final class State {final int page;final float dpi;State(int page,float dpi){this.page=page;this.dpi=dpi;}}
    private PdfRenderSafety(){}

    public static boolean withinLimit(double widthPoints,double heightPoints,double dpi){
        double width=widthPoints*dpi/72.0,height=heightPoints*dpi/72.0;
        return Double.isFinite(width)&&Double.isFinite(height)&&dpi>0&&width>=1&&height>=1&&width*height<=MAX_PIXELS;
    }

    public static boolean allow(Object dialog,int pageIndex){return evaluate(dialog,pageIndex,true);}

    private static boolean evaluate(Object dialog,int pageIndex,boolean warn){
        try{
            Field documentField=dialog.getClass().getDeclaredField("d"),dpiField=dialog.getClass().getDeclaredField("g");
            documentField.setAccessible(true);dpiField.setAccessible(true);
            Object document=documentField.get(dialog);
            double dpi=dpiField.getFloat(dialog);
            if(document!=null&&pageIndex>=0&&pageIndex<((Number)document.getClass().getMethod("getNumberOfPages").invoke(document)).intValue()){
                Object page=document.getClass().getMethod("getPage",int.class).invoke(document,pageIndex);
                Object box=page.getClass().getMethod("getCropBox").invoke(page);
                double width=((Number)box.getClass().getMethod("getWidth").invoke(box)).doubleValue();
                double height=((Number)box.getClass().getMethod("getHeight").invoke(box)).doubleValue();
                if(withinLimit(width,height,dpi)){confirm((JDialog)dialog,false);return true;}
            }
        }catch(Exception error){System.err.println("PDF_RENDER_CHECK "+error);}
        State previous; synchronized(LAST_RENDER){previous=LAST_RENDER.get(dialog);}
        try{
            if(previous==null){Field image=dialog.getClass().getField("a");image.set(null,null);confirm((JDialog)dialog,false);}
            else{
                Field dpiField=dialog.getClass().getDeclaredField("g"),inputField=dialog.getClass().getDeclaredField("h"),pagesField=dialog.getClass().getDeclaredField("c");
                dpiField.setAccessible(true);inputField.setAccessible(true);pagesField.setAccessible(true);
                dpiField.setFloat(dialog,previous.dpi);((JFormattedTextField)inputField.get(dialog)).setText(String.valueOf((int)previous.dpi));
                JComboBox pages=(JComboBox)pagesField.get(dialog);
                boolean pageChanged=pages.getSelectedIndex()!=previous.page;
                if(pageChanged)pages.setSelectedIndex(previous.page);
                else confirm((JDialog)dialog,true);
            }
        }catch(Exception restore){System.err.println("PDF_RENDER_RESTORE "+restore);confirm((JDialog)dialog,false);}
        if(warn)JOptionPane.showMessageDialog((JDialog)dialog,"此页在当前分辨率下超过 400 万像素；请降低 DPI。","PDF 页面",JOptionPane.WARNING_MESSAGE);
        return false;
    }

    public static void completed(Object dialog,int pageIndex){
        try{
            Field dpiField=dialog.getClass().getDeclaredField("g");dpiField.setAccessible(true);
            synchronized(LAST_RENDER){LAST_RENDER.put(dialog,new State(pageIndex,dpiField.getFloat(dialog)));}
            confirm((JDialog)dialog,true);
        }catch(Exception error){System.err.println("PDF_RENDER_COMPLETE "+error);confirm((JDialog)dialog,false);}
    }

    private static void confirm(JDialog dialog,boolean enabled){
        for(Component section:dialog.getContentPane().getComponents())if(section instanceof Container)
            for(Component child:((Container)section).getComponents())if(child instanceof JButton&&"确定".equals(((JButton)child).getText()))((JButton)child).setEnabled(enabled);
    }
}
