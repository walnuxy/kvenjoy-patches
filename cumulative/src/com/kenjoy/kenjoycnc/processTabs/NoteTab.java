package com.kenjoy.kenjoycnc.processTabs;

import com.google.gson.Gson;
import com.kenjoy.kenjoycnc.KenjoyDraw;
import com.kenjoy.kenjoycnc.L;
import com.kenjoy.kenjoycnc.data.*;
import com.kenjoy.kenjoycnc.fonts.r;
import com.kenjoy.kenjoycnc.fonts.t;
import com.kenjoy.kenjoycnc.model.Settings;
import com.kenjoy.kenjoycnc.note.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;

/** Article editing slice; original entry points and synthetic accessors remain available. */
public class NoteTab extends BaseTab implements ActionListener,NoteTextEditor.Host {
    private static final long serialVersionUID=1L;
    private JFormattedTextField tfWidth,tfHeight,tfLeftGap,tfRightGap,tfTopGap,tfBottomGap,tfLines,tfWordGap,tfFontSize,tfCurPage;
    private JComboBox cbSettings;
    private DefaultComboBoxModel model;
    private PageInfo pageInfo;
    private JButton btnPrePage,btnNextPage,btnSetContent,btnSave,btnDelete,btnChooseFont;
    private StringBuffer content;
    private float firstLineOffset; // Retained by the original-member compatibility check.
    private WordContentFormat contentSet;
    private final List pageDatas=new ArrayList();
    private int curPage;
    private JLabel labelMaxPage;
    private g notePanel;
    private t chooseFontListener=new h(this);

    private NoteDocument document=new NoteDocument();
    private NoteLayout layout=new NoteLayout();
    private final NoteEditSession editorSession=new NoteEditSession(document);
    private NoteEditGeometry editGeometry;
    private NoteLayout displayLayout;
    private SwingWorker<Prepared,Void> inlinePending;
    private boolean layoutQueued;
    private long layoutRevision=-1;
    private String inlineError;
    private String documentId=UUID.randomUUID().toString();
    private JToggleButton editToggle;
    private final javax.swing.Timer layoutTimer=new javax.swing.Timer(100,e->buildInlineLayout());
    private final javax.swing.Timer recoveryTimer=new javax.swing.Timer(750,e->saveRecoveryAsync());
    private boolean recoveryWriting,recoveryAgain;
    private boolean recoveryRestoring=true;
    private long recoveryStartRevision;
    private float viewAngle,viewScale=1;
    private Point viewPosition=new Point();
    private boolean viewCaptured,pendingViewRestore,changingPage;
    private int observedCaret=-1;
    private boolean observedCaretBackward;

    private final NoteSelection selection=new NoteSelection();
    private volatile NoteFonts catalogue;
    private SwingWorker<Prepared,Void> pending;
    private SwingWorker<NoteDocument,Void> loading;
    private long request;
    private long idHighWater=1;
    private boolean binding;
    private String savedDocument="";
    private Path documentFile;
    private volatile com.kenjoy.kenjoycnc.c.a machineJobGenerator;
    private volatile String machineJobSource;
    private final JLabel selectionLabel=new JLabel("未选字");
    private final JLabel selectionFontLabel=new JLabel(){public Dimension getPreferredSize(){Dimension d=super.getPreferredSize();d.width=1;return d;}public Dimension getMinimumSize(){Dimension d=super.getMinimumSize();d.width=0;return d;}};
    private final JLabel resumeStatusLabel=new JLabel("续写：未设置（整页书写）"){public Dimension getPreferredSize(){Dimension d=super.getPreferredSize();d.width=1;return d;}public Dimension getMinimumSize(){Dimension d=getPreferredSize();d.width=0;return d;}};
    private final JTextArea status=new JTextArea("应用版式保留已选字形；重新随机只影响未指定字体的字。",2,1){public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}};
    private NoteSidebarView sidebar;
    private JPanel pageNavigation;
    private JButton fontButton,resetButton,undoButton,redoButton,clearMissingButton;
    private JButton resumeFromSelectionButton,clearResumeButton,resumeInterruptedButton,resumeNextButton;
    private NoteWriteProgress.Snapshot writeProgress;
    private JCheckBox randomCopies;
    private NoteAppearancePanel appearancePanel;
    private Set<Long> appearanceTargets;
    private long appearanceRequestRevision=-1;
    private JList<ForbiddenZoneItem> forbiddenZoneList;
    private DefaultListModel<ForbiddenZoneItem> forbiddenZoneModel;
    private JButton editForbiddenZoneButton,deleteForbiddenZoneButton;
    private JButton importForbiddenTemplateButton,exportForbiddenTemplateButton;
    private NoteForbiddenZoneManager forbiddenZoneManager;
    private String selectedForbiddenZone;
    private boolean refreshingForbiddenZones;
    private volatile String lastFailure;
    private static final class Prepared {
        final NoteDocument document;final NoteLayout layout;
        Prepared(NoteDocument d,NoteLayout l){document=d;layout=l;}
    }
    private static final class ForbiddenZoneItem {
        final String id,label;final boolean rule;final int page;
        ForbiddenZoneItem(String id,String label,boolean rule,int page){this.id=id;this.label=label;this.rule=rule;this.page=page;}
        public String toString(){return label;}
    }
    public NoteTab(KenjoyDraw owner) {
        super(owner);saveName="note";setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));setName("NoteEditControls");
        setBorder(BorderFactory.createEmptyBorder(2,3,4,3));
        JPanel article=section("文章");btnSetContent=button("编辑文章…");btnChooseFont=button("默认字体…");btnChooseFont.setToolTipText("设置文章默认字体与字形选取比例；点击“重抽未指定字形”后应用到未指定字");
        JButton saveDoc=new JButton("保存文章…");saveDoc.setName("NoteSaveDocument");saveDoc.addActionListener(e->saveDocumentDialog());
        JButton loadDoc=new JButton("打开文章…");loadDoc.setName("NoteOpenDocument");loadDoc.addActionListener(e->openDocumentDialog());
        JButton refreshFonts=new JButton("刷新字库");refreshFonts.setToolTipText("字库文件变化后重新扫描");refreshFonts.addActionListener(e->refreshFonts());
        wide(article,0,buttons(btnSetContent,btnChooseFont));wide(article,1,buttons(loadDoc,saveDoc));wide(article,2,refreshFonts);
        JPanel paper=section("页面与模板");cbSettings=new JComboBox();cbSettings.setEditable(true);cbSettings.setPrototypeDisplayValue("页面模板名称");model=new DefaultComboBoxModel();cbSettings.setModel(model);cbSettings.addActionListener(this);wide(paper,0,cbSettings);
        btnSave=button("保存模板");btnDelete=button("删除模板");wide(paper,1,buttons(btnSave,btnDelete));
        tfWidth=number(210);tfHeight=number(297);form(paper,2,"宽 / 高 mm",tfWidth,tfHeight);
        tfLeftGap=number(31.8);tfRightGap=number(31.8);form(paper,3,"左 / 右 mm",tfLeftGap,tfRightGap);
        tfTopGap=number(25.4);tfBottomGap=number(25.4);form(paper,4,"上 / 下 mm",tfTopGap,tfBottomGap);
        tfLines=number(30);tfWordGap=number(1);form(paper,5,"行数 / 字距",tfLines,tfWordGap);tfWordGap.setToolTipText("字距（毫米）");
        tfFontSize=number(95);form(paper,6,"字号 %",tfFontSize,new JLabel());
        JButton applyLayout=new JButton("应用版式");applyLayout.setName("NoteApplyLayout");applyLayout.setToolTipText("重新排版，保留已经选定的字形");applyLayout.addActionListener(e->applyLayoutAsync());
        JButton rerandomize=new JButton("重抽未指定字形");rerandomize.setName("NoteRerandomize");rerandomize.setToolTipText("根据默认字体与配比重新选取未指定字的字形；保留手动指定字体，可撤销");rerandomize.addActionListener(e->rerandomizeAsync());
        JPanel layoutActions=new JPanel(new GridLayout(0,1,0,4));layoutActions.add(applyLayout);layoutActions.add(rerandomize);wide(paper,7,layoutActions);
        JPanel appearanceSection=section("字形与随机");
        appearancePanel=new NoteAppearancePanel(new NoteAppearancePanel.Host(){
            public NoteAppearance.Config current(boolean selected){return appearanceConfig(selected);}
            public boolean busy(){return isBusy();}
            public void notice(String text){message(text);}
            public void capture(boolean selected){finishEditorComposition();appearanceTargets=selected?selection.capture():null;appearanceRequestRevision=editorSession.revision();}
            public void apply(NoteAppearance.Config c,boolean selected){if(appearanceRequestValid())applyAppearanceTo(c,appearanceTargets);}
            public void rerandomize(boolean selected){if(appearanceRequestValid())rerandomizeAppearanceTo(appearanceTargets);}
        });wide(appearanceSection,0,appearancePanel);
        JPanel forbidden=section("禁写区");
        forbiddenZoneModel=new DefaultListModel<ForbiddenZoneItem>();forbiddenZoneList=new JList<ForbiddenZoneItem>(forbiddenZoneModel){
            public boolean getScrollableTracksViewportWidth(){return true;}
            public String getToolTipText(MouseEvent event){int index=locationToIndex(event.getPoint());Rectangle bounds=index<0?null:getCellBounds(index,index);return bounds!=null&&bounds.contains(event.getPoint())?getModel().getElementAt(index).label:null;}
        };forbiddenZoneList.setName("NoteForbiddenZoneList");forbiddenZoneList.setVisibleRowCount(4);forbiddenZoneList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);ToolTipManager.sharedInstance().registerComponent(forbiddenZoneList);
        forbiddenZoneList.addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&!refreshingForbiddenZones){ForbiddenZoneItem item=forbiddenZoneList.getSelectedValue();selectedForbiddenZone=item==null?null:item.id;updateForbiddenZoneButtons();if(notePanel!=null)notePanel.repaint();if(forbiddenZoneManager!=null&&forbiddenZoneManager.isDisplayable())forbiddenZoneManager.select(selectedForbiddenZone);}});
        JScrollPane forbiddenScroll=new JScrollPane(forbiddenZoneList,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER){
            public Dimension getPreferredSize(){
                Component cell=forbiddenZoneList.getCellRenderer().getListCellRendererComponent(forbiddenZoneList,new ForbiddenZoneItem("","禁写区",true,0),0,false,false);
                int row=Math.max(forbiddenZoneList.getFontMetrics(forbiddenZoneList.getFont()).getHeight()+4,cell.getPreferredSize().height);Insets border=getInsets();
                return new Dimension(1,row*forbiddenZoneList.getVisibleRowCount()+border.top+border.bottom);
            }
            public Dimension getMinimumSize(){return new Dimension(0,getPreferredSize().height);}
        };forbiddenScroll.setName("NoteForbiddenZoneScroll");wide(forbidden,0,forbiddenScroll);
        JButton addForbidden=new JButton("添加规则…");addForbidden.setName("NoteAddForbiddenZone");addForbidden.setToolTipText("添加多页或指定页禁写区规则");addForbidden.addActionListener(e->addForbiddenZone());
        editForbiddenZoneButton=new JButton("编辑选中…");editForbiddenZoneButton.setName("NoteEditForbiddenZone");editForbiddenZoneButton.addActionListener(e->editForbiddenZone());
        deleteForbiddenZoneButton=new JButton("删除选中");deleteForbiddenZoneButton.setName("NoteDeleteForbiddenZone");deleteForbiddenZoneButton.addActionListener(e->deleteForbiddenZone());
        wide(forbidden,1,buttons(addForbidden,editForbiddenZoneButton));
        JButton drawForbidden=new JButton("在预览中拖拽绘制");drawForbidden.setName("NoteDrawForbiddenZone");drawForbidden.addActionListener(e->startForbiddenZoneDrawing());
        JButton pageException=new JButton("本页例外…");pageException.setName("NoteForbiddenZoneException");pageException.setToolTipText("设置当前页停用的继承规则");pageException.addActionListener(e->editForbiddenZoneException());
        JButton addPageZone=new JButton("添加本页区域…");addPageZone.setName("NoteAddPageForbiddenZone");addPageZone.addActionListener(e->addPageForbiddenZone());
        wide(forbidden,2,drawForbidden);
        wide(forbidden,3,buttons(deleteForbiddenZoneButton,pageException));
        wide(forbidden,4,addPageZone);
        importForbiddenTemplateButton=new JButton("导入模板…");importForbiddenTemplateButton.setName("NoteImportForbiddenTemplate");importForbiddenTemplateButton.setToolTipText("导入禁写区规则模板");importForbiddenTemplateButton.addActionListener(e->importForbiddenZoneTemplate());
        exportForbiddenTemplateButton=new JButton("导出模板…");exportForbiddenTemplateButton.setName("NoteExportForbiddenTemplate");exportForbiddenTemplateButton.setToolTipText("导出禁写区规则模板");exportForbiddenTemplateButton.addActionListener(e->exportForbiddenZoneTemplate());
        wide(forbidden,5,buttons(importForbiddenTemplateButton,exportForbiddenTemplateButton));
        JButton manageForbidden=new JButton("管理全部规则…");manageForbidden.setName("NoteManageForbiddenZones");manageForbidden.addActionListener(e->showForbiddenZoneManager());wide(forbidden,6,manageForbidden);
        JTextArea forbiddenHint=new JTextArea("点选红框选中，双击编辑。\n更多设置见“管理全部规则”。",2,1){
            public Dimension getPreferredSize(){Dimension size=super.getPreferredSize();size.width=1;return size;}
            public Dimension getMinimumSize(){Dimension size=getPreferredSize();size.width=0;return size;}
        };forbiddenHint.setName("NoteForbiddenZoneHint");forbiddenHint.setEditable(false);forbiddenHint.setFocusable(false);forbiddenHint.setOpaque(false);forbiddenHint.setLineWrap(true);forbiddenHint.setWrapStyleWord(true);wide(forbidden,7,forbiddenHint);
        JPanel chosen=section("预览选字");btnPrePage=button("上一页");btnNextPage=button("下一页");tfCurPage=number(1);tfCurPage.setColumns(2);tfCurPage.addActionListener(this);labelMaxPage=new JLabel("/0");
        pageNavigation=new JPanel(new GridBagLayout());pageNavigation.setName("NotePageNavigation");btnPrePage.setName("NotePreviousPage");btnNextPage.setName("NoteNextPage");tfCurPage.setName("NoteCurrentPage");labelMaxPage.setName("NotePageTotal");
        cell(pageNavigation,0,0,1,0,btnPrePage);cell(pageNavigation,1,0,1,1,tfCurPage);cell(pageNavigation,2,0,1,0,labelMaxPage);cell(pageNavigation,3,0,1,0,btnNextPage);wide(chosen,0,pageNavigation);
        JToggleButton multi=new JToggleButton("多选：关");multi.setName("NoteMultiSelect");multi.setToolTipText("开启后连续点选，再点已选字可取消");multi.addActionListener(e->{selection.multiple=multi.isSelected();multi.setText(selection.multiple?"多选：开":"多选：关");});
        JButton line=new JButton("选整行");line.addActionListener(e->selectCurrentLine());JButton clear=new JButton("取消选区");clear.addActionListener(e->{selection.clear();selectionChanged();});wide(chosen,1,buttons(multi,line,clear));
        selectionLabel.setName("NoteSelectionCount");wide(chosen,2,selectionLabel);selectionFontLabel.setName("NoteSelectionFont");wide(chosen,3,selectionFontLabel);
        fontButton=new JButton("指定字体…");fontButton.setName("NoteAssignFont");fontButton.setToolTipText("只修改当前所选字的字体");fontButton.addActionListener(e->chooseSelectionFont());
        resetButton=new JButton("恢复默认字体");resetButton.setToolTipText("取消所选字的手动字体指定");resetButton.addActionListener(e->applyFont(null));wide(chosen,4,buttons(fontButton,resetButton));
        undoButton=new JButton("撤销");undoButton.setName("NoteUndo");undoButton.addActionListener(e->history(false));redoButton=new JButton("重做");redoButton.setName("NoteRedo");redoButton.addActionListener(e->history(true));wide(chosen,5,buttons(undoButton,redoButton));
        clearMissingButton=new JButton("清除红框…");clearMissingButton.setName("NoteClearMissing");clearMissingButton.setToolTipText("处理整篇文章所有缺字红框，可用指定字符或空格替代，也可删除；支持撤销");clearMissingButton.addActionListener(e->chooseMissingAction());clearMissingButton.setVisible(false);wide(chosen,6,clearMissingButton);
        resumeStatusLabel.setName("NoteResumeStatus");wide(chosen,7,resumeStatusLabel);
        resumeFromSelectionButton=new JButton("设为续写");resumeFromSelectionButton.setName("NoteSetResumeGlyph");resumeFromSelectionButton.setToolTipText("以当前选中的第一个可写字作为本页续写起点");resumeFromSelectionButton.addActionListener(e->setResumeFromSelection());
        clearResumeButton=new JButton("从头书写");clearResumeButton.setName("NoteClearResume");clearResumeButton.setToolTipText("清除续写进度与起点，从本页第 1 个字重新书写");clearResumeButton.addActionListener(e->clearResumeProgress());
        wide(chosen,8,buttons(resumeFromSelectionButton,clearResumeButton));
        resumeInterruptedButton=new JButton("写中断字");resumeInterruptedButton.setName("NoteResumeInterruptedGlyph");resumeInterruptedButton.setToolTipText("将续写起点设回断线或中断时正在写的字");resumeInterruptedButton.addActionListener(e->selectInterruptedResumeGlyph());
        resumeNextButton=new JButton("写下一字");resumeNextButton.setName("NoteResumeNextGlyph");resumeNextButton.setToolTipText("若纸面上中断的字已写完，将续写起点顺延到下一个字");resumeNextButton.addActionListener(e->selectNextResumeGlyph());
        wide(chosen,9,buttons(resumeInterruptedButton,resumeNextButton));
        randomCopies=new JCheckBox("每份重新随机");randomCopies.setToolTipText("启用新随机误差时，每份只重抽误差、保留字体；旧文章沿用原随机方式");
        status.setName("NoteEditStatus");status.setAlignmentX(LEFT_ALIGNMENT);status.setEditable(false);status.setLineWrap(true);status.setWrapStyleWord(true);status.setOpaque(false);status.setFont(UIManager.getFont("Label.font"));status.setBorder(BorderFactory.createEmptyBorder(4,4,2,4));
        JScrollPane statusPane=new JScrollPane(status,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER){public Dimension getPreferredSize(){Dimension d=super.getPreferredSize();d.width=1;d.height=status.getFontMetrics(status.getFont()).getHeight()*2+status.getInsets().top+status.getInsets().bottom+4;return d;}public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}};statusPane.setBorder(BorderFactory.createEmptyBorder());statusPane.setAlignmentX(LEFT_ALIGNMENT);add(statusPane);
        editToggle=new JToggleButton("进入编辑模式");editToggle.setName("NoteEditMode");editToggle.addActionListener(e->setEditing(editToggle.isSelected()));
        JButton editorSettings=new JButton("编辑设置…");editorSettings.setName("NoteEditorSettings");editorSettings.addActionListener(e->showEditorSettings());wide(article,3,buttons(editToggle,editorSettings));
        layoutTimer.setRepeats(false);recoveryTimer.setRepeats(false);
        binding=true;
        try(Reader in=Files.newBufferedReader(getPageSaveFile().toPath(),StandardCharsets.UTF_8)) {PageInfo[] pages=new Gson().fromJson(in,PageInfo[].class);if(pages!=null)for(PageInfo p:pages)if(p!=null&&p.name!=null)model.addElement(p);}catch(Exception ignored){}
        if(model.getSize()==0){PageInfo p=new PageInfo("默认",210,297,31.8f,31.8f,25.4f,25.4f,30,95,true);p.space=1;model.addElement(p);}
        pageInfo=(PageInfo)model.getElementAt(0);cbSettings.setSelectedIndex(0);binding=false;setPageInfo(pageInfo);applyFonts();
        document.page=copyPage(pageInfo);editorSession.reset(document);document=editorSession.mutableDocument();
        contentSet=document.format;content=new StringBuffer();supportFiles=new String[]{"kjnote","txt","doc","docx"};savedDocument=document.encode();captureInputFonts();refreshEditGeometry();selectionChanged();
        recoveryStartRevision=editorSession.revision();SwingUtilities.invokeLater(this::restoreRecovery);
    }
    private JPanel section(String title){
        JPanel p=new JPanel(new GridBagLayout()){public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}};p.setAlignmentX(LEFT_ALIGNMENT);
        String name="字形与随机".equals(title)?"NoteAppearanceSection":"禁写区".equals(title)?"NoteForbiddenSection":"预览选字".equals(title)?"NoteSelectionSection":null;
        if(name!=null)add(new NoteCollapsibleSection(title,name,p));else{p.setBorder(BorderFactory.createTitledBorder(title));add(p);}return p;
    }
    private static JPanel buttons(AbstractButton... buttons){JPanel p=new JPanel(new GridLayout(1,buttons.length,4,0));for(AbstractButton b:buttons){b.setMargin(new Insets(3,5,3,5));p.add(b);}return p;}
    private static void cell(JPanel p,int x,int y,int span,double weight,Component c){GridBagConstraints g=new GridBagConstraints();g.gridx=x;g.gridy=y;g.gridwidth=span;g.weightx=weight;g.fill=GridBagConstraints.HORIZONTAL;g.anchor=GridBagConstraints.WEST;g.insets=new Insets(2,3,2,3);p.add(c,g);}
    private static void wide(JPanel p,int row,Component c){cell(p,0,row,3,1,c);}
    private static void form(JPanel p,int row,String label,Component a,Component b){cell(p,0,row,1,0,new JLabel(label));cell(p,1,row,1,1,a);cell(p,2,row,1,1,b);}
    public Dimension getPreferredSize(){Dimension d=super.getPreferredSize();d.width=Math.max(d.width,Math.max(250,getFontMetrics(getFont()).charWidth('字')*18+16));return d;}
    private JButton button(String text){JButton b=new JButton(text);b.addActionListener(this);return b;}
    private JFormattedTextField number(double value){JFormattedTextField f=new JFormattedTextField(java.text.NumberFormat.getNumberInstance());f.setColumns(4);f.setValue(value);return f;}
    private float value(JFormattedTextField f) throws Exception {f.commitEdit();Object n=f.getValue();float v=n instanceof Number?((Number)n).floatValue():Float.parseFloat(f.getText());if(!Float.isFinite(v))throw new IOException("数值必须为有限值");return v;}
    private static PageInfo copyPage(PageInfo p){return NoteDocument.copyPage(p);}
    private PageInfo readPage() throws Exception {
        PageInfo p=copyPage(pageInfo);p.name=String.valueOf(cbSettings.getSelectedItem());p.pageWidth=value(tfWidth);p.pageHeight=value(tfHeight);p.leftGap=value(tfLeftGap);p.rightGap=value(tfRightGap);p.topGap=value(tfTopGap);p.bottomGap=value(tfBottomGap);
        float lines=value(tfLines);if(lines!=Math.rint(lines)||lines<=0)throw new IOException("行数必须为正整数");p.lineNum=(int)lines;p.space=value(tfWordGap);p.fontSize=value(tfFontSize);if(p.space<0)throw new IOException("字距不能为负数");return p;
    }
    private void save() {
        try {PageInfo p=readPage();int found=-1;for(int i=0;i<model.getSize();i++)if(p.name.equals(((PageInfo)model.getElementAt(i)).name))found=i;
            List<PageInfo> pages=settings();if(found>=0)pages.set(found,p);else pages.add(p);
            writeSettings(pages);
            binding=true;if(found>=0){model.removeElementAt(found);model.insertElementAt(p,found);}else model.addElement(p);cbSettings.setSelectedItem(p);binding=false;pageInfo=p;message("页面模板已保存；逐字字体随文章保存。");
        }catch(Exception error){binding=false;failure(error);}
    }
    private void delete(){int index=cbSettings.getSelectedIndex();if(index>=0&&model.getSize()>1)try{List<PageInfo> pages=settings();pages.remove(index);writeSettings(pages);model.removeElementAt(index);}catch(Exception error){failure(error);}}
    public void saveSet(){super.saveSet();saveSettings();}
    private List<PageInfo> settings(){List<PageInfo> pages=new ArrayList<PageInfo>();for(int i=0;i<model.getSize();i++)pages.add((PageInfo)model.getElementAt(i));return pages;}
    private void saveSettings(){try{writeSettings(settings());}catch(Exception e){failure(e);}}
    private void writeSettings(List<PageInfo> pages)throws IOException{
        Path file=getPageSaveFile().toPath().toAbsolutePath();Files.createDirectories(file.getParent());
        Path temporary=Files.createTempFile(file.getParent(),"page_settings-",".tmp");
        try{
            Files.write(temporary,new Gson().toJson(pages).getBytes(StandardCharsets.UTF_8));
            try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
    private File getPageSaveFile(){return new File(L.b(),"page_settings.json");}
    private void setPageInfo(PageInfo p){if(p==null)return;pageInfo=copyPage(p);binding=true;tfWidth.setValue(p.pageWidth);tfHeight.setValue(p.pageHeight);tfLeftGap.setValue(p.leftGap);tfRightGap.setValue(p.rightGap);tfTopGap.setValue(p.topGap);tfBottomGap.setValue(p.bottomGap);tfLines.setValue(p.lineNum);tfWordGap.setValue(p.space);tfFontSize.setValue(p.fontSize);Object template=p.name;for(int i=0;i<model.getSize();i++)if(java.util.Objects.equals(((PageInfo)model.getElementAt(i)).name,p.name)){template=model.getElementAt(i);break;}cbSettings.setSelectedItem(template);binding=false;if(pageInfo.fontPath==null)applyFonts();}
    private void applyFonts(){FontsDialogData f=(FontsDialogData)Storage.getStore(FontsDialogData.class);pageInfo.fontPath=f.fontsName==null?null:f.fontsName.clone();pageInfo.fontChance=f.chance==null?null:f.chance.clone();captureInputFonts();message("默认字体已载入；点击“更新”用于未指定的字。");}
    private synchronized NoteFonts fonts() throws Exception {if(catalogue==null)catalogue=NoteFonts.installed();else catalogue=catalogue.updated();return catalogue;}
    private boolean fontRefreshRunning;
    private void refreshFonts(){if(fontRefreshRunning){message("字库刷新已在后台进行。");return;}if(isBusy()){message("请等待排版完成后刷新字库。");return;}fontRefreshRunning=true;final NoteFonts previous=catalogue;message("正在后台扫描字库，文章与已选字形保持不变…");new SwingWorker<NoteFonts,Void>(){protected NoteFonts doInBackground()throws Exception{return previous==null?NoteFonts.installed():previous.refresh();}protected void done(){fontRefreshRunning=false;try{catalogue=get();message("字库已刷新，共 "+catalogue.entries().size()+" 个字体。"+(catalogue.errors().isEmpty()?"":"无法读取："+catalogue.errors()));}catch(Exception e){failure(e);}}}.execute();}
    private void message(String text){if(layout!=null)text+=layout.notice();status.setText(text);status.setCaretPosition(0);status.setToolTipText("<html>"+escape(text)+"</html>");}
    private static String escape(String t){return t==null?"":t.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\n","<br>");}
    private void reportFailure(Throwable error){while(error.getCause()!=null)error=error.getCause();lastFailure=error.getMessage()==null?error.toString():error.getMessage();message(lastFailure);System.err.println("NOTE_EDIT_ERROR "+lastFailure);}
    public void processAsync(){rerandomizeAsync();}
    public void applyLayoutAsync(){
        if(!SwingUtilities.isEventDispatchThread()){
            SwingUtilities.invokeLater(this::applyLayoutAsync);return;
        }
        if(pending!=null||loading!=null){message("请等待当前文章修改完成。");return;}
        try{
            finishEditorComposition();
            NoteEditSession.Transaction transaction=editorSession.capture();
            NoteDocument candidate=transaction.document();
            candidate.page=readPage();validatePageCandidate(candidate);
            editorSession.commit(transaction,candidate,NoteEditSession.Kind.OTHER);
            // Inline layout consumes a snapshot and rejects results from older revisions.
            changed();if(notePanel!=null)notePanel.syncEditor();
        }catch(Exception error){failure(error);}
    }
    public void rerandomizeAsync(){
        if(!SwingUtilities.isEventDispatchThread()){
            SwingUtilities.invokeLater(this::rerandomizeAsync);return;
        }
        if(pending!=null||loading!=null){message("请等待当前文章修改完成。");return;}
        try{
            finishEditorComposition();captureInputFonts();
            NoteEditSession.Transaction transaction=editorSession.capture();
            NoteDocument candidate=transaction.document();candidate.page=readPage();
            candidate.shapes=NoteDocument.ShapeSettings.capture((FontsSetData)Storage.getStore(FontsSetData.class));
            candidate.pool.clear();
            candidate.chances=candidate.page.fontChance==null?null:candidate.page.fontChance.clone();
            for(NoteDocument.Atom atom:candidate.atoms)if(atom.override==null){
                atom.inputFonts=candidate.page.fontPath==null?null:candidate.page.fontPath.clone();
                atom.inputChances=candidate.page.fontChance==null?null:candidate.page.fontChance.clone();
                atom.inputGeneration=candidate.generation+1;
            }
            candidate.rerandomize();validatePageCandidate(candidate);
            editorSession.commit(transaction,candidate,NoteEditSession.Kind.UPDATE);
            changed();if(notePanel!=null)notePanel.syncEditor();
        }catch(Exception error){failure(error);}
    }
    public void process(){processAsync();}
    private SwingWorker<Prepared,Void> submit(final NoteDocument candidate,final boolean history,final boolean newDocument){return submit(candidate,history,newDocument,null);}
    private SwingWorker<Prepared,Void> submit(final NoteDocument candidate,final boolean history,final boolean newDocument,final Runnable success) {return submit(candidate,history,newDocument,success,null);}
    private SwingWorker<Prepared,Void> submit(final NoteDocument candidate,final boolean history,final boolean newDocument,final Runnable success,final Runnable onFailure) {
        return submit(candidate,history,newDocument,success,onFailure,null);
    }
    private SwingWorker<Prepared,Void> submit(final NoteDocument candidate,final boolean history,final boolean newDocument,final Runnable success,final Runnable onFailure,final java.util.function.Consumer<Prepared> validator) {
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("文章提交必须在EDT发起");
        if(isBusy()){message("请等待当前文章修改完成。");return null;}
        if(!newDocument){long firstNew=editorSession.mutableDocument().nextId;String[] input=editorSession.inputFonts();int[] chances=editorSession.inputChances();for(NoteDocument.Atom atom:candidate.atoms)if(atom.id>=firstNew&&atom.inputFonts==null&&atom.inputChances==null){atom.inputFonts=input.clone();atom.inputChances=chances.clone();atom.inputGeneration=candidate.generation;}}
        final long ticket=++request;lastFailure=null;message("正在校验字体并重新排版…");
        final float dpi=Settings.getInst().dpi;
        pending=new SwingWorker<Prepared,Void>() {
            protected Prepared doInBackground() throws Exception {
                NoteFonts f=fonts();
                if(candidate.page.fontPath==null){candidate.page.fontPath=new String[candidate.pool.size()];for(int i=0;i<candidate.pool.size();i++)candidate.page.fontPath[i]=new File(candidate.pool.get(i).relativePath).getName();candidate.page.fontChance=candidate.chances;}
                Prepared prepared=new Prepared(candidate,new NoteLayoutEngine(f,dpi).build(candidate));if(validator!=null)validator.accept(prepared);return prepared;
            }
            protected void done() {
                if(ticket!=request)return;
                boolean committed=false;
                try {Prepared p=get();long anchor=!layout.pages.isEmpty()&&!layout.pages.get(curPage).glyphs.isEmpty()?layout.pages.get(curPage).glyphs.get(0).id:-1;

                    if(newDocument)idHighWater=p.document.nextId;else idHighWater=Math.max(idHighWater,p.document.nextId);p.document.nextId=idHighWater;
                    document=p.document;layout=p.layout;
                    if(newDocument)editorSession.reset(document);else{NoteEditSession.Transaction t=editorSession.capture();editorSession.commit(t,document,NoteEditSession.Kind.OTHER);}
                    document=editorSession.mutableDocument();layoutRevision=editorSession.revision();inlineError=null;displayLayout=layout;editGeometry=new NoteEditGeometry(layout,36);
                    if(newDocument){documentId=UUID.randomUUID().toString();writeProgress=null;curPage=0;selection.clear();savedDocument=document.encode();editorSession.markSaved();setPageInfo(document.page);restoreInputDefaults();}else selection.retain(document);
                    content=new StringBuffer(document.text);contentSet=document.format;pageDatas.clear();for(NoteLayout.Page page:layout.pages)pageDatas.add(new com.kenjoy.kenjoycnc.l.f(0,page.paths,Collections.emptyList()));
                    if(anchor>=0&&!newDocument){int index=layout.pageOf(anchor);if(index>=0)curPage=index;}
                    reconcileWriteProgress(false);
                    changePage();if(notePanel!=null)notePanel.syncEditor();message("已更新。"+(isDirty()?"文章有未保存修改。":""));committed=true;if(success!=null)success.run();recoveryTimer.restart();
                }catch(CancellationException ignored){}catch(Exception error){failure(error);if(!committed&&onFailure!=null)onFailure.run();}finally{if(ticket==request){pending=null;selectionChanged();}}
            }
        };selectionChanged();pending.execute();return pending;
    }
    private void changePage(){boolean previous=changingPage;changingPage=true;try{int count=geometry().layout.pages.size();curPage=Math.max(0,Math.min(curPage,count-1));
        if(notePanel!=null){NoteLayout.Page page=count==0?new NoteLayout.Page():geometry().layout.pages.get(curPage);notePanel.bind(page,selection,this::selectionChanged,this::chooseSelectionFont,()->applyFont(null),document.page.pageWidth>0?document.page:pageInfo);notePanel.refreshEditor();}
        labelMaxPage.setText("/"+count);tfCurPage.setValue(count==0?0:curPage+1);btnPrePage.setEnabled(curPage>0);btnNextPage.setEnabled(curPage+1<count);refreshForbiddenZoneList();selectionChanged();}finally{changingPage=previous;}}
    private void refreshForbiddenZoneList(){
        if(forbiddenZoneModel==null)return;refreshingForbiddenZones=true;try{forbiddenZoneModel.clear();NoteForbiddenZones zones=document.forbiddenZones;if(zones!=null&&zones.rules!=null)for(NoteForbiddenZones.Rule rule:zones.rules)if(rule!=null)
            forbiddenZoneModel.addElement(new ForbiddenZoneItem(rule.id,rule.toString()+"  ·  "+String.format(Locale.ROOT,"%.1f, %.1f / %.1f × %.1f mm",rule.x,rule.y,rule.width,rule.height),true,0));
        if(zones!=null){NoteForbiddenZones.PageException exception=zones.exception(curPage+1);if(exception!=null&&exception.additionalZones!=null)for(NoteForbiddenZones.Rect rect:exception.additionalZones)if(rect!=null)
            forbiddenZoneModel.addElement(new ForbiddenZoneItem(rect.id,"本页额外区域  ·  "+rect.name+"  ·  "+String.format(Locale.ROOT,"%.1f × %.1f mm",rect.width,rect.height),false,curPage+1));}
        if(selectedForbiddenZone!=null){if(findForbiddenZoneItem(selectedForbiddenZone)==null)selectedForbiddenZone=null;else for(int index=0;index<forbiddenZoneModel.size();index++)if(Objects.equals(forbiddenZoneModel.get(index).id,selectedForbiddenZone)){forbiddenZoneList.setSelectedIndex(index);break;}}
        }finally{refreshingForbiddenZones=false;}updateForbiddenZoneButtons();if(forbiddenZoneManager!=null&&forbiddenZoneManager.isDisplayable())forbiddenZoneManager.refresh();
    }
    private void updateForbiddenZoneButtons(){
        if(editForbiddenZoneButton==null||deleteForbiddenZoneButton==null)return;ForbiddenZoneItem item=forbiddenZoneList==null?null:forbiddenZoneList.getSelectedValue();
        editForbiddenZoneButton.setText(item==null?"编辑选中…":item.rule?"编辑规则…":"编辑本页…");editForbiddenZoneButton.setEnabled(!isBusy()&&item!=null);deleteForbiddenZoneButton.setEnabled(!isBusy()&&item!=null);
        if(forbiddenZoneManager!=null&&forbiddenZoneManager.isDisplayable())forbiddenZoneManager.updateState();
    }
    private ForbiddenZoneItem findForbiddenZoneItem(String id){
        if(id==null||document==null||document.forbiddenZones==null)return null;
        for(NoteForbiddenZones.Rule rule:document.forbiddenZones.rules)if(Objects.equals(rule.id,id))return new ForbiddenZoneItem(id,rule.name,true,0);
        for(NoteForbiddenZones.PageException exception:document.forbiddenZones.exceptions)for(NoteForbiddenZones.Rect rect:exception.additionalZones)if(Objects.equals(rect.id,id))return new ForbiddenZoneItem(id,rect.name,false,exception.page);
        return null;
    }
    public String selectedForbiddenZoneId(){return selectedForbiddenZone;}
    public void selectForbiddenZoneFromCanvas(int pageIndex,String id){
        selectedForbiddenZone=id==null||findForbiddenZoneItem(id)==null?null:id;
        if(selectedForbiddenZone!=null&&pageIndex>=0&&pageIndex<geometry().layout.pages.size())activatePreviewPage(pageIndex);
        else if(forbiddenZoneList!=null){refreshForbiddenZoneList();if(selectedForbiddenZone==null)forbiddenZoneList.clearSelection();}
        if(notePanel!=null)notePanel.repaint();
        if(forbiddenZoneManager!=null&&forbiddenZoneManager.isDisplayable())forbiddenZoneManager.select(selectedForbiddenZone);
    }
    public void editForbiddenZoneFromCanvas(int pageIndex,String id){selectForbiddenZoneFromCanvas(pageIndex,id);editForbiddenZone(findForbiddenZoneItem(id));}
    private void previewForbiddenManagerRow(NoteForbiddenZoneManager.Row row){
        if(row.kind==NoteForbiddenZoneManager.Kind.EXCEPTION){selectForbiddenZoneFromCanvas(-1,null);if(row.page<=geometry().layout.pages.size())activatePreviewPage(row.page-1);else message("第 "+row.page+" 页尚未生成；例外规则仍可在管理窗口编辑。");return;}
        int target=-1,count=geometry().layout.pages.size();
        if(row.page>0){if(row.page<=count)target=row.page-1;}
        else{
            for(NoteForbiddenZones.ResolvedZone zone:document.forbiddenZones.resolve(curPage+1,document.page.pageWidth,document.page.pageHeight))if(Objects.equals(zone.id,row.id))target=curPage;
            if(target<0)for(int page=0;page<count&&target<0;page++)for(NoteForbiddenZones.ResolvedZone zone:document.forbiddenZones.resolve(page+1,document.page.pageWidth,document.page.pageHeight))if(Objects.equals(zone.id,row.id)){target=page;break;}
        }
        if(notePanel!=null&&notePanel.isEditing())setEditing(false);selectForbiddenZoneFromCanvas(target,row.id);
        if(target<0){message("此区域当前未在已有页面生效，规则仍可编辑。");return;}
        if(notePanel!=null)for(NoteForbiddenZones.ResolvedZone zone:document.forbiddenZones.resolve(target+1,document.page.pageWidth,document.page.pageHeight))if(Objects.equals(zone.id,row.id)){
            float scale=geometry().layout.pageWidth/document.page.pageWidth;Point center=notePanel.previewPoint(target,(zone.x+zone.width/2)*scale,(zone.y+zone.height/2)*scale);notePanel.scrollRectToVisible(new Rectangle(center.x-30,center.y-30,60,60));break;
        }
    }
    public void showForbiddenZoneManager(){
        if(forbiddenZoneManager==null||!forbiddenZoneManager.isDisplayable())forbiddenZoneManager=new NoteForbiddenZoneManager(this,new NoteForbiddenZoneManager.Host(){
            public NoteDocument document(){return NoteTab.this.document();}public boolean busy(){return isBusy();}public String selectedId(){return selectedForbiddenZone;}
            public void preview(NoteForbiddenZoneManager.Row row){previewForbiddenManagerRow(row);}
            public void edit(NoteForbiddenZoneManager.Row row){if(row.kind==NoteForbiddenZoneManager.Kind.EXCEPTION)editForbiddenZoneException(row.page);else editForbiddenZone(findForbiddenZoneItem(row.id));}
            public void delete(NoteForbiddenZoneManager.Row row){
                if(row.kind!=NoteForbiddenZoneManager.Kind.EXCEPTION){deleteForbiddenZone(findForbiddenZoneItem(row.id));return;}
                if(isBusy()||JOptionPane.showConfirmDialog(NoteTab.this,"清除第 "+row.page+" 页停用的继承规则？本页额外区域会保留。","删除单页例外",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
                NoteEditSession.Transaction tx=editorSession.capture();NoteDocument candidate=tx.document();candidate.forbiddenZones.setException(row.page,Collections.<String>emptyList());commitForbiddenZones(tx,candidate,"单页例外已清除；正在重新排版。");
            }
            public void addRule(){addForbiddenZone();}public void addPageZone(){addPageForbiddenZone();}public void pageException(){editForbiddenZoneException();}
        });
        forbiddenZoneManager.show();
    }
    private static JFormattedTextField zoneNumber(float initial){JFormattedTextField field=new JFormattedTextField(java.text.NumberFormat.getNumberInstance(Locale.ROOT));field.setColumns(7);field.setValue(initial);return field;}
    private List<Integer> parseForbiddenPages(String value)throws IOException{
        List<Integer> pages=new ArrayList<Integer>();Set<Integer> seen=new HashSet<Integer>();String trimmed=value==null?"":value.trim();if(trimmed.isEmpty())throw new IOException("请填写自定义页面，例如 1,3-5");
        for(String token:trimmed.split("[,，;；\\s]+")){if(token.isEmpty())continue;int dash=token.indexOf('-');int first,last;
            try{if(dash<0)first=last=Integer.parseInt(token);else{if(dash==0||dash==token.length()-1||token.indexOf('-',dash+1)>=0)throw new NumberFormatException();first=Integer.parseInt(token.substring(0,dash));last=Integer.parseInt(token.substring(dash+1));}}catch(NumberFormatException invalid){throw new IOException("自定义页面格式无效："+token);}
            if(first<=0||last<first||last>NoteForbiddenZones.MAX_PAGE_NUMBER||last-first+1>NoteForbiddenZones.MAX_CUSTOM_PAGES)throw new IOException("自定义页面范围无效："+token);
            for(int page=first;page<=last;page++){if(!seen.add(page))throw new IOException("自定义页面重复："+page);pages.add(page);if(pages.size()>NoteForbiddenZones.MAX_CUSTOM_PAGES)throw new IOException("自定义页面总数过多");}
        }
        return pages;
    }
    private NoteForbiddenZones.Rule forbiddenRuleDialog(NoteForbiddenZones.Rule original,float x,float y,float width,float height){
        NoteForbiddenZones.Rule rule=original==null?new NoteForbiddenZones.Rule("禁写区",x,y,width,height,NoteForbiddenZones.DEFAULT_CLEARANCE_MM):original.copy();
        JTextField name=new JTextField(rule.name,14);JFormattedTextField fx=zoneNumber(rule.x),fy=zoneNumber(rule.y),fw=zoneNumber(rule.width),fh=zoneNumber(rule.height),clearance=zoneNumber(rule.clearanceMm);
        JComboBox<NoteForbiddenZones.PageMode> mode=new JComboBox<NoteForbiddenZones.PageMode>(NoteForbiddenZones.PageMode.values());mode.setSelectedItem(rule.pageMode==null?NoteForbiddenZones.PageMode.CUSTOM:rule.pageMode);
        JTextField pages=new JTextField(rule.customPages==null||rule.customPages.isEmpty()?String.valueOf(curPage+1):joinPages(rule.customPages),14);JCheckBox mirror=new JCheckBox("偶数页水平镜像",rule.mirrorEven);
        JLabel pagesLabel=new JLabel();pagesLabel.setName("NoteForbiddenPagesLabel");mode.setName("NoteForbiddenPageMode");pages.setName("NoteForbiddenPages");
        JTextArea pageHint=new JTextArea(2,1){public Dimension getPreferredSize(){Dimension size=super.getPreferredSize();size.width=1;return size;}};
        pageHint.setName("NoteForbiddenPageHint");pageHint.setEditable(false);pageHint.setFocusable(false);pageHint.setOpaque(false);pageHint.setLineWrap(true);pageHint.setWrapStyleWord(true);
        final String[] customText={pages.getText()};final NoteForbiddenZones.PageMode[] previousMode={null};
        Runnable updatePages=()->{
            if(previousMode[0]==NoteForbiddenZones.PageMode.CUSTOM)customText[0]=pages.getText();
            NoteForbiddenZones.PageMode selected=(NoteForbiddenZones.PageMode)mode.getSelectedItem();boolean custom=selected==NoteForbiddenZones.PageMode.CUSTOM;
            pages.setEditable(custom);pages.setFocusable(custom);pagesLabel.setText(custom?"指定页码":"页码预览");
            pages.setText(custom?customText[0]:selected==NoteForbiddenZones.PageMode.ODD?"1；3；5；7；9；…":selected==NoteForbiddenZones.PageMode.EVEN?"2；4；6；8；10；…":"1；2；3；4；5；…");
            pageHint.setText(custom?"填写示例：1；3；5 或 1-5；8；10-12\n支持分号、逗号或空格分隔；连续页使用短横线“-”。":selected+"自动生效，上方只读显示页码示例。\n如需自行填写页码，请选择“指定页面”。");
            pages.setToolTipText(custom?"例如 1；3；5 或 1-5；8；10-12":pages.getText());previousMode[0]=selected;
        };mode.addActionListener(e->updatePages.run());updatePages.run();
        JPanel fields=new JPanel(new GridLayout(0,2,6,4));fields.add(new JLabel("区域名称"));fields.add(name);fields.add(new JLabel("左上角 X / Y mm"));fields.add(pair(fx,fy));fields.add(new JLabel("宽 / 高 mm"));fields.add(pair(fw,fh));fields.add(new JLabel("安全边距 mm"));fields.add(clearance);fields.add(new JLabel("应用页面"));fields.add(mode);fields.add(pagesLabel);fields.add(pages);fields.add(new JLabel("偶数页处理"));fields.add(mirror);
        JPanel content=new JPanel(new BorderLayout(0,8));content.add(fields,BorderLayout.CENTER);content.add(pageHint,BorderLayout.SOUTH);
        JOptionPane options=new JOptionPane(content,JOptionPane.PLAIN_MESSAGE,JOptionPane.OK_CANCEL_OPTION);
        final JDialog dialog=options.createDialog(this,original==null?"添加禁写区规则":"编辑禁写区规则");dialog.setName("NoteForbiddenRuleDialog");
        dialog.addWindowListener(new WindowAdapter(){public void windowOpened(WindowEvent event){dialog.pack();dialog.setLocationRelativeTo(NoteTab.this);}});
        try{
            for(;;){
                options.setValue(JOptionPane.UNINITIALIZED_VALUE);
                dialog.setVisible(true);
                if(!Integer.valueOf(JOptionPane.OK_OPTION).equals(options.getValue()))return null;
                try{
                    String label=name.getText()==null?"":name.getText().trim();if(label.isEmpty()||label.length()>80)throw new IOException("区域名称不能为空且不能超过 80 个字符");
                    rule.name=label;rule.x=value(fx);rule.y=value(fy);rule.width=value(fw);rule.height=value(fh);rule.clearanceMm=value(clearance);rule.pageMode=(NoteForbiddenZones.PageMode)mode.getSelectedItem();rule.mirrorEven=mirror.isSelected();
                    rule.customPages=rule.pageMode==NoteForbiddenZones.PageMode.CUSTOM?parseForbiddenPages(pages.getText()):new ArrayList<Integer>();
                    NoteForbiddenZones candidate=document.forbiddenZones.copy();boolean replaced=false;
                    for(int index=0;index<candidate.rules.size();index++)if(Objects.equals(candidate.rules.get(index).id,rule.id)){candidate.rules.set(index,rule);replaced=true;break;}
                    if(!replaced)candidate.rules.add(rule);
                    candidate.validate(document.page.pageWidth,document.page.pageHeight);
                    return rule;
                }catch(Exception error){
                    reportFailure(error);
                    JOptionPane.showMessageDialog(dialog,lastFailure,"禁写区规则",JOptionPane.ERROR_MESSAGE);
                }
            }
        }finally{dialog.dispose();}
    }
    private static JPanel pair(Component a,Component b){JPanel p=new JPanel(new GridLayout(1,2,4,0));p.add(a);p.add(b);return p;}
    private static String joinPages(List<Integer> values){StringBuilder text=new StringBuilder();for(Integer page:values){if(text.length()>0)text.append(',');text.append(page);}return text.toString();}
    private void commitForbiddenZones(NoteEditSession.Transaction transaction,NoteDocument candidate,String notice){
        try{candidate.forbiddenZones.validate(candidate.page.pageWidth,candidate.page.pageHeight);if(editorSession.commit(transaction,candidate,NoteEditSession.Kind.OTHER)){changed();if(notePanel!=null)notePanel.syncEditor();refreshForbiddenZoneList();message(notice);}else message("禁写区设置没有变化。");}
        catch(Exception error){failure(error);}
    }
    private void addForbiddenZone(){
        if(isBusy())return;NoteForbiddenZones.Rule rule=forbiddenRuleDialog(null,10,10,30,20);if(rule==null)return;
        NoteEditSession.Transaction transaction=editorSession.capture();NoteDocument candidate=transaction.document();candidate.forbiddenZones.rules.add(rule);commitForbiddenZones(transaction,candidate,"禁写区规则已添加；正在重新排版。");
    }
    private void editForbiddenZone(){
        editForbiddenZone(forbiddenZoneList==null?null:forbiddenZoneList.getSelectedValue());
    }
    private void editForbiddenZone(ForbiddenZoneItem item){
        if(isBusy()||item==null)return;if(!item.rule){editForbiddenPageZone(item);return;}
        NoteForbiddenZones.Rule existing=null;for(NoteForbiddenZones.Rule rule:document.forbiddenZones.rules)if(rule!=null&&Objects.equals(rule.id,item.id)){existing=rule;break;}if(existing==null)return;
        NoteForbiddenZones.Rule edited=forbiddenRuleDialog(existing,0,0,0,0);if(edited==null)return;NoteEditSession.Transaction transaction=editorSession.capture();NoteDocument candidate=transaction.document();
        for(int i=0;i<candidate.forbiddenZones.rules.size();i++){NoteForbiddenZones.Rule rule=candidate.forbiddenZones.rules.get(i);if(rule!=null&&Objects.equals(rule.id,item.id)){candidate.forbiddenZones.rules.set(i,edited);break;}}
        commitForbiddenZones(transaction,candidate,"禁写区规则已更新；正在重新排版。");
    }
    private void editForbiddenPageZone(ForbiddenZoneItem item){
        if(item==null||item.rule||item.page<=0)return;NoteForbiddenZones.PageException existingException=document.forbiddenZones.exception(item.page);if(existingException==null||existingException.additionalZones==null)return;
        NoteForbiddenZones.Rect existing=null;for(NoteForbiddenZones.Rect rect:existingException.additionalZones)if(rect!=null&&Objects.equals(rect.id,item.id)){existing=rect;break;}if(existing==null)return;
        NoteForbiddenZones.Rect edited=forbiddenRectDialog(existing,0,0,0,0);if(edited==null)return;NoteEditSession.Transaction transaction=editorSession.capture();NoteDocument candidate=transaction.document();NoteForbiddenZones.PageException exception=candidate.forbiddenZones.exception(item.page);if(exception==null||exception.additionalZones==null)return;
        for(int index=0;index<exception.additionalZones.size();index++){NoteForbiddenZones.Rect rect=exception.additionalZones.get(index);if(rect!=null&&Objects.equals(rect.id,item.id)){exception.additionalZones.set(index,edited);break;}}
        commitForbiddenZones(transaction,candidate,"本页额外禁写区已更新；正在重新排版。");
    }
    private void deleteForbiddenZone(){
        deleteForbiddenZone(forbiddenZoneList==null?null:forbiddenZoneList.getSelectedValue());
    }
    private void deleteForbiddenZone(ForbiddenZoneItem item){
        if(isBusy()||item==null)return;
        if(JOptionPane.showConfirmDialog(this,"删除所选禁写区？","删除禁写区",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        NoteEditSession.Transaction transaction=editorSession.capture();NoteDocument candidate=transaction.document();if(item.rule)candidate.forbiddenZones.removeRule(item.id);else candidate.forbiddenZones.removeExceptionZone(item.page,item.id);
        commitForbiddenZones(transaction,candidate,"禁写区已删除；正在重新排版。");
    }
    private void addPageForbiddenZone(){
        if(isBusy())return;NoteForbiddenZones.Rect rect=forbiddenRectDialog(null,10,10,30,20);if(rect==null)return;NoteEditSession.Transaction transaction=editorSession.capture();NoteDocument candidate=transaction.document();candidate.forbiddenZones.addExceptionZone(curPage+1,rect);commitForbiddenZones(transaction,candidate,"已添加第 "+(curPage+1)+" 页的额外禁写区；正在重新排版。");
    }
    private NoteForbiddenZones.Rect forbiddenRectDialog(NoteForbiddenZones.Rect original,float x,float y,float width,float height){
        NoteForbiddenZones.Rect rect=original==null?new NoteForbiddenZones.Rect("本页禁写区",x,y,width,height,NoteForbiddenZones.DEFAULT_CLEARANCE_MM):original.copy();
        JTextField name=new JTextField(rect.name,14);JFormattedTextField fx=zoneNumber(rect.x),fy=zoneNumber(rect.y),fw=zoneNumber(rect.width),fh=zoneNumber(rect.height),clearance=zoneNumber(rect.clearanceMm);
        JPanel fields=new JPanel(new GridLayout(0,2,6,4));fields.add(new JLabel("区域名称"));fields.add(name);fields.add(new JLabel("左上角 X / Y mm"));fields.add(pair(fx,fy));fields.add(new JLabel("宽 / 高 mm"));fields.add(pair(fw,fh));fields.add(new JLabel("安全边距 mm"));fields.add(clearance);
        JOptionPane options=new JOptionPane(fields,JOptionPane.PLAIN_MESSAGE,JOptionPane.OK_CANCEL_OPTION);
        final JDialog dialog=options.createDialog(this,"本页额外禁写区");
        try{
            for(;;){
                options.setValue(JOptionPane.UNINITIALIZED_VALUE);
                dialog.setVisible(true);
                if(!Integer.valueOf(JOptionPane.OK_OPTION).equals(options.getValue()))return null;
                try{
                    String label=name.getText()==null?"":name.getText().trim();if(label.isEmpty()||label.length()>80)throw new IOException("区域名称不能为空且不能超过 80 个字符");
                    rect.name=label;rect.x=value(fx);rect.y=value(fy);rect.width=value(fw);rect.height=value(fh);rect.clearanceMm=value(clearance);
                    NoteForbiddenZones candidate=document.forbiddenZones.copy();boolean replaced=false;
                    for(NoteForbiddenZones.PageException exception:candidate.exceptions)for(int index=0;index<exception.additionalZones.size();index++)if(Objects.equals(exception.additionalZones.get(index).id,rect.id)){exception.additionalZones.set(index,rect);replaced=true;break;}
                    if(!replaced)candidate.addExceptionZone(curPage+1,rect);
                    candidate.validate(document.page.pageWidth,document.page.pageHeight);
                    return rect;
                }
                catch(Exception error){reportFailure(error);JOptionPane.showMessageDialog(dialog,lastFailure,"本页额外禁写区",JOptionPane.ERROR_MESSAGE);}
            }
        }finally{dialog.dispose();}
    }
    private void editForbiddenZoneException(){
        editForbiddenZoneException(curPage+1);
    }
    private void editForbiddenZoneException(int page){
        if(isBusy())return;NoteForbiddenZones.PageException existing=document.forbiddenZones.exception(page);Set<String> disabled=new LinkedHashSet<String>();if(existing!=null&&existing.disabledRuleIds!=null)disabled.addAll(existing.disabledRuleIds);
        JPanel panel=new JPanel(new GridLayout(0,1,0,3));panel.add(new JLabel("勾选要在第 "+page+" 页停用的继承规则："));List<JCheckBox> controls=new ArrayList<JCheckBox>();
        if(document.forbiddenZones.rules!=null)for(NoteForbiddenZones.Rule rule:document.forbiddenZones.rules)if(rule!=null&&rule.appliesTo(page)){JCheckBox check=new JCheckBox(rule.name+"（"+rule.pageMode+"）",disabled.contains(rule.id));check.putClientProperty("ruleId",rule.id);controls.add(check);panel.add(check);}
        if(controls.isEmpty())panel.add(new JLabel("当前页没有适用的继承规则。"));
        JScrollPane scroll=new JScrollPane(panel);scroll.setPreferredSize(new Dimension(300,Math.min(300,Math.max(100,controls.size()*32+45))));
        if(JOptionPane.showConfirmDialog(this,scroll,"设置当前页例外",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
        for(JCheckBox check:controls){String id=(String)check.getClientProperty("ruleId");if(check.isSelected())disabled.add(id);else disabled.remove(id);}NoteEditSession.Transaction transaction=editorSession.capture();NoteDocument candidate=transaction.document();candidate.forbiddenZones.setException(page,disabled);commitForbiddenZones(transaction,candidate,"第 "+page+" 页的例外规则已更新；正在重新排版。");
    }
    private void startForbiddenZoneDrawing(){
        if(isBusy()||notePanel==null)return;if(notePanel.isEditing())setEditing(false);notePanel.setForbiddenZoneDrawing(true);message("请在预览纸张内按住鼠标左键拖拽，松开后设置区域尺寸和批量页面规则。");
    }
    public void createForbiddenZoneFromCanvas(int pageIndex,float x,float y,float width,float height){
        if(isBusy()||pageIndex<0||pageIndex>=layout.pages.size())return;float mmPerPx=document.page.pageWidth/layout.pageWidth;
        if(!Float.isFinite(mmPerPx)||mmPerPx<=0){failure(new IllegalStateException("当前页面像素与毫米比例无效"));return;}
        NoteForbiddenZones.Rule rule=forbiddenRuleDialog(null,x*mmPerPx,y*mmPerPx,width*mmPerPx,height*mmPerPx);if(rule==null)return;
        NoteEditSession.Transaction transaction=editorSession.capture();NoteDocument candidate=transaction.document();candidate.forbiddenZones.rules.add(rule);commitForbiddenZones(transaction,candidate,"禁写区已创建；正在重新排版。");
    }
    private void exportForbiddenZoneTemplate(){
        if(isBusy())return;
        try{File selected=NoteFileDialogs.saveFile(this,"导出禁写区规则模板",new File("禁写区模板.json"),"JSON 模板 (*.json)","json");if(selected==null)return;
            NoteForbiddenZones.Template template=new NoteForbiddenZones.Template(document.page.pageWidth,document.page.pageHeight,document.forbiddenZones);NoteForbiddenZoneTemplateIO.save(selected.toPath(),template);message("禁写区模板已导出："+selected.getName());}
        catch(Exception error){failure(error);}
    }
    private void importForbiddenZoneTemplate(){
        if(isBusy())return;
        try{
            File selected=NoteFileDialogs.openFile(this,"导入禁写区规则模板",null,"JSON 模板 (*.json)","json");if(selected==null)return;
            NoteForbiddenZones.Template template=NoteForbiddenZoneTemplateIO.load(selected.toPath());
            JCheckBox scale=new JCheckBox("按当前纸张宽高分别缩放区域坐标",true);JLabel preview=new JLabel();
            Runnable updatePreview=()->{
                try{
                    NoteForbiddenZones converted=template.convert(document.page.pageWidth,document.page.pageHeight,scale.isSelected());
                    int extra=0;for(NoteForbiddenZones.PageException exception:converted.exceptions)extra+=exception.additionalZones.size();
                    String ratio=scale.isSelected()?String.format(Locale.ROOT,"X %.3f，Y %.3f",document.page.pageWidth/template.sourcePageWidthMm,document.page.pageHeight/template.sourcePageHeightMm):"保持原始毫米坐标";
                    preview.setText("<html>来源纸张："+template.sourcePageWidthMm+" × "+template.sourcePageHeightMm+" mm<br>当前纸张："+document.page.pageWidth+" × "+document.page.pageHeight+" mm<br>转换："+ratio+"<br>规则："+converted.rules.size()+"；单页例外："+converted.exceptions.size()+"；额外区域："+extra+"</html>");
                }catch(Exception invalid){preview.setText("<html><font color='red'>当前纸张无法应用此模板："+escape(invalid.getMessage())+"</font></html>");}
            };
            scale.addActionListener(e->updatePreview.run());updatePreview.run();
            JPanel panel=new JPanel(new BorderLayout(0,8));panel.add(scale,BorderLayout.NORTH);panel.add(preview,BorderLayout.CENTER);panel.add(new JLabel("导入会替换当前文章的全部禁写区规则和单页例外；此操作可以撤销。"),BorderLayout.SOUTH);
            if(JOptionPane.showConfirmDialog(this,panel,"预览禁写区模板",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
            NoteForbiddenZones converted=template.convert(document.page.pageWidth,document.page.pageHeight,scale.isSelected());
            NoteEditSession.Transaction transaction=editorSession.capture();NoteDocument candidate=transaction.document();candidate.forbiddenZones=converted;
            commitForbiddenZones(transaction,candidate,"禁写区模板已导入；正在重新排版。");
        }catch(Exception error){failure(error);}
    }
    private void cacluteAllPaths(){processAsync();} // Original entry point; spelling is part of compatibility.
    private void updateSelectionControls(){updateForbiddenZoneButtons();updateMissingButton();Set<String> names=new LinkedHashSet<String>();int explicit=0;for(NoteDocument.Atom a:document.atoms)if(selection.ids().contains(a.id)){NoteDocument.FontRef f=a.override!=null?a.override:a.automatic;if(f!=null)names.add(f.name);if(a.override!=null)explicit++;}String detail=names.size()>1?"混合字体":names.isEmpty()?"点选、拖框或选择整行":names.iterator().next();selectionLabel.setText("已选 "+selection.ids().size()+" 字 · 指定 "+explicit+" 字"+(isDirty()?" · 未保存":""));selectionFontLabel.setText("字体："+detail);selectionFontLabel.setToolTipText(detail);boolean valid=!selection.ids().isEmpty()&&!isBusy();fontButton.setEnabled(valid);resetButton.setEnabled(valid);undoButton.setEnabled(editorSession.canUndo());redoButton.setEnabled(editorSession.canRedo());setPageInputsEnabled(!isBusy());updateResumeControls();if(appearancePanel!=null)appearancePanel.refresh();if(notePanel!=null)notePanel.repaint();}
    private void setPageInputsEnabled(boolean enabled){cbSettings.setEnabled(enabled);for(JFormattedTextField field:new JFormattedTextField[]{tfWidth,tfHeight,tfLeftGap,tfRightGap,tfTopGap,tfBottomGap,tfLines,tfWordGap,tfFontSize})field.setEnabled(enabled);btnSave.setEnabled(enabled);btnDelete.setEnabled(enabled);btnChooseFont.setEnabled(enabled);}
    private void selectCurrentLine(){if(layout.pages.isEmpty())return;NoteLayout.Page page=layout.pages.get(curPage);for(NoteLayout.Glyph g:page.glyphs)if(selection.ids().contains(g.id)){selection.add(page.lines.get(g.line).ids,false);selectionChanged();return;}message("先点选行内任意一个字，或点击预览左侧 › 选整行。");}
    private void updateMissingButton(){boolean visible=!layout.missing.isEmpty();if(clearMissingButton.isVisible()!=visible){clearMissingButton.setVisible(visible);revalidate();repaint();}clearMissingButton.setEnabled(visible&&!isBusy());}
    private void chooseMissingAction(){
        if(isBusy()||layout.missing.isEmpty())return;
        Object[] options={"用指定字符替换","用空格替代","全部删除","取消"};
        int action=JOptionPane.showOptionDialog(this,"处理整篇文章的所有红框字符；正常文字和已补全的符号保持不变。\n指定字符替换后以蓝框标出，蓝框不会导出。\n也可用空格替代或全部删除；所有操作都可以撤销。","清除红框",JOptionPane.DEFAULT_OPTION,JOptionPane.QUESTION_MESSAGE,null,options,options[0]);
        if(action==0){String replacement=JOptionPane.showInputDialog(this,"输入一个替代字符（也可输入空格）：","替换红框字符",JOptionPane.QUESTION_MESSAGE);if(replacement!=null)replaceMissingCharacters(replacement);}
        else if(action==1||action==2)clearMissingCharacters(action==1);
    }
    public void clearMissingCharacters(boolean spaces){
        if(!SwingUtilities.isEventDispatchThread()){SwingUtilities.invokeLater(()->clearMissingCharacters(spaces));return;}
        if(isBusy()||layout.missing.isEmpty())return;
        Set<Long> ids=new HashSet<Long>();for(NoteLayout.Page page:layout.pages)for(NoteLayout.Glyph glyph:page.glyphs)if(glyph.missing)ids.add(glyph.id);
        try{NoteDocument candidate=document.copy();candidate.clearMissing(ids,spaces);submit(candidate,true,false,()->message("已"+(spaces?"用空格替代":"删除")+ids.size()+" 个红框字符，可撤销。"));}catch(Exception error){failure(error);}
    }
    public void replaceMissingCharacters(String replacement){
        if(!SwingUtilities.isEventDispatchThread()){SwingUtilities.invokeLater(()->replaceMissingCharacters(replacement));return;}
        if(isBusy()||layout.missing.isEmpty())return;
        try{
            NoteDocument.validateReplacement(replacement);Set<Long> ids=new HashSet<Long>();for(NoteLayout.Page page:layout.pages)for(NoteLayout.Glyph glyph:page.glyphs)if(glyph.missing)ids.add(glyph.id);
            final long firstNew=document.nextId;NoteDocument candidate=document.copy();candidate.replaceMissing(ids,replacement);
            submit(candidate,true,false,()->message("已替换 "+ids.size()+" 个红框字符，并以蓝框标出；可撤销。"),null,prepared->{
                for(NoteLayout.Page page:prepared.layout.pages)for(NoteLayout.Glyph glyph:page.glyphs)if(glyph.id>=firstNew&&glyph.missing)throw new IllegalArgumentException("所选字体无法生成替代字符，请换一个字符或调整默认字体；文章尚未修改。");
            });
        }catch(Exception error){failure(error);}
    }
    public void applyFont(NoteDocument.FontRef font){if(selection.ids().isEmpty()){message("请先在预览中选字。");return;}NoteEditSession.Transaction t=editorSession.capture();NoteDocument d=editorSession.snapshot();Set<Long> targets=selection.capture();for(NoteDocument.Atom a:d.atoms)if(targets.contains(a.id))a.override=font;editorSession.commit(t,d,NoteEditSession.Kind.FONT);changed();if(notePanel!=null)notePanel.syncEditor();}
    private NoteAppearance.Config appearanceConfig(boolean selected){
        if(selected)for(NoteDocument.Atom a:document.atoms)if(selection.ids().contains(a.id))return NoteAppearance.copy(NoteAppearance.effective(document,a));
        return NoteAppearance.copy(document.appearance);
    }
    /** Semantic effect changes are committed before any asynchronous font/layout work. */
    public void applyAppearance(NoteAppearance.Config config,boolean selected){
        applyAppearanceTo(config,selected?selection.capture():null);
    }
    private boolean appearanceRequestValid(){
        if(appearanceRequestRevision==editorSession.revision())return true;
        message("正文已变化，已取消过期的字形设置；请重新调整。");if(appearancePanel!=null)appearancePanel.refresh();return false;
    }
    private void applyAppearanceTo(NoteAppearance.Config config,Set<Long> targets){
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("字形设置必须在界面线程应用");
        finishEditorComposition();if(isBusy()){message("请等待当前笔迹生成完成。");return;}
        if(targets!=null&&targets.isEmpty())throw new IllegalArgumentException("请先选择要调整的文字");
        NoteAppearance.validate(config);NoteEditSession.Transaction tx=editorSession.capture();NoteDocument next=editorSession.snapshot();
        boolean rebuild=false;
        for(NoteDocument.Atom a:next.atoms)if(targets==null||targets.contains(a.id)){
            rebuild|=NoteAppearance.effective(next,a).continuity!=config.continuity;
            a.appearanceOverride=targets!=null?NoteAppearance.copy(config):null;
        }
        if(targets==null)next.appearance=NoteAppearance.copy(config);
        NoteAppearance.initialize(next,targets,false,rebuild);
        if(editorSession.commit(tx,next,NoteEditSession.Kind.OTHER)){appearanceRequestRevision=editorSession.revision();changed();if(notePanel!=null)notePanel.syncEditor();}
    }
    public void rerandomizeAppearance(boolean selected){
        rerandomizeAppearanceTo(selected?selection.capture():null);
    }
    private void rerandomizeAppearanceTo(Set<Long> targets){
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("随机误差必须在界面线程生成");
        finishEditorComposition();if(isBusy()){message("请等待当前笔迹生成完成。");return;}
        if(targets!=null&&targets.isEmpty())throw new IllegalArgumentException("请先选择要调整的文字");
        NoteEditSession.Transaction tx=editorSession.capture();NoteDocument next=editorSession.snapshot();
        boolean enabled=false;for(NoteDocument.Atom a:next.atoms)if((targets==null||targets.contains(a.id))&&NoteAppearance.effective(next,a).randomMode!=0){enabled=true;break;}
        if(!enabled){message("请先为目标文字开启独立随机或关联随机。");return;}
        NoteAppearance.rerandomizeEffects(next,targets);
        if(editorSession.commit(tx,next,NoteEditSession.Kind.OTHER)){changed();if(notePanel!=null)notePanel.syncEditor();}
    }
    public boolean isDirty(){return editorSession.isDirty()||(appearancePanel!=null&&appearancePanel.hasPending());}
    public boolean hasUnappliedPageSettings(){try{PageInfo requested=readPage(),applied=copyPage(document.page);requested.fontPath=applied.fontPath;requested.fontChance=applied.fontChance;return !samePageSettings(requested,applied);}catch(Exception error){return true;}}
    private static boolean samePageSettings(PageInfo a,PageInfo b){return java.util.Objects.equals(a.name,b.name)&&Float.compare(a.pageWidth,b.pageWidth)==0&&Float.compare(a.pageHeight,b.pageHeight)==0&&Float.compare(a.leftGap,b.leftGap)==0&&Float.compare(a.rightGap,b.rightGap)==0&&Float.compare(a.topGap,b.topGap)==0&&Float.compare(a.bottomGap,b.bottomGap)==0&&a.lineNum==b.lineNum&&Float.compare(a.space,b.space)==0&&Float.compare(a.fontSize,b.fontSize)==0&&Arrays.equals(a.fontPath,b.fontPath)&&Arrays.equals(a.fontChance,b.fontChance);}
    public boolean isBusy(){return pending!=null||loading!=null||inlinePending!=null||layoutQueued;}
    public String lastFailure(){return lastFailure;}
    public NoteDocument document(){return document.copy();}
    public NoteLayout noteLayout(){return layout;}
    public NoteSelection selection(){return selection;}
    public NoteFonts catalogue() throws Exception{return fonts();}
    public boolean randomCopies(){return randomCopies.isSelected();}
    public void setRandomCopies(boolean selected){randomCopies.setSelected(selected);}
    public void setDocument(NoteDocument d){submit(d.copy(),false,true);}
    public void saveDocument(Path file) throws IOException{finishEditorComposition();if(pending!=null||loading!=null)throw new IOException("请等待文章读取完成后保存");if(appearancePanel!=null&&appearancePanel.hasPending())throw new IOException("请等待字形与随机设置应用后保存");if(hasUnappliedPageSettings())throw new IOException("页面版式尚未应用；请先点击“应用版式”。");document.save(file);documentFile=file;savedDocument=document.encode();editorSession.markSaved();selectionChanged();saveRecoveryAsync();message("文章已保存："+file.getFileName());}
    public String articleTitle(){return (documentFile==null?"未命名文章":documentFile.getFileName().toString())+(isDirty()?" *":"");}
    private boolean prepareSave(){
        finishEditorComposition();
        if(isBusy()||(appearancePanel!=null&&appearancePanel.hasPending())){message("请等待排版完成后保存。");return false;}
        if(!hasUnappliedPageSettings())return true;
        Object[] choices={"应用并保存","取消"};
        int answer=JOptionPane.showOptionDialog(this,"版式参数已修改但尚未应用，是否应用后保存？","保存文章",JOptionPane.DEFAULT_OPTION,JOptionPane.QUESTION_MESSAGE,null,choices,choices[0]);
        if(answer!=0)return false;
        applyLayoutAsync();
        if(hasUnappliedPageSettings())return false;
        if(isBusy()){
            SecondaryLoop loop=Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            javax.swing.Timer ready=new javax.swing.Timer(25,e->{if(!isBusy())loop.exit();});
            ready.start();try{loop.enter();}finally{ready.stop();}
        }
        if(inlineError!=null){message("版式应用失败，文章尚未保存："+inlineError);return false;}
        return !hasUnappliedPageSettings();
    }
    public boolean saveDocumentCurrent(){try{if(documentFile==null)return saveDocumentDialog();if(!prepareSave())return false;saveDocument(documentFile);return true;}catch(Exception e){failure(e);return false;}}
    private boolean saveDocumentDialog(){if(!prepareSave())return false;try{File chosen=NoteFileDialogs.saveArticle(this,documentFile==null?null:documentFile.toFile());if(chosen!=null){saveDocument(chosen.toPath());return true;}}catch(Exception e){failure(e);}return false;}
    private void openDocumentDialog(){if(isBusy())return;try{openDocument(NoteFileDialogs.openArticle(this,documentFile==null?null:documentFile.toFile()));}catch(Exception e){failure(e);}}
    public boolean openDocument(File file){return openDocument(file,null,null);}
    public boolean openDocument(File file,Runnable onSuccess){return openDocument(file,onSuccess,null);}
    public boolean openDocument(File file,Runnable onSuccess,Runnable onFailure){lastFailure=null;if(file==null||isBusy())return false;if((isDirty()||hasUnappliedPageSettings())&&JOptionPane.showConfirmDialog(this,"当前文章或版式参数尚未保存，仍要打开另一篇？","打开文章",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return false;return loadDocument(file,onSuccess,onFailure);}
    public void setFile(File f){loadDocument(f,null,null);}
    private boolean loadDocument(File f,Runnable onSuccess,Runnable onFailure){
        if(f==null||isBusy())return false;
        final String name=f.getName().toLowerCase(Locale.ROOT);
        final boolean nativeArticle=name.endsWith(".kjnote");
        if(!nativeArticle&&!name.endsWith(".txt")&&!name.endsWith(".doc")&&!name.endsWith(".docx")){failure(new IOException("请选择 KJNOTE、TXT、DOC 或 DOCX 文章"));return false;}
        final PageInfo importedPage=copyPage(pageInfo);
        final WordContentFormat importedFormat=NoteDocument.copyFormat(document.format);
        final JDialog progress=new JDialog(SwingUtilities.getWindowAncestor(this),"正在读取文章",Dialog.ModalityType.MODELESS);
        JButton cancel=new JButton("取消读取");
        progress.setLayout(new BorderLayout(8,8));progress.add(new JLabel("正在校验并读取 "+f.getName()+"…"),BorderLayout.CENTER);progress.add(cancel,BorderLayout.SOUTH);
        progress.setSize(360,110);progress.setLocationRelativeTo(this);
        final java.util.concurrent.atomic.AtomicBoolean stop=new java.util.concurrent.atomic.AtomicBoolean(),finished=new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicReference<Thread> reader=new java.util.concurrent.atomic.AtomicReference<Thread>();
        loading=new SwingWorker<NoteDocument,Void>(){
            protected NoteDocument doInBackground()throws Exception{
                Thread currentThread=Thread.currentThread();synchronized(reader){reader.set(currentThread);}
                try{
                    if(stop.get())throw new CancellationException();
                    if(Files.size(f.toPath())>NoteDocument.MAX_DOCUMENT_BYTES)throw new IOException("文章文件超过 16 MB 上限");
                    NoteDocument d;
                    if(nativeArticle)d=NoteDocument.load(f.toPath());
                    else{d=new NoteDocument();d.page=importedPage;d.format=importedFormat;d.replace(0,0,readText(f,d.format.charset));}
                    if(stop.get())throw new CancellationException();return d;
                }finally{synchronized(reader){reader.compareAndSet(currentThread,null);}}
            }
            protected void done(){
                finished.set(true);loading=null;progress.dispose();
                try{
                    if(stop.get()){message("已取消读取文章。");return;}
                    NoteDocument d=get();
                    Runnable committed=()->{documentFile=nativeArticle?f.toPath():null;if(!nativeArticle){savedDocument="";editorSession.markUnsaved();selectionChanged();message("已导入文章，请保存为 .kjnote："+f.getName());}if(onSuccess!=null)onSuccess.run();};
                    if(submit(d,false,true,committed,onFailure)==null&&onFailure!=null)onFailure.run();
                }catch(Exception error){failure(error);if(onFailure!=null)onFailure.run();}finally{selectionChanged();}
            }
        };
        Runnable stopReading=()->{if(finished.get()||stop.getAndSet(true))return;synchronized(reader){Thread thread=reader.get();if(thread!=null)thread.interrupt();}progress.dispose();message("正在停止读取文章…");};
        cancel.addActionListener(e->stopReading.run());
        progress.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        progress.addWindowListener(new WindowAdapter(){public void windowClosed(WindowEvent e){stopReading.run();}});
        selectionChanged();loading.execute();progress.setVisible(true);return true;
    }
    private void chooseSelectionFont(){
        finishEditorComposition();
        if(isBusy()||selection.ids().isEmpty())return;
        if(catalogue==null){message("字库仍在载入，请稍候。");return;}
        NoteEditSession.Transaction transaction=editorSession.capture();
        Set<Long> captured=selection.capture();NoteDocument candidate=transaction.document();
        NoteFontDialog dialog=new NoteFontDialog(SwingUtilities.getWindowAncestor(this),catalogue,candidate,captured);
        NoteFonts.Entry chosen=dialog.choose();if(chosen==null)return;
        if(transaction.revision()!=editorSession.revision()){
            message("文章已变化，请重新选择要修改的字。");return;
        }
        try{
            NoteDocument.FontRef ref=catalogue.ref(chosen);
            for(NoteDocument.Atom atom:candidate.atoms)if(captured.contains(atom.id))atom.override=ref;
            if(editorSession.commit(transaction,candidate,NoteEditSession.Kind.FONT)){
                changed();if(notePanel!=null)notePanel.syncEditor();
            }
        }catch(Exception error){failure(error);}
    }
    private void editContent(){if(isBusy()){message("请等待当前文章修改完成。");return;}final NoteDocument draft=document.copy();JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(this),"编辑文章",Dialog.ModalityType.APPLICATION_MODAL);dialog.setSize(760,560);
        JTextArea text=new JTextArea(draft.text);text.setLineWrap(true);text.setWrapStyleWord(true);text.setName("NoteArticleText");JLabel feedback=new JLabel();
        ((AbstractDocument)text.getDocument()).setDocumentFilter(new DocumentFilter(){
            public void insertString(FilterBypass b,int o,String s,AttributeSet a)throws BadLocationException{replace(b,o,0,s,a);}
            public void remove(FilterBypass b,int o,int n)throws BadLocationException{replace(b,o,n,"",null);}
            public void replace(FilterBypass b,int o,int n,String s,AttributeSet a)throws BadLocationException{String value=NoteDocument.normalize(s==null?"":s);try{draft.replace(o,n,value);b.replace(o,n,value,a);feedback.setText("");}catch(IllegalArgumentException tooLong){feedback.setText("文章最多 10 万字，请缩短后再粘贴。");java.awt.Toolkit.getDefaultToolkit().beep();}}
        });
        JPanel bar=new JPanel(new GridLayout(0,1,0,4)),formatRow=new JPanel(new FlowLayout(FlowLayout.LEADING,6,2)),importRow=new JPanel(new FlowLayout(FlowLayout.LEADING,6,2));bar.add(formatRow);bar.add(importRow);
        JCheckBox paragraphs=new JCheckBox("自动段落缩进",draft.format.autoGp);JSpinner indent=new JSpinner(new SpinnerNumberModel(draft.format.gpNum,1,5,1));JComboBox<LineAlign> align=new JComboBox<LineAlign>(LineAlign.values());align.setSelectedItem(draft.format.firstLineAlign);formatRow.add(paragraphs);formatRow.add(new JLabel("首行缩进格数"));formatRow.add(indent);formatRow.add(new JLabel("对齐"));formatRow.add(align);
        JComboBox<String> charset=new JComboBox<String>(new String[]{"UTF-8","GBK"});charset.setSelectedItem(draft.format.charset);importRow.add(new JLabel("TXT 编码"));importRow.add(charset);
        JButton ok=new JButton("确定文章"),cancel=new JButton("取消");ok.setName("NoteConfirmContent");
        JButton importText=new JButton("导入 TXT / Word…");importText.setName("NoteImportText");importRow.add(importText);
        final java.util.concurrent.atomic.AtomicReference<SwingWorker<String,Void>> importing=new java.util.concurrent.atomic.AtomicReference<SwingWorker<String,Void>>();
        importText.addActionListener(ev->{
            try{
                File chosen=NoteFileDialogs.importText(dialog);if(chosen==null)return;final String selectedCharset=(String)charset.getSelectedItem();
                importText.setEnabled(false);ok.setEnabled(false);text.setEditable(false);
                SwingWorker<String,Void> work=new SwingWorker<String,Void>(){
                    protected String doInBackground()throws Exception{return readText(chosen,selectedCharset);}
                    protected void done(){importing.set(null);importText.setEnabled(true);if(isCancelled()||!dialog.isDisplayable()){text.setEditable(true);ok.setEnabled(true);return;}try{text.setText(get());}catch(Exception error){failure(error);}finally{text.setEditable(true);ok.setEnabled(true);}}
                };
                importing.set(work);work.execute();
            }catch(Exception e){text.setEditable(true);importText.setEnabled(true);ok.setEnabled(true);failure(e);}
        });
        dialog.addWindowListener(new WindowAdapter(){public void windowClosed(WindowEvent e){SwingWorker<String,Void> work=importing.getAndSet(null);if(work!=null)work.cancel(true);}});
        ok.addActionListener(ev->{draft.format.autoGp=paragraphs.isSelected();draft.format.gpNum=(Integer)indent.getValue();draft.format.firstLineAlign=(LineAlign)align.getSelectedItem();draft.format.charset=(String)charset.getSelectedItem();
            try{draft.page=readPage();draft.shapes=NoteDocument.ShapeSettings.capture((FontsSetData)Storage.getStore(FontsSetData.class));SwingWorker<Prepared,Void> work=submit(draft.copy(),true,false,dialog::dispose);
                if(work!=null){ok.setEnabled(false);cancel.setEnabled(false);text.setEditable(false);importText.setEnabled(false);dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);feedback.setText("正在校验并排版…");
                    work.addPropertyChangeListener(change->{if(change.getNewValue()==SwingWorker.StateValue.DONE&&dialog.isDisplayable()){ok.setEnabled(true);cancel.setEnabled(true);text.setEditable(true);importText.setEnabled(true);dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);feedback.setText(lastFailure==null?"":"<html>"+escape(lastFailure)+"</html>");}});
                }
            }catch(Exception e){failure(e);feedback.setText(lastFailure);}});
        cancel.addActionListener(ev->dialog.dispose());JPanel footer=new JPanel(new BorderLayout());JPanel actions=new JPanel();actions.add(ok);actions.add(cancel);footer.add(actions,BorderLayout.NORTH);footer.add(feedback,BorderLayout.CENTER);dialog.add(bar,BorderLayout.NORTH);dialog.add(new JScrollPane(text),BorderLayout.CENTER);dialog.add(footer,BorderLayout.SOUTH);dialog.setLocationRelativeTo(this);dialog.setVisible(true);
    }
    private String readText(File f,String charset)throws Exception {
        if(Files.size(f.toPath())>NoteDocument.MAX_DOCUMENT_BYTES)throw new IOException("文章文件超过 16 MB 上限");
        String n=f.getName().toLowerCase(Locale.ROOT),value;
        if(n.endsWith(".docx"))checkWordArchive(f.toPath());
        if(Thread.currentThread().isInterrupted())throw new CancellationException();
        try(InputStream in=new FileInputStream(f)) {
            if(n.endsWith(".docx")){try(org.apache.poi.xwpf.usermodel.XWPFDocument doc=new org.apache.poi.xwpf.usermodel.XWPFDocument(in);org.apache.poi.xwpf.extractor.XWPFWordExtractor ex=new org.apache.poi.xwpf.extractor.XWPFWordExtractor(doc)){value=ex.getText();}}
            else if(n.endsWith(".doc")){try(org.apache.poi.hwpf.extractor.WordExtractor ex=new org.apache.poi.hwpf.extractor.WordExtractor(in)){value=ex.getText();}}
            else if(n.endsWith(".txt")){value=org.apache.commons.io.IOUtils.toString(in,charset==null?"UTF-8":charset);if(value.startsWith("\uFEFF"))value=value.substring(1);}
            else throw new IOException("请选择 TXT、DOC 或 DOCX 文章");
        }
        if(Thread.currentThread().isInterrupted())throw new CancellationException();
        if(value.length()>NoteDocument.MAX_TEXT_CHARS)throw new IOException("文章正文超过 10 万字符上限");
        return NoteDocument.normalize(value);
    }
    private static void checkWordArchive(Path file)throws IOException{
        long total=0;int entries=0;byte[] chunk=new byte[8192];
        try(java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(Files.newInputStream(file))){
            for(java.util.zip.ZipEntry entry;(entry=zip.getNextEntry())!=null;zip.closeEntry()){
                if(++entries>4096)throw new IOException("Word 文件包含过多内容");
                for(int n;(n=zip.read(chunk))!=-1;){
                    if(Thread.currentThread().isInterrupted())throw new CancellationException();
                    total+=n;if(total>32L*1024*1024)throw new IOException("Word 解压内容超过 32 MB 上限");
                }
            }
        }
    }
    public boolean onNoteContentSet(StringBuffer text,WordContentFormat format){if(!SwingUtilities.isEventDispatchThread()||isBusy())return false;NoteDocument d=document.copy();d.replace(0,d.text.length(),text.toString());d.format=NoteDocument.copyFormat(format);final boolean[] committed={false};SecondaryLoop loop=Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();SwingWorker<Prepared,Void> work=submit(d,true,false,()->committed[0]=true);if(work==null)return false;work.addPropertyChangeListener(e->{if(e.getNewValue()==SwingWorker.StateValue.DONE)loop.exit();});loop.enter();return committed[0];}
    public void actionPerformed(ActionEvent ev){Object source=ev.getSource();if(source==btnSave)save();else if(source==btnDelete)delete();else if(source==btnChooseFont)r.a(chooseFontListener).a();else if(source==btnSetContent)editContent();else if(source==btnPrePage){curPage--;changePage();if(notePanel!=null)notePanel.scrollToPage(curPage);}else if(source==btnNextPage){curPage++;changePage();if(notePanel!=null)notePanel.scrollToPage(curPage);}else if(source==tfCurPage){try{curPage=(int)value(tfCurPage)-1;changePage();if(notePanel!=null)notePanel.scrollToPage(curPage);}catch(Exception e){failure(e);}}else if(source==cbSettings&&!binding&&cbSettings.getSelectedItem() instanceof PageInfo)setPageInfo((PageInfo)cbSettings.getSelectedItem());}
    public Component createCanvas(){captureView();notePanel=new g();notePanel.attachEditor(this);changePage();notePanel.setEditing(editToggle.isSelected());if(viewCaptured){notePanel.a(viewAngle);notePanel.setDisplayScale(viewScale);pendingViewRestore=true;SwingUtilities.invokeLater(this::restoreView);}return notePanel;}
    public void onActive(){sidebar=NoteSidebarView.attach(this);changePage();}
    public void onDeactive(){finishEditorComposition();captureView();saveRecoveryAsync();if(forbiddenZoneManager!=null&&forbiddenZoneManager.isDisplayable())forbiddenZoneManager.close();if(sidebar!=null){sidebar.detach();sidebar=null;}/* Submitted edits still belong to this tab. */}
    public String toString(){return "笔记";}
    public ImageIcon getIcon(){return new ImageIcon(L.b("note.png"));}
    public void setScale(float scale){}
    public String getDescription(){return "支持 KJNOTE、TXT、DOC 和 DOCX 文章；导入的文本请保存为 KJNOTE。";}
    public String enableExport(){return "kjnote";}
    public boolean enablePdf(){return true;}
    public byte[] getExportData(){return document.encode().getBytes(StandardCharsets.UTF_8);}
    public int getPageNum(){return exportLayout().pages.size();}
    public int getCurrentPageIndex(){return curPage;}
    public JComponent pageNavigation(){return pageNavigation;}
    public String currentPageSource(){
        String base="笔记当前页 "+(layout.pages.isEmpty()?0:curPage+1)+"/"+layout.pages.size();
        NoteLayout.Glyph resume=activeResumeGlyphForPage(curPage);
        return resume==null?base:base+" · 从第"+(resume.line+1)+"行「"+resume.text+"」续写";
    }
    public boolean beginMachineJob(){if(!isOutputReady()||layout.pages.isEmpty()||layout.pages.get(curPage).paths.isEmpty())return false;com.kenjoy.kenjoycnc.c.a generator=createPageGcodeGenerator(curPage,true);if(generator==null)return false;machineJobSource=currentPageSource();machineJobGenerator=generator;return true;}
    public void endMachineJob(){machineJobGenerator=null;machineJobSource=null;NoteWriteProgress.clearPreparedFor(this);}
    public String machineJobSource(){return machineJobSource;}
    public NoteLayout exportLayout(){NoteExportHooks.Job job=NoteExportHooks.current(this);return job==null?layout:job.layout;}
    public com.kenjoy.kenjoycnc.c.a getGcodeGenerator(){NoteExportHooks.Job job=NoteExportHooks.current(this);if(job!=null)return job.generator(job.pageIndex);if(machineJobGenerator!=null)return machineJobGenerator;if(layout.pages.isEmpty()||!isOutputReady())return null;return createPageGcodeGenerator(curPage,false);}
    private com.kenjoy.kenjoycnc.c.a createPageGcodeGenerator(int pageIndex,boolean trackWrite){
        if(layout.pages.isEmpty()||pageIndex<0||pageIndex>=layout.pages.size())return null;
        NoteLayout.Page page=layout.pages.get(pageIndex);
        if(page.paths.isEmpty())return null;
        java.awt.geom.AffineTransform transform=machineTransform();
        List converted=validatedMachinePaths(pageIndex,page,document,Settings.getInst().dpi,transform);
        List<NoteLayout.Glyph> glyphs=machineGlyphs(page);
        if(glyphs.size()!=converted.size())return new com.kenjoy.kenjoycnc.c.c(L.b(converted),Settings.getInst().nearDst,false);
        NoteWriteProgress.Snapshot snapshot=buildBaseSnapshot(pageIndex,currentProcessingAngle());
        if(snapshot==null)return new com.kenjoy.kenjoycnc.c.c(L.b(converted),Settings.getInst().nearDst,false);
        int startOrder=0;
        long resumeId=resumeGlyphIdForPage(pageIndex);
        if(resumeId>0L){
            int idx=snapshot.indexOf(resumeId);
            if(idx>=0&&idx<converted.size()){startOrder=idx;snapshot.resumeGlyphId=resumeId;}
        }
        snapshot.startGlyphOrder=startOrder;
        List slice=L.b(converted.subList(startOrder,converted.size()));
        return new NoteWriteProgress.PageGenerator(this,snapshot,slice,trackWrite);
    }
    public List getExportGcodeGenerator(){NoteExportHooks.Job job=NoteExportHooks.current(this);if(job!=null)return job.generators();List result=new ArrayList();if(!isOutputReady())throw new IllegalStateException(outputReadinessMessage());java.awt.geom.AffineTransform transform=machineTransform();for(int index=0;index<layout.pages.size();index++){List converted=validatedMachinePaths(index,layout.pages.get(index),document,Settings.getInst().dpi,transform);result.add(new com.kenjoy.kenjoycnc.c.c(L.b(converted),Settings.getInst().nearDst,false));}return result;}
    public BufferedImage renderToImage(int page){NoteExportHooks.Job job=NoteExportHooks.current(this);if(job==null&&!isOutputReady())throw new IllegalStateException(outputReadinessMessage());NoteLayout l=job==null?layout:job.layout;NoteDocument d=job==null?document:job.document;return image(l,d,page,job==null?Settings.getInst().dpi:job.dpi);}
    public static BufferedImage image(NoteLayout layout,NoteDocument d,int index,float dpi){if(index<0||index>=layout.pages.size())throw new IllegalArgumentException("页码不存在");double width=d.page.pageWidth*(double)dpi/25.4,height=d.page.pageHeight*(double)dpi/25.4;if(!Double.isFinite(width)||!Double.isFinite(height)||width<1||height<1||width>12000||height>12000||width*height>24_000_000)throw new IllegalArgumentException("导出页面尺寸过大（最多 2400 万像素）");int w=(int)Math.round(width),h=(int)Math.round(height);BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);Graphics2D out=img.createGraphics();out.setColor(Color.WHITE);out.fillRect(0,0,w,h);out.setColor(Color.BLACK);out.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);out.draw(NoteLayout.path(layout.pages.get(index).paths));out.dispose();return img;}
    public List machinePaths(NoteLayout.Page page){return notePanel.a(L.b(page.paths));}
    private List validatedMachinePaths(int pageIndex,NoteLayout.Page page,NoteDocument source,float dpi,java.awt.geom.AffineTransform transform){List converted=machinePaths(page);NoteForbiddenZoneGeometry.validatePagePaths(source,pageIndex,dpi,transform,converted);return converted;}
    public java.awt.geom.AffineTransform machineTransform(){com.kenjoy.kenjoycnc.g.e basis=new com.kenjoy.kenjoycnc.g.e();basis.a(0,0);basis.b(1024,0);basis.b(0,1024);List result=notePanel.a(Collections.singletonList(basis));float[] xy=((com.kenjoy.kenjoycnc.g.e)result.get(0)).a();return new java.awt.geom.AffineTransform((xy[2]-xy[0])/1024,(xy[3]-xy[1])/1024,(xy[4]-xy[0])/1024,(xy[5]-xy[1])/1024,xy[0],xy[1]);}

    public NoteEditSession session(){return editorSession;}
    public NoteEditGeometry geometry(){if(editGeometry==null)refreshEditGeometry();return editGeometry;}
    public float scale(){return notePanel==null?1:notePanel.displayScale();}
    private void validatePageCandidate(NoteDocument d){NoteEditGeometry.approximate(d,Settings.getInst().dpi,null);for(float[] values:new float[][]{d.shapes.randomFontSize,d.shapes.randomXScale,d.shapes.randomYScale,d.shapes.randomRotate,d.shapes.randomSpace}){if(values==null||values.length!=2||!Float.isFinite(values[0])||!Float.isFinite(values[1]))throw new IllegalArgumentException("随机参数无效");}}
    private void restoreInputDefaults(){FontsDialogData f=(FontsDialogData)Storage.getStore(FontsDialogData.class);if(f.fontsName!=null&&f.fontsName.length>0){pageInfo.fontPath=f.fontsName.clone();pageInfo.fontChance=f.chance==null?null:f.chance.clone();}captureInputFonts();}
    private void captureInputFonts(){if(pageInfo!=null&&editorSession!=null)editorSession.setInputFonts(pageInfo.fontPath,pageInfo.fontChance);}
    private void refreshEditGeometry(){
        try{displayLayout=NoteEditGeometry.approximate(document,Settings.getInst().dpi,displayLayout==null?layout:displayLayout);editGeometry=new NoteEditGeometry(displayLayout,36);}
        catch(Exception e){inlineError=e.getMessage();if(editGeometry==null){NoteDocument safe=new NoteDocument();safe.page=new PageInfo("默认",210,297,20,20,20,20,30,95,true);displayLayout=NoteEditGeometry.approximate(safe,96,null);editGeometry=new NoteEditGeometry(displayLayout,36);}}
    }
    public void changed(){
        if(pending!=null){++request;pending.cancel(true);pending=null;}
        document=editorSession.mutableDocument();content=new StringBuffer(document.text);contentSet=document.format;inlineError=null;
        layoutQueued=true;refreshEditGeometry();if(notePanel!=null)notePanel.refreshEditor();layoutTimer.restart();recoveryTimer.restart();selectionChanged();
    }
    public void selectionChanged(){
        if(notePanel!=null&&notePanel.isEditing()){
            selection.clear();int start=editorSession.selectionStart(),end=editorSession.selectionEnd(),offset=0;List<Long> ids=new ArrayList<Long>();
            for(NoteDocument.Atom atom:document.atoms){int next=offset+atom.text.length();if(offset<end&&next>start&&atom.drawable())ids.add(atom.id);offset=next;}selection.add(ids,false);
            int caret=editorSession.caret();boolean backward=caretBackward();boolean moved=caret!=observedCaret||backward!=observedCaretBackward;observedCaret=caret;observedCaretBackward=backward;
            if(!changingPage&&moved){int selectedPage=caretPage();if(curPage!=selectedPage){curPage=selectedPage;changePage();}else tfCurPage.setValue(curPage+1);}
        }
        updateSelectionControls();
    }
    public void failure(Throwable error){reportFailure(error);}
    private boolean caretBackward(){return notePanel!=null&&notePanel.textEditor()!=null&&notePanel.textEditor().getCaret() instanceof DefaultCaret&&((DefaultCaret)notePanel.textEditor().getCaret()).getDotBias()==Position.Bias.Backward;}
    private int caretPage(){return Math.max(0,geometry().caret(editorSession.caret(),caretBackward()).page);}
    public void history(boolean forward){editorHistory(forward);}
    private void editorHistory(boolean forward){
        finishEditorComposition();String[] inputFonts=editorSession.inputFonts();int[] inputChances=editorSession.inputChances();boolean changed=forward?editorSession.redo():editorSession.undo();if(!changed)return;
        changed();if(notePanel!=null)notePanel.syncEditor();refreshForbiddenZoneList();setPageInfo(document.page);pageInfo.fontPath=inputFonts;pageInfo.fontChance=inputChances;captureInputFonts();
    }
    private void setEditing(boolean active){
        boolean previous=notePanel!=null&&notePanel.isEditing();
        try{if(active&&hasUnappliedPageSettings()){NoteEditSession.Transaction t=editorSession.capture();NoteDocument d=editorSession.snapshot();d.page=readPage();d.shapes=NoteDocument.ShapeSettings.capture((FontsSetData)Storage.getStore(FontsSetData.class));validatePageCandidate(d);editorSession.commit(t,d,NoteEditSession.Kind.OTHER);document=editorSession.mutableDocument();changed();}
            editToggle.setSelected(active);editToggle.setText(active?"退出编辑模式":"进入编辑模式");captureInputFonts();refreshEditGeometry();if(notePanel!=null)notePanel.setEditing(active);selectionChanged();
        }catch(Exception e){editToggle.setSelected(previous);editToggle.setText(previous?"退出编辑模式":"进入编辑模式");reportFailure(e);}
    }
    public void setInlineEditing(boolean active){setEditing(active);}
    public boolean isInlineEditing(){return notePanel!=null&&notePanel.isEditing();}
    private void finishEditorComposition(){if(notePanel!=null&&notePanel.textEditor()!=null)notePanel.textEditor().finishComposition();}
    private void buildInlineLayout(){
        if(!layoutQueued)return;if(inlinePending!=null){return;}if(pending!=null||loading!=null){layoutTimer.restart();return;}
        layoutQueued=false;final NoteDocument candidate=editorSession.snapshot();final long revision=editorSession.revision();final float dpi=Settings.getInst().dpi;
        inlinePending=new SwingWorker<Prepared,Void>(){
            protected Prepared doInBackground()throws Exception{
                NoteFonts f=fonts();
                return new Prepared(candidate,new NoteLayoutEngine(f,dpi).build(candidate));
            }
            protected void done(){
                try{Prepared result=get();boolean followCaret=notePanel!=null&&notePanel.isEditing()&&curPage==caretPage();if(editorSession.adoptLayout(result.document,revision)){
                    document=editorSession.mutableDocument();layout=result.layout;layoutRevision=revision;inlineError=null;lastFailure=null;displayLayout=layout;editGeometry=new NoteEditGeometry(layout,36);
                    content=new StringBuffer(document.text);pageDatas.clear();for(NoteLayout.Page p:layout.pages)pageDatas.add(new com.kenjoy.kenjoycnc.l.f(0,p.paths,Collections.emptyList()));
                    reconcileWriteProgress(true);
                    curPage=followCaret?Math.min(caretPage(),Math.max(0,layout.pages.size()-1)):Math.max(0,Math.min(curPage,layout.pages.size()-1));changePage();message("笔迹已更新。"+(isDirty()?"文章有未保存修改。":""));
                }}catch(Exception e){if(revision==editorSession.revision()){Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();inlineError=cause.getMessage()==null?cause.toString():cause.getMessage();lastFailure=inlineError;message("正文已保留，笔迹尚未完成："+inlineError);}}
                finally{inlinePending=null;if(revision!=editorSession.revision())layoutQueued=true;if(layoutQueued)layoutTimer.restart();selectionChanged();restoreView();recoveryTimer.restart();}
            }
        };inlinePending.execute();selectionChanged();
    }
    public boolean isOutputReady(){return !isBusy()&&(appearancePanel==null||!appearancePanel.hasPending())&&inlineError==null&&layoutRevision==editorSession.revision()&&layout.missing.isEmpty()&&!hasUnappliedPageSettings();}
    public String outputReadinessMessage(){if(!layout.missing.isEmpty())return "文章仍有缺字红框，请修复后再导出或开始书写。";if(inlineError!=null)return "笔迹尚未生成："+inlineError;if(hasUnappliedPageSettings())return "请先应用页面版式。";return "请等待当前正文的笔迹生成完成。";}
    public String editSourceIdentity(){return documentId+":"+editorSession.contentIdentity()+":"+curPage+":"+(notePanel==null?0:notePanel.processingAngle());}
    public void showEditorSettings(){
        try{JComboBox<String> policy=new JComboBox<String>(new String[]{"自动保留本地恢复稿","关闭前询问保存"});policy.setSelectedIndex(NoteRecovery.closePolicy()==NoteRecovery.ClosePolicy.AUTO_RECOVER?0:1);
            JPanel pane=new JPanel(new BorderLayout(6,6));pane.add(new JLabel("关闭时未保存内容的处理方式"),BorderLayout.NORTH);pane.add(policy,BorderLayout.CENTER);
            if(JOptionPane.showConfirmDialog(this,pane,"编辑设置",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION)NoteRecovery.setClosePolicy(policy.getSelectedIndex()==0?NoteRecovery.ClosePolicy.AUTO_RECOVER:NoteRecovery.ClosePolicy.ASK_SAVE);
        }catch(Exception e){reportFailure(e);}
    }
    private SwingWorker<Void,Void> recoveryWorker;
    private boolean editorClosing;
    private JViewport canvasViewport(){
        if(notePanel==null)return null;
        Container viewport=SwingUtilities.getAncestorOfClass(JViewport.class,notePanel);
        return viewport instanceof JViewport?(JViewport)viewport:null;
    }
    private void captureView(){
        if(notePanel==null)return;viewAngle=notePanel.processingAngle();viewScale=notePanel.displayScale();viewCaptured=true;
        JViewport viewport=canvasViewport();
        if(!pendingViewRestore&&viewport!=null)viewPosition=viewport.getViewPosition();
    }
    private void restoreView(){
        if(!pendingViewRestore||notePanel==null||isBusy())return;
        JViewport viewport=canvasViewport();if(viewport==null)return;
        Component view=viewport.getView();if(view==null)return;
        Dimension extent=viewport.getExtentSize(),preferred=view.getPreferredSize();
        if(extent.width<=0||extent.height<=0)return;
        Dimension size=view==notePanel?preferred:new Dimension(Math.max(extent.width,preferred.width),Math.max(extent.height,preferred.height));
        viewport.setViewSize(size);if(view instanceof Container)((Container)view).doLayout();
        viewport.setViewPosition(new Point(Math.max(0,Math.min(viewPosition.x,size.width-extent.width)),Math.max(0,Math.min(viewPosition.y,size.height-extent.height))));
        viewPosition=viewport.getViewPosition();pendingViewRestore=false;
    }
    private boolean awaitingInitialRecovery(){return recoveryRestoring&&editorSession.revision()==recoveryStartRevision;}
    private NoteRecovery.Snapshot recoverySnapshot(){
        captureView();NoteRecovery.Snapshot value=new NoteRecovery.Snapshot();value.documentId=documentId;value.document=editorSession.snapshot();value.savedState=savedDocument;value.file=documentFile==null?null:documentFile.toAbsolutePath().toString();value.dirty=isDirty();value.editing=editToggle.isSelected();value.anchor=editorSession.anchor();value.dot=editorSession.caret();value.revision=editorSession.revision();value.page=Math.max(0,curPage);value.processingAngle=viewAngle;
        value.scrollX=Math.max(0,viewPosition.x);value.scrollY=Math.max(0,viewPosition.y);return value;
    }
    private void saveRecoveryAsync(){
        if(editorClosing||awaitingInitialRecovery())return;if(recoveryWriting){recoveryAgain=true;return;}final NoteRecovery.Snapshot snapshot=recoverySnapshot();recoveryWriting=true;
        recoveryWorker=new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{NoteRecovery.save(snapshot);return null;}protected void done(){recoveryWriting=false;try{get();}catch(Exception e){reportFailure(e);}if(recoveryAgain&&!editorClosing){recoveryAgain=false;recoveryTimer.restart();}}};recoveryWorker.execute();
    }
    private static final class RestoredRecovery {
        final NoteRecovery.Snapshot editor;
        final NoteWriteProgress.Snapshot progress;
        RestoredRecovery(NoteRecovery.Snapshot editor,NoteWriteProgress.Snapshot progress){this.editor=editor;this.progress=progress;}
    }
    private void restoreRecovery(){
        final long restoreRevision=editorSession.revision(),restoreRequest=request;
        new SwingWorker<RestoredRecovery,Void>(){protected RestoredRecovery doInBackground()throws Exception{
            NoteRecovery.Snapshot editor=NoteRecovery.load();
            NoteWriteProgress.Snapshot progress=null;
            try{progress=NoteWriteProgress.load();}catch(Exception error){System.err.println("NOTE_WRITE_PROGRESS_LOAD "+error.getMessage());}
            return new RestoredRecovery(editor,progress);
        }protected void done(){try{
            RestoredRecovery restored=get();NoteRecovery.Snapshot value=restored.editor;
            if(editorClosing||value==null||!document.text.isEmpty()||editorSession.revision()!=restoreRevision||request!=restoreRequest||pending!=null||loading!=null)return;documentId=value.documentId;
            if(restored.progress!=null&&Objects.equals(documentId,restored.progress.documentId))writeProgress=restored.progress;
            editorSession.reset(value.document);document=editorSession.mutableDocument();editorSession.setSelection(value.anchor,value.dot);documentFile=value.file==null?null:Paths.get(value.file);savedDocument=value.savedState==null?"":value.savedState;if(value.dirty)editorSession.markUnsaved();else editorSession.markSaved();setPageInfo(document.page);restoreInputDefaults();curPage=value.page;changed();
            viewAngle=value.processingAngle;viewPosition=new Point(value.scrollX,value.scrollY);viewCaptured=true;pendingViewRestore=true;
            if(notePanel!=null){notePanel.a(viewAngle);viewScale=notePanel.displayScale();notePanel.syncEditor();}setEditing(value.editing);
            SwingUtilities.invokeLater(NoteTab.this::restoreView);message(writeProgress==null?"已恢复上次编辑的文章。":"已恢复上次编辑的文章及书写进度。");
        }catch(Exception e){reportFailure(e);}finally{recoveryRestoring=false;}}}.execute();
    }
    private float currentProcessingAngle(){return notePanel!=null?notePanel.processingAngle():viewAngle;}
    private static List<NoteLayout.Glyph> machineGlyphs(NoteLayout.Page page){
        if(page==null||page.paths==null||page.glyphs==null)return Collections.emptyList();
        IdentityHashMap<com.kenjoy.kenjoycnc.g.e,NoteLayout.Glyph> byPath=new IdentityHashMap<com.kenjoy.kenjoycnc.g.e,NoteLayout.Glyph>();
        for(NoteLayout.Glyph g:page.glyphs)if(g.path!=null&&!g.whitespace&&!g.missing&&!g.pending)byPath.put(g.path,g);
        List<NoteLayout.Glyph> result=new ArrayList<NoteLayout.Glyph>(page.paths.size());
        for(com.kenjoy.kenjoycnc.g.e path:page.paths){NoteLayout.Glyph matched=byPath.get(path);if(matched==null)return Collections.emptyList();result.add(matched);}
        return result;
    }
    private NoteWriteProgress.Snapshot buildBaseSnapshot(int pageIndex,float angle){
        if(layout==null||pageIndex<0||pageIndex>=layout.pages.size())return null;
        List<NoteLayout.Glyph> glyphs=machineGlyphs(layout.pages.get(pageIndex));
        if(glyphs.isEmpty())return null;
        NoteWriteProgress.Snapshot s=new NoteWriteProgress.Snapshot();
        s.documentId=documentId;s.contentIdentity=String.valueOf(editorSession.contentIdentity());
        s.textHash=NoteWriteProgress.textHash(document.text);
        s.geometryKey=layout.geometryKey==null?"":layout.geometryKey;
        s.pageIndex=pageIndex;s.pageCount=layout.pages.size();s.processingAngle=angle;
        s.updatedAtMillis=System.currentTimeMillis();
        int lastLine=-1,col=0;
        for(int i=0;i<glyphs.size();i++){
            NoteLayout.Glyph g=glyphs.get(i);
            if(g.line!=lastLine){lastLine=g.line;col=1;}else col++;
            NoteWriteProgress.GlyphEntry entry=new NoteWriteProgress.GlyphEntry();
            entry.id=g.id;entry.text=g.text;entry.line=g.line;entry.column=col;entry.pageOrder=i+1;
            s.glyphs.add(entry);
        }
        return s;
    }
    private boolean isSnapshotCompatible(NoteWriteProgress.Snapshot s){
        if(s==null||layout==null||inlineError!=null||layoutRevision!=editorSession.revision())return false;
        if(!Objects.equals(documentId,s.documentId))return false;
        if(s.pageIndex<0||s.pageIndex>=layout.pages.size())return false;
        if(Float.compare(currentProcessingAngle(),s.processingAngle)!=0)return false;
        if(s.textHash!=null&&!s.textHash.isEmpty()&&!Objects.equals(NoteWriteProgress.textHash(document.text),s.textHash))return false;
        if(s.geometryKey!=null&&!s.geometryKey.isEmpty()&&!Objects.equals(layout.geometryKey,s.geometryKey))return false;
        List<NoteLayout.Glyph> glyphs=machineGlyphs(layout.pages.get(s.pageIndex));
        if(glyphs.size()!=s.glyphs.size())return false;
        for(int i=0;i<glyphs.size();i++){
            NoteLayout.Glyph g=glyphs.get(i);NoteWriteProgress.GlyphEntry e=s.glyphs.get(i);
            if(g.id!=e.id||!Objects.equals(g.text,e.text))return false;
        }
        return true;
    }
    private void reconcileWriteProgress(boolean clearDiskIfStale){
        if(writeProgress==null||isBusy()||inlineError!=null||layoutRevision!=editorSession.revision())return;
        if(!isSnapshotCompatible(writeProgress)){
            writeProgress=null;
            if(clearDiskIfStale)NoteWriteProgress.clearSaved();
        }
    }
    public NoteWriteProgress.Snapshot writeProgressForPage(int pageIndex){
        if(writeProgress==null||writeProgress.pageIndex!=pageIndex)return null;
        if(!isBusy()&&!isSnapshotCompatible(writeProgress))return null;
        return writeProgress;
    }
    public long resumeGlyphIdForPage(int pageIndex){
        NoteWriteProgress.Snapshot s=writeProgressForPage(pageIndex);
        if(s==null)return -1L;
        if(s.resumeGlyphId>0L&&s.indexOf(s.resumeGlyphId)>=0)return s.resumeGlyphId;
        return s.hasExecutionProgress()?s.recommendedResumeGlyphId():-1L;
    }
    private NoteLayout.Glyph activeResumeGlyphForPage(int pageIndex){
        long id=resumeGlyphIdForPage(pageIndex);
        if(id<=0L||layout==null||pageIndex<0||pageIndex>=layout.pages.size())return null;
        for(NoteLayout.Glyph g:layout.pages.get(pageIndex).glyphs)if(g.id==id)return g;
        return null;
    }
    public boolean hasResumeProgress(){return writeProgress!=null;}
    private long firstWritableSelectedGlyphId(){
        if(layout==null||selection.ids().isEmpty())return -1L;
        if(curPage>=0&&curPage<layout.pages.size()){
            for(NoteLayout.Glyph g:machineGlyphs(layout.pages.get(curPage)))if(selection.ids().contains(g.id))return g.id;
        }
        for(NoteLayout.Page p:layout.pages)for(NoteLayout.Glyph g:machineGlyphs(p))if(selection.ids().contains(g.id))return g.id;
        return -1L;
    }
    private void scrollToGlyph(int pageIndex,long glyphId){
        if(notePanel==null||layout==null||pageIndex<0||pageIndex>=layout.pages.size())return;
        for(NoteLayout.Glyph g:layout.pages.get(pageIndex).glyphs)if(g.id==glyphId){
            Point center=notePanel.previewPoint(pageIndex,(float)g.cell.getCenterX(),(float)g.cell.getCenterY());
            notePanel.scrollRectToVisible(new Rectangle(center.x-40,center.y-40,80,80));break;
        }
    }
    public void setResumeGlyphId(long glyphId){
        if(!SwingUtilities.isEventDispatchThread()){SwingUtilities.invokeLater(()->setResumeGlyphId(glyphId));return;}
        if(isBusy()||NoteWriteProgress.isWriting(documentId)||layout==null||layout.pages.isEmpty())return;
        int targetPage=layout.pageOf(glyphId);
        if(targetPage<0)return;
        boolean writable=false;
        for(NoteLayout.Glyph g:machineGlyphs(layout.pages.get(targetPage)))if(g.id==glyphId){writable=true;break;}
        if(!writable){message("所选字符缺少可写笔迹或为空白，不能作为续写起点。");return;}
        if(curPage!=targetPage)activatePreviewPage(targetPage);
        if(writeProgress!=null&&writeProgress.pageIndex==targetPage&&isSnapshotCompatible(writeProgress)){
            writeProgress.resumeGlyphId=glyphId;writeProgress.updatedAtMillis=System.currentTimeMillis();
        }else{
            NoteWriteProgress.Snapshot s=buildBaseSnapshot(targetPage,currentProcessingAngle());
            if(s==null)return;
            s.status="RESUME_SELECTED";s.startGlyphOrder=0;s.resumeGlyphId=glyphId;writeProgress=s;
        }
        NoteWriteProgress.saveResumeSelection(writeProgress.copy());
        updateResumeControls();if(notePanel!=null)notePanel.repaint();
        scrollToGlyph(targetPage,glyphId);
        message(writeProgress.resumeTargetSummary());
    }
    public void setResumeFromSelection(){
        if(isBusy()||NoteWriteProgress.isWriting(documentId))return;
        long id=firstWritableSelectedGlyphId();
        if(id<=0L){message("请先在预览中点选一个可写字作为续写起点。");return;}
        setResumeGlyphId(id);
    }
    public void selectInterruptedResumeGlyph(){
        if(isBusy()||NoteWriteProgress.isWriting(documentId)||writeProgress==null)return;
        long id=writeProgress.recommendedResumeGlyphId();
        if(id<=0L)return;
        setResumeGlyphId(id);selection.click(id,false);selectionChanged();
    }
    public void selectNextResumeGlyph(){
        if(isBusy()||NoteWriteProgress.isWriting(documentId)||writeProgress==null)return;
        long nextId=writeProgress.nextResumeGlyphId();
        if(nextId<=0L){message("当前已是本页最后一个可写字。");return;}
        setResumeGlyphId(nextId);selection.click(nextId,false);selectionChanged();
    }
    public void clearResumeProgress(){
        if(!SwingUtilities.isEventDispatchThread()){SwingUtilities.invokeLater(this::clearResumeProgress);return;}
        if(isBusy()||NoteWriteProgress.isWriting(documentId))return;
        writeProgress=null;NoteWriteProgress.clearSaved();
        updateResumeControls();if(notePanel!=null)notePanel.repaint();
        message("已清除续写断点，下次将从本页第 1 个字书写。");
    }
    public void onWriteJobProgress(NoteWriteProgress.Snapshot snapshot){
        if(snapshot==null||!Objects.equals(documentId,snapshot.documentId))return;
        writeProgress=snapshot;updateResumeControls();if(notePanel!=null)notePanel.repaint();
    }
    public void onWriteJobInterrupted(NoteWriteProgress.Snapshot snapshot){
        if(snapshot==null||!Objects.equals(documentId,snapshot.documentId))return;
        writeProgress=snapshot;
        if(layout!=null&&snapshot.pageIndex>=0&&snapshot.pageIndex<layout.pages.size()&&curPage!=snapshot.pageIndex)activatePreviewPage(snapshot.pageIndex);
        else{updateResumeControls();if(notePanel!=null)notePanel.repaint();}
        if(snapshot.resumeGlyphId>0L)scrollToGlyph(snapshot.pageIndex,snapshot.resumeGlyphId);
        message(snapshot.progressSummary()+"；"+snapshot.resumeTargetSummary());
    }
    public void onWriteJobCompleted(){
        writeProgress=null;updateResumeControls();if(notePanel!=null)notePanel.repaint();
        message("本页书写已完成。");
    }
    public void onCanvasAngleChanged(){
        if(writeProgress==null||isBusy()||layoutRevision!=editorSession.revision())return;
        if(Float.compare(currentProcessingAngle(),writeProgress.processingAngle)!=0){
            writeProgress=null;NoteWriteProgress.clearSaved();
            updateResumeControls();if(notePanel!=null)notePanel.repaint();
            message("加工方向已变化，已清除旧的续写断点。");
        }
    }
    private void updateResumeControls(){
        if(resumeFromSelectionButton==null)return;
        boolean writing=NoteWriteProgress.isWriting(documentId);
        boolean busy=isBusy()||writing;
        boolean hasSelection=firstWritableSelectedGlyphId()>0L;
        boolean hasProgress=writeProgress!=null;
        boolean hasInterrupted=hasProgress&&writeProgress.hasExecutionProgress()&&writeProgress.recommendedResumeGlyphId()>0L;
        boolean hasNext=hasProgress&&writeProgress.nextResumeGlyphId()>0L;
        resumeFromSelectionButton.setEnabled(!busy&&hasSelection);
        clearResumeButton.setEnabled(!busy&&hasProgress);
        resumeInterruptedButton.setEnabled(!busy&&hasInterrupted);
        resumeNextButton.setEnabled(!busy&&hasNext);
        if(!hasProgress){
            resumeStatusLabel.setText("续写：未设置（整页书写）");
            resumeStatusLabel.setToolTipText("断线或终止后会自动记录进度，也可选中任意字点击“设为续写”。");
        }else if(writeProgress.pageIndex!=curPage){
            String tip=writeProgress.progressSummary()+"；"+writeProgress.resumeTargetSummary();
            resumeStatusLabel.setText("第 "+(writeProgress.pageIndex+1)+" 页有续写进度");
            resumeStatusLabel.setToolTipText(tip);
        }else{
            String summary=writeProgress.resumeTargetSummary();
            String progress=writeProgress.progressSummary();
            resumeStatusLabel.setText(summary);
            resumeStatusLabel.setToolTipText(progress+"；"+summary);
        }
    }
    private void waitForRecoveryWriter()throws InterruptedException{if(recoveryWorker!=null)try{recoveryWorker.get();}catch(java.util.concurrent.ExecutionException ignored){/* The current synchronous save is retried below. */}}
    public void activatePreviewPage(int page){curPage=Math.max(0,Math.min(page,Math.max(0,geometry().layout.pages.size()-1)));changePage();}
    public boolean prepareEditorClose(){
        finishEditorComposition();try{
            if(appearancePanel!=null&&appearancePanel.hasPending()){message("请等待字形与随机设置应用完成后关闭。");return false;}
            if(awaitingInitialRecovery()){editorClosing=true;recoveryTimer.stop();return true;}
            if(NoteRecovery.closePolicy()==NoteRecovery.ClosePolicy.ASK_SAVE&&isDirty()){
                int answer=JOptionPane.showConfirmDialog(this,"文章有未保存修改，是否保存？","关闭文章",JOptionPane.YES_NO_CANCEL_OPTION);
                if(answer==JOptionPane.CANCEL_OPTION||answer==JOptionPane.CLOSED_OPTION)return false;
                if(answer==JOptionPane.YES_OPTION&&!saveDocumentCurrent())return false;
                if(answer==JOptionPane.NO_OPTION){editorClosing=true;recoveryTimer.stop();waitForRecoveryWriter();NoteRecovery.clear();return true;}
            }
            editorClosing=true;recoveryTimer.stop();waitForRecoveryWriter();NoteRecovery.save(recoverySnapshot());return true;
        }catch(Exception e){editorClosing=false;reportFailure(e);return false;}
    }

    static PageInfo access$0(NoteTab tab){return tab.pageInfo;}
    static void access$1(NoteTab tab){tab.applyFonts();}
}
