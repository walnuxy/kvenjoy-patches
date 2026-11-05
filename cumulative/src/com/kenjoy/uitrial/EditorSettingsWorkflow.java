package com.kenjoy.uitrial;

import javax.swing.*;

/** Makes the article close preference available from the application's Settings menu. */
public final class EditorSettingsWorkflow {
    private EditorSettingsWorkflow(){}
    public static void install(Object app)throws Exception{
        JFrame frame=(JFrame)app.getClass().getMethod("f").invoke(app);JMenuBar bar=frame.getJMenuBar();
        for(int i=0;i<bar.getMenuCount();i++){
            JMenu menu=bar.getMenu(i);if(menu==null||!"设置".equals(menu.getText()))continue;
            if(Boolean.TRUE.equals(menu.getClientProperty("note.editorSettings")))return;
            JMenuItem item=new JMenuItem("文章编辑设置…");item.setName("NoteEditorSettingsMenu");
            item.addActionListener(e->{try{
                JComboBox modes=(JComboBox)UiStyle.field(app,"e");
                for(int j=0;j<modes.getItemCount();j++){Object mode=modes.getItemAt(j);if(mode.getClass().getName().equals("com.kenjoy.kenjoycnc.processTabs.NoteTab")){mode.getClass().getMethod("showEditorSettings").invoke(mode);return;}}
            }catch(Exception error){throw new IllegalStateException("无法打开文章编辑设置",error);}});
            menu.addSeparator();menu.add(item);menu.putClientProperty("note.editorSettings",Boolean.TRUE);return;
        }
    }
}
