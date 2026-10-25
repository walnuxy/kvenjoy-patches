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
public class NoteTab extends BaseTab implements ActionListener {
    private static final long serialVersionUID=1L;
    private JFormattedTextField tfWidth,tfHeight,tfLeftGap,tfRightGap,tfTopGap,tfBottomGap,tfLines,tfWordGap,tfFontSize,tfCurPage;
    private JComboBox cbSettings;
    private DefaultComboBoxModel model;
    private PageInfo pageInfo;
    private JButton btnPrePage,btnNextPage,btnSetContent,btnSave,btnDelete,btnChooseFont;
    private StringBuffer content;
    private float firstLineOffset;
    private WordContentFormat contentSet;
    private final List pageDatas=new ArrayList();
    private int curPage;
    private JLabel labelMaxPage;
    private g notePanel;
    private t chooseFontListener=new h(this);

    private NoteDocument document=new NoteDocument();
    private NoteLayout layout=new NoteLayout();
    private final NoteSelection selection=new NoteSelection();
    private volatile NoteFonts catalogue;
    private final Deque<NoteDocument> undo=new ArrayDeque<NoteDocument>(),redo=new ArrayDeque<NoteDocument>();
    private SwingWorker<Prepared,Void> pending;
    private SwingWorker<NoteDocument,Void> loading;
    private long request;
    private long idHighWater=1;
    private boolean binding;
    private String savedDocument="",documentState="";
    private Path documentFile;
    private volatile com.kenjoy.kenjoycnc.c.a machineJobGenerator;
    private volatile String machineJobSource;
    private final JLabel selectionLabel=new JLabel("未选字");
    private final JLabel selectionFontLabel=new JLabel(){public Dimension getPreferredSize(){Dimension d=super.getPreferredSize();d.width=1;return d;}public Dimension getMinimumSize(){Dimension d=super.getMinimumSize();d.width=0;return d;}};
    private final JTextArea status=new JTextArea("应用版式保留已选字形；重新随机只影响未指定字体的字。",2,1){public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}};
    private NoteSidebarView sidebar;
    private JButton fontButton,resetButton,undoButton,redoButton,clearMissingButton;
    private JCheckBox randomCopies;
    private volatile String lastFailure;
    private static final class Prepared {
        final NoteDocument document;final NoteLayout layout;
        Prepared(NoteDocument d,NoteLayout l){document=d;layout=l;}
    }
    public NoteTab(KenjoyDraw owner) {
        super(owner);saveName="note";setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));setName("NoteEditControls");
        setBorder(BorderFactory.createEmptyBorder(2,3,4,3));
        JPanel article=section("文章");btnSetContent=button("编辑文章…");btnChooseFont=button("默认字体…");btnChooseFont.setToolTipText("设置文章默认字体与字形选取比例；点击“重新随机字形”后应用到未指定字");
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
        JButton rerandomize=new JButton("重新随机字形");rerandomize.setName("NoteRerandomize");rerandomize.setToolTipText("根据默认字体与配比重新选取未指定字的字形");rerandomize.addActionListener(e->rerandomizeAsync());
        wide(paper,7,buttons(applyLayout,rerandomize));
        JPanel chosen=section("预览选字");btnPrePage=button("上一页");btnNextPage=button("下一页");tfCurPage=number(1);tfCurPage.setColumns(2);tfCurPage.addActionListener(this);labelMaxPage=new JLabel("/0");
        JPanel pageNav=new JPanel(new GridBagLayout());cell(pageNav,0,0,1,0,btnPrePage);cell(pageNav,1,0,1,1,tfCurPage);cell(pageNav,2,0,1,0,labelMaxPage);cell(pageNav,3,0,1,0,btnNextPage);wide(chosen,0,pageNav);
        JToggleButton multi=new JToggleButton("多选：关");multi.setName("NoteMultiSelect");multi.setToolTipText("开启后连续点选，再点已选字可取消");multi.addActionListener(e->{selection.multiple=multi.isSelected();multi.setText(selection.multiple?"多选：开":"多选：关");});
        JButton line=new JButton("选整行");line.addActionListener(e->selectCurrentLine());JButton clear=new JButton("取消选区");clear.addActionListener(e->{selection.clear();selectionChanged();});wide(chosen,1,buttons(multi,line,clear));
        selectionLabel.setName("NoteSelectionCount");wide(chosen,2,selectionLabel);selectionFontLabel.setName("NoteSelectionFont");wide(chosen,3,selectionFontLabel);
        fontButton=new JButton("指定字体…");fontButton.setName("NoteAssignFont");fontButton.setToolTipText("只修改当前所选字的字体");fontButton.addActionListener(e->chooseSelectionFont());
        resetButton=new JButton("恢复默认");resetButton.setToolTipText("取消所选字的手动字体指定");resetButton.addActionListener(e->applyFont(null));wide(chosen,4,buttons(fontButton,resetButton));
        undoButton=new JButton("撤销");undoButton.setName("NoteUndo");undoButton.addActionListener(e->history(false));redoButton=new JButton("重做");redoButton.setName("NoteRedo");redoButton.addActionListener(e->history(true));wide(chosen,5,buttons(undoButton,redoButton));
        clearMissingButton=new JButton("清除红框…");clearMissingButton.setName("NoteClearMissing");clearMissingButton.setToolTipText("处理整篇文章所有缺字红框，可用空格替代或删除，支持撤销");clearMissingButton.addActionListener(e->chooseMissingAction());clearMissingButton.setVisible(false);wide(chosen,6,clearMissingButton);
        randomCopies=new JCheckBox("每份重新随机");randomCopies.setToolTipText("批量导出时每份重新随机未指定字，保留手动指定字体");
        status.setName("NoteEditStatus");status.setAlignmentX(LEFT_ALIGNMENT);status.setEditable(false);status.setLineWrap(true);status.setWrapStyleWord(true);status.setOpaque(false);status.setFont(UIManager.getFont("Label.font"));status.setBorder(BorderFactory.createEmptyBorder(4,4,2,4));
        JScrollPane statusPane=new JScrollPane(status,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER){public Dimension getPreferredSize(){Dimension d=super.getPreferredSize();d.width=1;d.height=status.getFontMetrics(status.getFont()).getHeight()*2+status.getInsets().top+status.getInsets().bottom+4;return d;}public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}};statusPane.setBorder(BorderFactory.createEmptyBorder());statusPane.setAlignmentX(LEFT_ALIGNMENT);add(statusPane);
        binding=true;
        try(Reader in=Files.newBufferedReader(getPageSaveFile().toPath(),StandardCharsets.UTF_8)) {PageInfo[] pages=new Gson().fromJson(in,PageInfo[].class);if(pages!=null)for(PageInfo p:pages)if(p!=null&&p.name!=null)model.addElement(p);}catch(Exception ignored){}
        if(model.getSize()==0){PageInfo p=new PageInfo("默认",210,297,31.8f,31.8f,25.4f,25.4f,30,95,true);p.space=1;model.addElement(p);}
        pageInfo=(PageInfo)model.getElementAt(0);cbSettings.setSelectedIndex(0);binding=false;setPageInfo(pageInfo);applyFonts();
        contentSet=document.format;content=new StringBuffer();supportFiles=new String[]{"kjnote","txt","doc","docx"};savedDocument=documentState=semanticState(document);selectionChanged();
    }
    private JPanel section(String title){JPanel p=new JPanel(new GridBagLayout()){public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}};p.setAlignmentX(LEFT_ALIGNMENT);p.setBorder(BorderFactory.createTitledBorder(title));add(p);return p;}
    private static JPanel buttons(AbstractButton... buttons){JPanel p=new JPanel(new GridLayout(1,buttons.length,4,0));for(AbstractButton b:buttons){b.setMargin(new Insets(3,5,3,5));p.add(b);}return p;}
    private static void cell(JPanel p,int x,int y,int span,double weight,Component c){GridBagConstraints g=new GridBagConstraints();g.gridx=x;g.gridy=y;g.gridwidth=span;g.weightx=weight;g.fill=GridBagConstraints.HORIZONTAL;g.anchor=GridBagConstraints.WEST;g.insets=new Insets(2,3,2,3);p.add(c,g);}
    private static void wide(JPanel p,int row,Component c){cell(p,0,row,3,1,c);}
    private static void form(JPanel p,int row,String label,Component a,Component b){cell(p,0,row,1,0,new JLabel(label));cell(p,1,row,1,1,a);cell(p,2,row,1,1,b);}
    public Dimension getPreferredSize(){Dimension d=super.getPreferredSize();d.width=Math.max(d.width,Math.max(250,getFontMetrics(getFont()).charWidth('字')*18+16));return d;}
    private JButton button(String text){JButton b=new JButton(text);b.addActionListener(this);return b;}
    private JFormattedTextField number(double value){JFormattedTextField f=new JFormattedTextField(java.text.NumberFormat.getNumberInstance());f.setColumns(4);f.setValue(value);return f;}
    private float value(JFormattedTextField f) throws Exception {f.commitEdit();Object n=f.getValue();float v=n instanceof Number?((Number)n).floatValue():Float.parseFloat(f.getText());if(!Float.isFinite(v))throw new IOException("数值必须为有限值");return v;}
    private static PageInfo copyPage(PageInfo p){return new Gson().fromJson(new Gson().toJson(p),PageInfo.class);}
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
    private void applyFonts(){FontsDialogData f=(FontsDialogData)Storage.getStore(FontsDialogData.class);pageInfo.fontPath=f.fontsName==null?null:f.fontsName.clone();pageInfo.fontChance=f.chance==null?null:f.chance.clone();message("默认字体已载入；点击“重新随机字形”用于未指定的字。");}
    private synchronized NoteFonts fonts() throws Exception {if(catalogue==null)catalogue=NoteFonts.installed();return catalogue;}
    private void refreshFonts(){if(isBusy()){message("请等待排版完成后刷新字库。");return;}message("正在扫描本地字库…");new SwingWorker<NoteFonts,Void>(){protected NoteFonts doInBackground()throws Exception{return NoteFonts.installed();}protected void done(){try{catalogue=get();message("字库已刷新，共 "+catalogue.entries().size()+" 个字体。");}catch(Exception e){failure(e);}}}.execute();}
    private void message(String text){if(layout!=null)text+=layout.notice();status.setText(text);status.setCaretPosition(0);status.setToolTipText("<html>"+escape(text)+"</html>");}
    private static String escape(String t){return t==null?"":t.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\n","<br>");}
    private void failure(Throwable error){while(error.getCause()!=null)error=error.getCause();lastFailure=error.getMessage()==null?error.toString():error.getMessage();message(lastFailure);System.err.println("NOTE_EDIT_ERROR "+lastFailure);}
    public void processAsync(){rerandomizeAsync();}
    public void applyLayoutAsync(){if(!SwingUtilities.isEventDispatchThread()){SwingUtilities.invokeLater(this::applyLayoutAsync);return;}if(isBusy()){message("请等待当前文章修改完成。");return;}try{NoteDocument d=document.copy();d.page=readPage();submit(d,true,false);}catch(Exception e){failure(e);}}
    public void rerandomizeAsync(){if(!SwingUtilities.isEventDispatchThread()){SwingUtilities.invokeLater(this::rerandomizeAsync);return;}if(isBusy()){message("请等待当前文章修改完成。");return;}try{NoteDocument d=document.copy();d.page=readPage();d.pool.clear();d.chances=d.page.fontChance==null?null:d.page.fontChance.clone();d.shapes=NoteDocument.ShapeSettings.capture((FontsSetData)Storage.getStore(FontsSetData.class));d.rerandomize();submit(d,true,false);}catch(Exception e){failure(e);}}
    public void process(){processAsync();}
    private SwingWorker<Prepared,Void> submit(final NoteDocument candidate,final boolean history,final boolean newDocument){return submit(candidate,history,newDocument,null);}
    private SwingWorker<Prepared,Void> submit(final NoteDocument candidate,final boolean history,final boolean newDocument,final Runnable success) {return submit(candidate,history,newDocument,success,null);}
    private SwingWorker<Prepared,Void> submit(final NoteDocument candidate,final boolean history,final boolean newDocument,final Runnable success,final Runnable onFailure) {
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("文章提交必须在EDT发起");
        if(isBusy()){message("请等待当前文章修改完成。");return null;}
        final long ticket=++request;lastFailure=null;message("正在校验字体并重新排版…");
        final float dpi=Settings.getInst().dpi;
        pending=new SwingWorker<Prepared,Void>() {
            protected Prepared doInBackground() throws Exception {
                NoteFonts f=fonts();
                boolean needPool=false;for(NoteDocument.Atom a:candidate.atoms)if(a.drawable()&&a.override==null&&a.automatic==null)needPool=true;
                if(candidate.pool.isEmpty()&&needPool){candidate.pool=f.byNames(candidate.page.fontPath);candidate.chances=candidate.page.fontChance==null?null:candidate.page.fontChance.clone();}
                if(candidate.page.fontPath==null){candidate.page.fontPath=new String[candidate.pool.size()];for(int i=0;i<candidate.pool.size();i++)candidate.page.fontPath[i]=new File(candidate.pool.get(i).relativePath).getName();candidate.page.fontChance=candidate.chances;}
                return new Prepared(candidate,new NoteLayoutEngine(f,dpi).build(candidate));
            }
            protected void done() {
                if(ticket!=request)return;
                boolean committed=false;
                try {Prepared p=get();String before=document.encode();long anchor=!layout.pages.isEmpty()&&!layout.pages.get(curPage).glyphs.isEmpty()?layout.pages.get(curPage).glyphs.get(0).id:-1;
                    if(history&&!before.equals(p.document.encode())){undo.push(document.copy());while(undo.size()>30)undo.removeLast();redo.clear();}
                    if(newDocument)idHighWater=p.document.nextId;else idHighWater=Math.max(idHighWater,p.document.nextId);p.document.nextId=idHighWater;
                    document=p.document;layout=p.layout;documentState=semanticState(document);if(newDocument){curPage=0;selection.clear();undo.clear();redo.clear();savedDocument=documentState;setPageInfo(document.page);}else selection.retain(document);
                    content=new StringBuffer(document.text);contentSet=document.format;pageDatas.clear();for(NoteLayout.Page page:layout.pages)pageDatas.add(new com.kenjoy.kenjoycnc.l.f(0,page.paths,Collections.emptyList()));
                    if(anchor>=0&&!newDocument){int index=layout.pageOf(anchor);if(index>=0)curPage=index;}
                    changePage();message("已更新。"+(isDirty()?"文章有未保存修改。":""));committed=true;if(success!=null)success.run();
                }catch(CancellationException ignored){}catch(Exception error){failure(error);if(!committed&&onFailure!=null)onFailure.run();}finally{if(ticket==request){pending=null;selectionChanged();}}
            }
        };selectionChanged();pending.execute();return pending;
    }
    private void changePage(){curPage=Math.max(0,Math.min(curPage,layout.pages.size()-1));
        if(notePanel!=null){NoteLayout.Page page=layout.pages.isEmpty()?new NoteLayout.Page():layout.pages.get(curPage);notePanel.bind(page,selection,this::selectionChanged,this::chooseSelectionFont,()->applyFont(null),document.page.pageWidth>0?document.page:pageInfo);}
        labelMaxPage.setText("/"+layout.pages.size());tfCurPage.setValue(layout.pages.isEmpty()?0:curPage+1);btnPrePage.setEnabled(curPage>0);btnNextPage.setEnabled(curPage+1<layout.pages.size());selectionChanged();}
    private void cacluteAllPaths(){processAsync();}
    private void selectionChanged(){updateMissingButton();Set<String> names=new LinkedHashSet<String>();int explicit=0;for(NoteDocument.Atom a:document.atoms)if(selection.ids().contains(a.id)){NoteDocument.FontRef f=a.override!=null?a.override:a.automatic;if(f!=null)names.add(f.name);if(a.override!=null)explicit++;}String detail=names.size()>1?"混合字体":names.isEmpty()?"点选、拖框或选择整行":names.iterator().next();selectionLabel.setText("已选 "+selection.ids().size()+" 字 · 指定 "+explicit+" 字"+(isDirty()?" · 未保存":""));selectionFontLabel.setText(detail);selectionFontLabel.setToolTipText(detail);boolean valid=!selection.ids().isEmpty()&&!isBusy();fontButton.setEnabled(valid);resetButton.setEnabled(valid);undoButton.setEnabled(!undo.isEmpty()&&!isBusy());redoButton.setEnabled(!redo.isEmpty()&&!isBusy());setPageInputsEnabled(!isBusy());if(notePanel!=null)notePanel.repaint();}
    private void setPageInputsEnabled(boolean enabled){cbSettings.setEnabled(enabled);for(JFormattedTextField field:new JFormattedTextField[]{tfWidth,tfHeight,tfLeftGap,tfRightGap,tfTopGap,tfBottomGap,tfLines,tfWordGap,tfFontSize})field.setEnabled(enabled);btnSave.setEnabled(enabled);btnDelete.setEnabled(enabled);btnChooseFont.setEnabled(enabled);}
    private void selectCurrentLine(){if(layout.pages.isEmpty())return;NoteLayout.Page page=layout.pages.get(curPage);for(NoteLayout.Glyph g:page.glyphs)if(selection.ids().contains(g.id)){selection.add(page.lines.get(g.line).ids,false);selectionChanged();return;}message("先点选行内任意一个字，或点击预览左侧 › 选整行。");}
    private void updateMissingButton(){boolean visible=!layout.missing.isEmpty();if(clearMissingButton.isVisible()!=visible){clearMissingButton.setVisible(visible);revalidate();repaint();}clearMissingButton.setEnabled(visible&&!isBusy());}
    private void chooseMissingAction(){
        if(isBusy()||layout.missing.isEmpty())return;
        Object[] options={"用空格替代","全部删除","取消"};
        int action=JOptionPane.showOptionDialog(this,"处理整篇文章的所有红框字符；正常文字和已补全的符号保持不变。\n用空格替代：每个红框改为一个空格。\n全部删除：删除红框字符并重新排版。两种操作都可以撤销。","清除红框",JOptionPane.DEFAULT_OPTION,JOptionPane.QUESTION_MESSAGE,null,options,options[0]);
        if(action==0||action==1)clearMissingCharacters(action==0);
    }
    public void clearMissingCharacters(boolean spaces){
        if(!SwingUtilities.isEventDispatchThread()){SwingUtilities.invokeLater(()->clearMissingCharacters(spaces));return;}
        if(isBusy()||layout.missing.isEmpty())return;
        Set<Long> ids=new HashSet<Long>();for(NoteLayout.Page page:layout.pages)for(NoteLayout.Glyph glyph:page.glyphs)if(glyph.path.a().length==0)ids.add(glyph.id);
        try{NoteDocument candidate=document.copy();candidate.clearMissing(ids,spaces);submit(candidate,true,false,()->message("已"+(spaces?"用空格替代":"删除")+ids.size()+" 个红框字符，可撤销。"));}catch(Exception error){failure(error);}
    }
    public void applyFont(NoteDocument.FontRef font){if(selection.ids().isEmpty()){message("请先在预览中选字。");return;}NoteDocument d=document.copy();Set<Long> targets=selection.capture();for(NoteDocument.Atom a:d.atoms)if(targets.contains(a.id))a.override=font;submit(d,true,false);}
    private void history(boolean forward){Deque<NoteDocument> source=forward?redo:undo,target=forward?undo:redo;if(source.isEmpty()||isBusy())return;NoteDocument before=document.copy(),candidate=source.peek().copy();
        candidate.nextId=Math.max(candidate.nextId,idHighWater);submit(candidate,false,false,()->{source.pop();target.push(before);setPageInfo(document.page);selectionChanged();});
    }
    private static String semanticState(NoteDocument d){com.google.gson.JsonObject state=new Gson().toJsonTree(d).getAsJsonObject();state.remove("nextId");return state.toString();}
    public boolean isDirty(){return !documentState.equals(savedDocument);}
    public boolean hasUnappliedPageSettings(){try{return !samePageSettings(readPage(),document.page);}catch(Exception error){return true;}}
    private static boolean samePageSettings(PageInfo a,PageInfo b){return java.util.Objects.equals(a.name,b.name)&&Float.compare(a.pageWidth,b.pageWidth)==0&&Float.compare(a.pageHeight,b.pageHeight)==0&&Float.compare(a.leftGap,b.leftGap)==0&&Float.compare(a.rightGap,b.rightGap)==0&&Float.compare(a.topGap,b.topGap)==0&&Float.compare(a.bottomGap,b.bottomGap)==0&&a.lineNum==b.lineNum&&Float.compare(a.space,b.space)==0&&Float.compare(a.fontSize,b.fontSize)==0&&Arrays.equals(a.fontPath,b.fontPath)&&Arrays.equals(a.fontChance,b.fontChance);}
    public boolean isBusy(){return pending!=null||loading!=null;}
    public String lastFailure(){return lastFailure;}
    public NoteDocument document(){return document.copy();}
    public NoteLayout noteLayout(){return layout;}
    public NoteSelection selection(){return selection;}
    public NoteFonts catalogue() throws Exception{return fonts();}
    public boolean randomCopies(){return randomCopies.isSelected();}
    public void setRandomCopies(boolean selected){randomCopies.setSelected(selected);}
    public void setDocument(NoteDocument d){submit(d.copy(),false,true);}
    public void saveDocument(Path file) throws IOException{if(isBusy())throw new IOException("请等待排版完成后保存");if(hasUnappliedPageSettings())throw new IOException("页面或默认字体设置尚未应用；请先点击“应用版式”并等待排版完成。");document.save(file);documentFile=file;savedDocument=semanticState(document);selectionChanged();message("文章已保存："+file.getFileName());}
    public boolean saveDocumentCurrent(){if(isBusy()){message("请等待排版完成后保存。");return false;}try{if(documentFile==null)return saveDocumentDialog();saveDocument(documentFile);return true;}catch(Exception e){failure(e);return false;}}
    private boolean saveDocumentDialog(){if(isBusy())return false;if(hasUnappliedPageSettings()){message("页面或默认字体设置尚未应用；请先点击“应用版式”并等待排版完成。");return false;}try{File chosen=NoteFileDialogs.saveArticle(this,documentFile==null?null:documentFile.toFile());if(chosen!=null){saveDocument(chosen.toPath());return true;}}catch(Exception e){failure(e);}return false;}
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
        final WordContentFormat importedFormat=new Gson().fromJson(new Gson().toJson(document.format),WordContentFormat.class);
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
                    Runnable committed=()->{documentFile=nativeArticle?f.toPath():null;if(!nativeArticle){savedDocument="";selectionChanged();message("已导入文章，请保存为 .kjnote："+f.getName());}if(onSuccess!=null)onSuccess.run();};
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
    private void chooseSelectionFont(){if(isBusy()||selection.ids().isEmpty())return;if(catalogue==null){message("字库仍在载入，请稍候。");return;}final long revision=request;final Set<Long> captured=selection.capture();
        NoteFontDialog dialog=new NoteFontDialog(SwingUtilities.getWindowAncestor(this),catalogue,document,captured);
        NoteFonts.Entry chosen=dialog.choose();if(chosen==null)return;if(revision!=request){message("文章已变化，请重新选择要修改的字。");return;}
        NoteDocument candidate=document.copy();SwingWorker<Prepared,Void> worker;
        try {NoteDocument.FontRef ref=catalogue.ref(chosen);for(NoteDocument.Atom a:candidate.atoms)if(captured.contains(a.id))a.override=ref;submit(candidate,true,false);}catch(Exception e){failure(e);}
    }
    private void editContent(){if(isBusy()){message("请等待当前文章修改完成。");return;}final NoteDocument draft=document.copy();JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(this),"编辑文章",Dialog.ModalityType.APPLICATION_MODAL);dialog.setSize(760,560);
        JTextArea text=new JTextArea(draft.text);text.setLineWrap(true);text.setWrapStyleWord(true);text.setName("NoteArticleText");JLabel feedback=new JLabel();
        ((AbstractDocument)text.getDocument()).setDocumentFilter(new DocumentFilter(){
            public void insertString(FilterBypass b,int o,String s,AttributeSet a)throws BadLocationException{replace(b,o,0,s,a);}
            public void remove(FilterBypass b,int o,int n)throws BadLocationException{replace(b,o,n,"",null);}
            public void replace(FilterBypass b,int o,int n,String s,AttributeSet a)throws BadLocationException{String value=NoteDocument.normalize(s==null?"":s);try{draft.replace(o,n,value);b.replace(o,n,value,a);feedback.setText("");}catch(IllegalArgumentException tooLong){feedback.setText("文章最多 10 万字，请缩短后再粘贴。");java.awt.Toolkit.getDefaultToolkit().beep();}}
        });
        JPanel bar=new JPanel();JCheckBox paragraphs=new JCheckBox("自动段落缩进",draft.format.autoGp);JSpinner indent=new JSpinner(new SpinnerNumberModel(draft.format.gpNum,1,5,1));JComboBox<LineAlign> align=new JComboBox<LineAlign>(LineAlign.values());align.setSelectedItem(draft.format.firstLineAlign);bar.add(paragraphs);bar.add(indent);bar.add(align);
        JComboBox<String> charset=new JComboBox<String>(new String[]{"UTF-8","GBK"});charset.setSelectedItem(draft.format.charset);bar.add(charset);
        JButton ok=new JButton("确定文章"),cancel=new JButton("取消");ok.setName("NoteConfirmContent");
        JButton importText=new JButton("导入 TXT / Word…");importText.setName("NoteImportText");bar.add(importText);
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
    public boolean onNoteContentSet(StringBuffer text,WordContentFormat format){if(!SwingUtilities.isEventDispatchThread()||isBusy())return false;NoteDocument d=document.copy();d.replace(0,d.text.length(),text.toString());d.format=new Gson().fromJson(new Gson().toJson(format),WordContentFormat.class);final boolean[] committed={false};SecondaryLoop loop=Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();SwingWorker<Prepared,Void> work=submit(d,true,false,()->committed[0]=true);if(work==null)return false;work.addPropertyChangeListener(e->{if(e.getNewValue()==SwingWorker.StateValue.DONE)loop.exit();});loop.enter();return committed[0];}
    public void actionPerformed(ActionEvent ev){Object source=ev.getSource();if(source==btnSave)save();else if(source==btnDelete)delete();else if(source==btnChooseFont)r.a(chooseFontListener).a();else if(source==btnSetContent)editContent();else if(source==btnPrePage){curPage--;changePage();}else if(source==btnNextPage){curPage++;changePage();}else if(source==tfCurPage){try{curPage=(int)value(tfCurPage)-1;changePage();}catch(Exception e){failure(e);}}else if(source==cbSettings&&!binding&&cbSettings.getSelectedItem() instanceof PageInfo)setPageInfo((PageInfo)cbSettings.getSelectedItem());}
    public Component createCanvas(){notePanel=new g();changePage();return notePanel;}
    public void onActive(){sidebar=NoteSidebarView.attach(this);changePage();}
    public void onDeactive(){if(sidebar!=null){sidebar.detach();sidebar=null;}/* Submitted edits still belong to this tab. */}
    public String toString(){return "笔记";}
    public ImageIcon getIcon(){return new ImageIcon(L.b("note.png"));}
    public void setScale(float scale){}
    public String getDescription(){return "支持 KJNOTE、TXT、DOC 和 DOCX 文章；导入的文本请保存为 KJNOTE。";}
    public String enableExport(){return "kjnote";}
    public boolean enablePdf(){return true;}
    public byte[] getExportData(){return document.encode().getBytes(StandardCharsets.UTF_8);}
    public int getPageNum(){return exportLayout().pages.size();}
    public int getCurrentPageIndex(){return curPage;}
    public String currentPageSource(){return "笔记当前页 "+(layout.pages.isEmpty()?0:curPage+1)+"/"+layout.pages.size();}
    public boolean beginMachineJob(){if(isBusy()||layout.pages.isEmpty()||layout.pages.get(curPage).paths.isEmpty())return false;com.kenjoy.kenjoycnc.c.a generator=getGcodeGenerator();if(generator==null)return false;machineJobSource=currentPageSource();machineJobGenerator=generator;return true;}
    public void endMachineJob(){machineJobGenerator=null;machineJobSource=null;}
    public String machineJobSource(){return machineJobSource;}
    public NoteLayout exportLayout(){NoteExportHooks.Job job=NoteExportHooks.current(this);return job==null?layout:job.layout;}
    public com.kenjoy.kenjoycnc.c.a getGcodeGenerator(){NoteExportHooks.Job job=NoteExportHooks.current(this);if(job!=null)return job.generator(job.pageIndex);if(machineJobGenerator!=null)return machineJobGenerator;if(layout.pages.isEmpty()||isBusy())return null;return new com.kenjoy.kenjoycnc.c.c(notePanel.a(L.b(layout.pages.get(curPage).paths)),Settings.getInst().nearDst,false);}
    public List getExportGcodeGenerator(){NoteExportHooks.Job job=NoteExportHooks.current(this);if(job!=null)return job.generators();List result=new ArrayList();if(isBusy())return result;for(NoteLayout.Page p:layout.pages)result.add(new com.kenjoy.kenjoycnc.c.c(notePanel.a(L.b(p.paths)),Settings.getInst().nearDst,false));return result;}
    public BufferedImage renderToImage(int page){NoteExportHooks.Job job=NoteExportHooks.current(this);NoteLayout l=job==null?layout:job.layout;NoteDocument d=job==null?document:job.document;return image(l,d,page,job==null?Settings.getInst().dpi:job.dpi);}
    public static BufferedImage image(NoteLayout layout,NoteDocument d,int index,float dpi){if(index<0||index>=layout.pages.size())throw new IllegalArgumentException("页码不存在");double width=d.page.pageWidth*(double)dpi/25.4,height=d.page.pageHeight*(double)dpi/25.4;if(!Double.isFinite(width)||!Double.isFinite(height)||width<1||height<1||width>12000||height>12000||width*height>24_000_000)throw new IllegalArgumentException("导出页面尺寸过大（最多 2400 万像素）");int w=(int)Math.round(width),h=(int)Math.round(height);BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);Graphics2D out=img.createGraphics();out.setColor(Color.WHITE);out.fillRect(0,0,w,h);out.setColor(Color.BLACK);out.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);out.draw(NoteLayout.path(layout.pages.get(index).paths));out.dispose();return img;}
    public List machinePaths(NoteLayout.Page page){return notePanel.a(L.b(page.paths));}
    public java.awt.geom.AffineTransform machineTransform(){com.kenjoy.kenjoycnc.g.e basis=new com.kenjoy.kenjoycnc.g.e();basis.a(0,0);basis.b(1024,0);basis.b(0,1024);List result=notePanel.a(Collections.singletonList(basis));float[] xy=((com.kenjoy.kenjoycnc.g.e)result.get(0)).a();return new java.awt.geom.AffineTransform((xy[2]-xy[0])/1024,(xy[3]-xy[1])/1024,(xy[4]-xy[0])/1024,(xy[5]-xy[1])/1024,xy[0],xy[1]);}
    static PageInfo access$0(NoteTab tab){return tab.pageInfo;}
    static void access$1(NoteTab tab){tab.applyFonts();}
}
