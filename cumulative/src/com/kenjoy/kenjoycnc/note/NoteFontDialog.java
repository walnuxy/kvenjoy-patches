package com.kenjoy.kenjoycnc.note;

import com.kenjoy.kenjoycnc.fonts.v;
import com.kenjoy.kenjoycnc.fonts.FontFavorites;
import com.kenjoy.kenjoycnc.g.e;
import com.kenjoy.kenjoycnc.n.q;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;

/** Single-font picker: never writes FontsDialogData or the article's default pool. */
public final class NoteFontDialog extends JDialog {
    private final NoteFonts fonts;
    private final NoteDocument document;
    private final Set<Long> targets;
    private final DefaultListModel<NoteFonts.Entry> model=new DefaultListModel<NoteFonts.Entry>();
    private final JList<NoteFonts.Entry> list=new JList<NoteFonts.Entry>(model);
    private final JTextField search=new JTextField();
    private final JComboBox<String> category=new JComboBox<String>(new String[]{"全部","中文","英文","符号","其他语言","喜好字体"});
    private final JLabel preview=new JLabel("选择字体查看所选字预览",SwingConstants.CENTER),coverage=new JLabel();
    private final JButton confirm=new JButton("应用到所选字");
    private final Path favoritesFile=Paths.get(System.getProperty("user.home"),".KenjoyDraw","font-favorites.properties");
    private final FontFavorites favorites=new FontFavorites(favoritesFile);
    private NoteFonts.Entry result;
    private SwingWorker<BufferedImage,Void> worker;
    private final double uiScale,renderScale;
    private int revision;
    public NoteFontDialog(Window owner,NoteFonts fonts,NoteDocument d,Set<Long> targets) {
        super(owner,"指定所选 "+targets.size()+" 字的字体",ModalityType.APPLICATION_MODAL);this.fonts=fonts;this.document=d;this.targets=targets;
        double scale=1;try{scale=((Number)Class.forName("com.kenjoy.uitrial.NativeDpi").getMethod("scale").invoke(null)).doubleValue();}catch(ReflectiveOperationException ignored){}
        uiScale=scale;renderScale=uiScale*getGraphicsConfiguration().getDefaultTransform().getScaleX();
        setName("NoteSingleFontPicker");setSize(760,560);setLocationRelativeTo(owner);setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        java.util.List<File> fontFiles=new ArrayList<File>();
        for(NoteFonts.Entry entry:fonts.entries())fontFiles.add(entry.file);
        favorites.setFonts(fontFiles);
        JPanel head=new JPanel(new BorderLayout(6,4));head.add(category,BorderLayout.WEST);search.setName("NoteFontSearch");head.add(search,BorderLayout.CENTER);head.add(new JLabel("仅修改捕获的 "+targets.size()+" 个字"),BorderLayout.SOUTH);add(head,BorderLayout.NORTH);
        list.setName("NoteFontCandidates");list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);add(new JScrollPane(list),BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout());preview.setPreferredSize(new Dimension(700,125));bottom.add(preview,BorderLayout.NORTH);bottom.add(coverage,BorderLayout.CENTER);
        JPanel buttons=new JPanel();
        JButton favorite=new JButton("加入 / 取消喜好");favorite.setName("NoteFavoriteToggle");
        favorite.addActionListener(ev->{
            NoteFonts.Entry item=list.getSelectedValue();if(item==null)return;
            try{favorites.toggle(item.file,item.label);filter();}
            catch(IOException e){coverage.setText("保存喜好失败："+e.getMessage());}
        });buttons.add(favorite);
        confirm.setName("NoteApplySelectedFont");confirm.setEnabled(false);confirm.addActionListener(ev->{result=list.getSelectedValue();dispose();});buttons.add(confirm);JButton cancel=new JButton("取消");cancel.addActionListener(ev->dispose());buttons.add(cancel);bottom.add(buttons,BorderLayout.SOUTH);add(bottom,BorderLayout.SOUTH);
        category.addActionListener(ev->filter());search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent ev){filter();}public void removeUpdate(DocumentEvent ev){filter();}public void changedUpdate(DocumentEvent ev){filter();}});
        list.addListSelectionListener(ev->{if(!ev.getValueIsAdjusting())preview();});filter();
    }
    private void filter(){
        try{favorites.reload();}catch(IOException e){coverage.setText("读取喜好失败："+e.getMessage());}
        NoteFonts.Entry selected=list.getSelectedValue();model.clear();String query=search.getText().trim().toLowerCase(Locale.ROOT),kind=(String)category.getSelectedItem();
        for(NoteFonts.Entry entry:fonts.entries()) {
            String folder=entry.folder;String language=folder.startsWith("中文")?"中文":folder.startsWith("英文")?"英文":folder.startsWith("符号")?"符号":"其他语言";
            if(!"全部".equals(kind)&&!("喜好字体".equals(kind)?favorites.isFavorite(entry.file):kind.equals(language)))continue;
            if((entry.label+" "+entry.file.getName()+" "+folder).toLowerCase(Locale.ROOT).contains(query))model.addElement(entry);
        }
        if(selected!=null&&model.contains(selected))list.setSelectedValue(selected,true);
    }
    private void preview(){final NoteFonts.Entry item=list.getSelectedValue();final int ticket=++revision;if(worker!=null)worker.cancel(true);confirm.setEnabled(false);preview.setIcon(null);preview.setText(item==null?"选择字体查看预览":"正在检查所选字…");if(item==null)return;
        worker=new SwingWorker<BufferedImage,Void>() {
            int supported,total;String missing="";
            protected BufferedImage doInBackground()throws Exception {
                v font=item.font();BufferedImage image=new BufferedImage((int)Math.ceil(700*renderScale),(int)Math.ceil(120*renderScale),BufferedImage.TYPE_INT_RGB);Graphics2D out=image.createGraphics();out.scale(renderScale,renderScale);out.setColor(NotePreviewColors.PAPER);out.fillRect(0,0,700,120);out.setColor(NotePreviewColors.INK);out.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);int drawn=0;
                for(NoteDocument.Atom a:document.atoms)if(targets.contains(a.id)) {total++;if(a.text.length()!=1||!font.a(a.text.charAt(0))||font.b(a.text.charAt(0))==null){missing+=a.text+" ";continue;}supported++;
                    if(drawn<6){e path=font.b(a.text.charAt(0)).a().e();q bounds=path.f();float scale=85/Math.max(bounds.a(),bounds.b());path.d(scale,scale);bounds=path.f();path.c(60+drawn*112-(bounds.a+bounds.c)/2,60-(bounds.b+bounds.d)/2);out.draw(NoteLayout.path(Collections.singletonList(path)));drawn++;}
                }
                out.dispose();return image;
            }
            protected void done(){if(ticket!=revision)return;try{preview.setIcon(new PreviewIcon(get()));preview.setText("");coverage.setText("可用 "+supported+" / "+total+" 字"+(missing.isEmpty()?"":"；缺字："+missing));confirm.setEnabled(supported==total&&total>0);}catch(Exception e){coverage.setText("字体无法预览："+e.getMessage());}}
        };worker.execute();
    }
    public NoteFonts.Entry choose(){setVisible(true);return result;}
    private final class PreviewIcon extends ImageIcon {
        PreviewIcon(BufferedImage image){super(image);}
        public int getIconWidth(){return (int)Math.round(700*uiScale);}
        public int getIconHeight(){return (int)Math.round(120*uiScale);}
        public void paintIcon(Component c,Graphics g,int x,int y){g.drawImage(getImage(),x,y,getIconWidth(),getIconHeight(),c);}
    }
    public void dispose(){revision++;if(worker!=null)worker.cancel(true);super.dispose();}
}
