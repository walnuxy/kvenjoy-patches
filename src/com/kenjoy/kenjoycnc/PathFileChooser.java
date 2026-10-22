package com.kenjoy.kenjoycnc;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.File;
import javax.swing.*;

/** Explicit pasteable path entry for the application's shared file choosers. */
public final class PathFileChooser extends JFileChooser {
    private final JTextField pathInput = new JTextField();
    private final JLabel feedback = new JLabel(" ");

    public PathFileChooser() {
        super();
        JPanel accessory = new JPanel(new BorderLayout(0, 8));
        accessory.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        accessory.setPreferredSize(new Dimension(280, 140));
        JPanel input = new JPanel(new BorderLayout(0, 6));
        input.add(new JLabel("粘贴文件或文件夹路径（Ctrl+V）"), BorderLayout.NORTH);
        pathInput.setName("PastedPath");
        input.add(pathInput, BorderLayout.CENTER);
        JButton use = new JButton("转到目录 / 选择文件");
        input.add(use, BorderLayout.SOUTH);
        accessory.add(input, BorderLayout.NORTH);
        feedback.setName("PathFeedback");
        accessory.add(feedback, BorderLayout.CENTER);
        setAccessory(accessory);
        pathInput.addActionListener(event -> usePastedPath());
        use.addActionListener(event -> usePastedPath());
    }

    public void usePastedPath() {
        String value = pathInput.getText().trim();
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) value = value.substring(1, value.length()-1);
        if (value.length() == 0) { feedback.setText("请先粘贴路径。"); return; }
        File file = new File(value);
        if (!file.isAbsolute()) file = new File(getCurrentDirectory(), value);
        if (file.isDirectory()) {
            setCurrentDirectory(file);
            if (getFileSelectionMode() != FILES_ONLY) setSelectedFile(file);
            feedback.setText("已转到目录，可继续选择。");
            return;
        }
        boolean save = getDialogType() == SAVE_DIALOG;
        if ((!save && !file.isFile()) || file.getParentFile() == null || !file.getParentFile().isDirectory()) {
            feedback.setText("路径不存在，请检查后重试。"); return;
        }
        if (getFileSelectionMode() == DIRECTORIES_ONLY) { feedback.setText("此处需要文件夹路径。"); return; }
        if (!accept(file)) { feedback.setText("文件类型不符合当前筛选条件。"); return; }
        setCurrentDirectory(file.getParentFile());
        setSelectedFile(file);
        if (isMultiSelectionEnabled()) setSelectedFiles(new File[]{file});
        feedback.setText("已选择，点击原窗口的打开或保存确认。");
        // Keep the standard approve button so the original save/overwrite flow still runs.
    }
}
