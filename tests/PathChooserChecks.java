package com.kenjoy.kenjoycnc;

import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

public final class PathChooserChecks {
    static final java.util.List<String> results = new ArrayList<String>();
    static int failures;
    static void check(String label, boolean ok) { results.add((ok?"PASS ":"FAIL ")+label);if(!ok)failures++; }
    static JTextField input(Container parent) {
        for (Component c:parent.getComponents()) {
            if(c instanceof JTextField && "PastedPath".equals(c.getName()))return (JTextField)c;
            if(c instanceof Container){JTextField found=input((Container)c);if(found!=null)return found;}
        }
        return null;
    }
    static JFileChooser chooser(Container parent) {
        for(Component c:parent.getComponents()) {
            if(c instanceof JFileChooser)return (JFileChooser)c;
            if(c instanceof Container){JFileChooser f=chooser((Container)c);if(f!=null)return f;}
        }
        return null;
    }
    public static void main(String[] args) throws Exception {
        Path evidence=Paths.get(args[0]);
        Path dir=evidence.toAbsolutePath().getParent().resolve("path-fixtures");Files.createDirectories(dir);
        final File file=dir.resolve("中文 文件.txt").toFile();Files.write(file.toPath(),"test".getBytes(StandardCharsets.UTF_8));
        try {
            // Use a synthetic machine label only for isolated FilePathsData storage.
            Class<?> type=Class.forName("com.kenjoy.kenjoycnc.data.MachineData");Object machine=type.newInstance();
            type.getField("name").set(machine,"PathChooserChecks");
            Class.forName("com.kenjoy.kenjoycnc.o.c").getField("e").set(null,machine);
            SwingUtilities.invokeAndWait(() -> {
                PathFileChooser c=new PathFileChooser();JTextField text=input(c.getAccessory());
                check("公共选择器提供可编辑路径框",text!=null);
                c.setMultiSelectionEnabled(true);text.setText("\""+file+"\"");text.postActionEvent();
                check("粘贴中文空格及带引号文件路径",file.equals(c.getSelectedFile()));
                check("粘贴文件兼容原多选返回值",c.getSelectedFiles().length==1 && file.equals(c.getSelectedFiles()[0]));
                text.setText(dir.toString());text.postActionEvent();
                check("粘贴文件夹直接转到目录",dir.toFile().equals(c.getCurrentDirectory()));
                text.setText(file.toString());text.postActionEvent();File previous=c.getSelectedFile();
                text.setText(dir.resolve("missing.txt").toString());text.postActionEvent();
                check("无效路径保留原选择",previous.equals(c.getSelectedFile()));
                c.setFileFilter(new FileNameExtensionFilter("PNG","png"));File filteredPrevious=c.getSelectedFile();
                text.setText(file.toString());text.postActionEvent();
                check("类型不匹配不替换选择",Objects.equals(filteredPrevious,c.getSelectedFile()));
                c.setFileFilter(c.getAcceptAllFileFilter());c.setDialogType(JFileChooser.SAVE_DIALOG);
                File newFile=dir.resolve("新文件.txt").toFile();text.setText(newFile.toString());text.postActionEvent();
                check("保存路径可包含尚未创建的文件",newFile.equals(c.getSelectedFile()) && !newFile.exists());
                c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);text.setText(dir.toString());text.postActionEvent();
                check("文件夹模式支持粘贴选择",dir.toFile().equals(c.getSelectedFile()));
                // Verify actual shared application entry points construct the enhanced chooser.
                for(int mode=0;mode<3;mode++) {
                    final int which=mode;final boolean[] inspected={false};
                    javax.swing.Timer timer=new javax.swing.Timer(150,event -> {
                        for(Window window:Window.getWindows())if(window.isVisible()&&window instanceof Container){
                            JFileChooser shown=chooser((Container)window);
                            if(shown!=null){inspected[0]=shown instanceof PathFileChooser;shown.cancelSelection();}
                        }
                    });
                    timer.start();
                    try {
                        File selected;
                        if(which==0)selected=L.a(null,"路径输入-载入","path-check",new String[]{"txt"});
                        else if(which==1)selected=L.a(null,"路径输入-保存","path-check","txt","test.txt");
                        else selected=L.a(null,"路径输入-文件夹","path-check");
                        check("原软件入口"+which+"使用增强选择器且取消不选文件",inspected[0]&&selected==null);
                    } finally {timer.stop();}
                }
            });
        } catch(Throwable e){failures++;results.add("FAIL "+e);e.printStackTrace();}
        results.add("SUMMARY "+(failures==0?"PASS":"FAIL")+" failures="+failures);
        Files.write(evidence,results,StandardCharsets.UTF_8);
        for(String line:results)System.out.println(line);
        System.exit(failures==0?0:1);
    }
}
