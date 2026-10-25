package com.kenjoy.kenjoycnc.note;

import com.kenjoy.kenjoycnc.fonts.v;
import com.kenjoy.kenjoycnc.g.e;
import com.kenjoy.kenjoycnc.n.q;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.*;

/** Note-specific layout. Shared legacy g.a state is deliberately not used. */
public final class NoteLayoutEngine {
    private final NoteFonts fonts;
    private final float dpi;
    private final Set<Long> unavailable=new HashSet<Long>();
    private final Set<Long> punctuationFallback=new HashSet<Long>();
    private static boolean invisible(String text) {return text.codePoints().allMatch(c->Character.getType(c)==Character.FORMAT);}
    private static boolean builtin(char ch) {return "·•‧∙⋅…—–−".indexOf(ch)>=0;}
    public NoteLayoutEngine(NoteFonts fonts,float dpi) {this.fonts=fonts;this.dpi=dpi;}
    private float px(float mm) {return mm*dpi/25.4f;}
    private static boolean finite(float f) {return !Float.isNaN(f)&&!Float.isInfinite(f);}
    private float sample(Random r,float[] range) throws IOException {if(range==null||range.length!=2||!finite(range[0])||!finite(range[1]))throw new IOException("随机参数无效");return range[0]+r.nextFloat()*(range[1]-range[0]);}
    private Random random(NoteDocument d,long id) {return new Random(id*0x9e3779b97f4a7c15L+d.generation*0x632be59bd9b4e019L);}
    private float lineOffset(NoteDocument d,long anchor){return random(d,anchor^0x51edL).nextFloat()*px(d.shapes.randomFirstPos);}
    private void prepare(NoteDocument d) throws Exception {
        unavailable.clear();punctuationFallback.clear();
        List<NoteFonts.Entry> pool=new ArrayList<NoteFonts.Entry>();
        boolean needPool=false;for(NoteDocument.Atom a:d.atoms)if(a.drawable()&&a.override==null&&a.automatic==null)needPool=true;
        if(needPool){for(NoteDocument.FontRef f:d.pool)pool.add(fonts.resolve(f));if(pool.isEmpty())throw new IOException("请先选择文章默认字体");}
        StringBuilder missing=new StringBuilder();
        for(NoteDocument.Atom a:d.atoms) {
            if(invisible(a.text))continue;
            if(a.text.length()!=1) {unavailable.add(a.id);continue;}
            char ch=a.text.charAt(0);if(Character.isWhitespace(ch)||Character.isSpaceChar(ch))continue;
            Random r=random(d,a.id);
            if(a.override==null && a.automatic==null) {
                List<Integer> eligible=new ArrayList<Integer>();int max=0;
                for(int i=0;i<pool.size();i++)if(pool.get(i).font().a(ch)) {eligible.add(i);max=Math.max(max,d.chances!=null&&i<d.chances.length?d.chances[i]:0);}
                if(eligible.isEmpty()){if(builtin(ch))punctuationFallback.add(a.id);else unavailable.add(a.id);continue;}
                // Match legacy priority thresholds rather than silently changing probability semantics.
                int threshold=max>0?1+r.nextInt(max):0;List<Integer> choices=new ArrayList<Integer>();
                for(int i:eligible)if(max==0 || (d.chances!=null&&i<d.chances.length?d.chances[i]:0)>=threshold)choices.add(i);
                int index=choices.get(r.nextInt(choices.size()));a.automatic=d.pool.get(index);
            }
            NoteDocument.FontRef chosen=a.override!=null?a.override:a.automatic;
            v font=fonts.resolve(chosen).font();
            if(!font.a(ch)||font.b(ch)==null){if(a.override!=null)missing.append(ch).append("（").append(chosen.name).append("） ");else if(builtin(ch))punctuationFallback.add(a.id);else unavailable.add(a.id);continue;}
            if(!a.sampled) {
                a.size=sample(r,d.shapes.randomFontSize);a.scaleX=sample(r,d.shapes.randomXScale);a.scaleY=sample(r,d.shapes.randomYScale);
                a.rotate=sample(r,d.shapes.randomRotate);a.space=sample(r,d.shapes.randomSpace);a.y=(r.nextFloat()*2-1)*d.shapes.randomY;a.sampled=true;
            }
            if(!finite(a.size)||!finite(a.scaleX)||!finite(a.scaleY)||!finite(a.rotate)||!finite(a.space)||!finite(a.y)||a.size<=0||a.scaleX<=0||a.scaleY<=0||a.space<0)
                throw new IOException("字符随机尺寸或间距无效："+a.text);
        }
        if(missing.length()>0)throw new IOException("无法生成以下字符，未修改文章：\n"+missing.substring(0,Math.min(600,missing.length())));
    }
    private static final class Form {e path;float width,shift;NoteDocument.FontRef font;}
    private Form form(NoteDocument d,NoteDocument.Atom a,float height) throws Exception {
        Form f=new Form();char ch=a.text.charAt(0);float size=d.page.fontSize/100f;
        if(invisible(a.text))return f;
        if(unavailable.contains(a.id)){f.width=height*size;f.path=new e();return f;}
        if(punctuationFallback.contains(a.id)) {
            float unit=height*size;f.path=new e();f.width=unit*(ch=='…'||ch=='—'?1f:.5f);
            float y=-unit*.3f;
            if("—–−".indexOf(ch)>=0){f.path.a(unit*.05f,y);f.path.b(f.width-unit*.05f,y);}
            else {int count=ch=='…'?3:1;float radius=unit*(ch=='•'?.065f:.025f);for(int n=0;n<count;n++){float cx=f.width*(n+.5f)/count;for(int k=0;k<=12;k++){double angle=k*Math.PI/6;float x=cx+radius*(float)Math.cos(angle),py=y+radius*(float)Math.sin(angle);if(k==0)f.path.a(x,py);else f.path.b(x,py);}}}
            return f;
        }
        if(ch=='\t'){f.width=height*size*0.3f+px(d.shapes.tabWidthAdd);return f;}
        if(Character.isWhitespace(ch)||Character.isSpaceChar(ch)){f.width=height*size*0.3f+px(d.shapes.spaceWidthAdd);return f;}
        f.font=a.override!=null?a.override:a.automatic;v font=fonts.resolve(f.font).font();f.path=font.b(ch).a().e();
        float scale=height/font.d*a.size*size;f.path.d(scale*a.scaleX,scale*a.scaleY);f.path.a(a.rotate,0,0);
        q bounds=f.path.f();f.shift=Math.max(0,-bounds.a);f.width=bounds.a()+Math.max(0,bounds.a);
        if(!finite(f.width)||f.width<0)throw new IOException("字形尺寸无效："+a.text);
        if(f.width==0)f.width=Math.max(2,height*size*.3f);
        if(d.shapes.chars!=null&&d.shapes.chars.indexOf(ch)>=0){f.shift+=px(d.shapes.widthAdd);f.width+=px(d.shapes.widthAdd);}
        if(d.shapes.simple>0)f.path.a(d.shapes.simple);
        return f;
    }
    public NoteLayout build(NoteDocument d) throws Exception {
        float left=px(d.page.leftGap),top=px(d.page.topGap),right=px(d.page.pageWidth-d.page.rightGap),bottom=px(d.page.pageHeight-d.page.bottomGap);
        if(!finite(dpi)||dpi<=0||!finite(left)||!finite(top)||!finite(right)||!finite(bottom)||left<0||top<0||right<=left||bottom<=top||!finite(d.page.fontSize)||d.page.fontSize<=0)
            throw new IOException("页面尺寸、边距或字体大小无效");
        int lines=d.page.lineNum;float height=lines>0?(bottom-top)/lines:px(d.page.lineGap),gap=lines>0?0:px(d.page.lineGapOffset);
        if(!finite(height)||!finite(gap)||height<=0||height+gap<=0)throw new IOException("行距无效");
        if(lines<=0)lines=(int)((bottom-top)/(height+gap));if(lines<=0)throw new IOException("页面容不下一行文字");
        prepare(d);NoteLayout result=new NoteLayout();for(NoteDocument.Atom a:d.atoms){if(unavailable.contains(a.id))result.missing.add(a.text);if(punctuationFallback.contains(a.id))result.fallback.add(a.text);}if(d.atoms.isEmpty())return result;
        NoteLayout.Page page=new NoteLayout.Page();result.pages.add(page);int line=0;float x=left;boolean paragraph=true,first=true;
        String punctuation=",，;；.。!！?？>》}]】)）、…”°%：:";
        for(int i=0;i<d.atoms.size();i++) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedException("排版已取消");
            NoteDocument.Atom atom=d.atoms.get(i);char ch=atom.text.charAt(0);
            if(invisible(atom.text))continue;
            if(ch=='\n'||ch=='\f') {first=false;if(ch=='\f'){page=new NoteLayout.Page();result.pages.add(page);line=0;}else line++;paragraph=true;continue;}
            if(line>=lines){page=new NoteLayout.Page();result.pages.add(page);line=0;paragraph=true;}
            if(paragraph) {
                x=left+(first?0:lineOffset(d,atom.id));
                if(first && d.format.firstLineAlign!=null) {
                    String align=d.format.firstLineAlign.name();
                    if(align.equals("CENTER")||align.equals("RIGHT")) {
                        float width=0;for(int j=i;j<d.atoms.size()&&!d.atoms.get(j).text.equals("\n")&&!d.atoms.get(j).text.equals("\f");j++){NoteDocument.Atom z=d.atoms.get(j);if(invisible(z.text))continue;Form f=form(d,z,height);width+=f.width+px(d.page.space)*z.space;if(width>right-left)break;}
                        if(width<=right-left)x+=align.equals("CENTER")?(right-left-width)/2:(right-left-width);
                    } else if(align.equals("TAB"))x+=d.format.gpNum*(height*d.page.fontSize/100f*0.3f+px(d.shapes.tabWidthAdd));
                } else if(d.format.autoGp)x+=d.format.gpNum*(height*d.page.fontSize/100f*0.3f+px(d.shapes.tabWidthAdd));
                paragraph=false;first=false;
            }
            Form f=form(d,atom,height);
            if(f.width>right-left)throw new IOException("单字宽于可用行宽："+atom.text+"。请减小字号或调整页面。");
            float groupWidth=f.width;
            for(int j=i+1;j<d.atoms.size()&&punctuation.indexOf(d.atoms.get(j).text.charAt(0))>=0;j++){float next=px(d.page.space)*d.atoms.get(j-1).space+form(d,d.atoms.get(j),height).width;if(groupWidth+next>right-left)break;groupWidth+=next;}
            if(x+groupWidth>right) {
                line++;x=left+lineOffset(d,atom.id);if(line>=lines){page=new NoteLayout.Page();result.pages.add(page);line=0;}
            }
            if(x+f.width>right)x=left;
            float y=top+line*(height+gap);
            while(page.lines.size()<=line)page.lines.add(new NoteLayout.Line(page.lines.size(),left,top+page.lines.size()*(height+gap),right-left,height));
            if(f.path!=null) {
                f.path.c(x+f.shift,y+height*0.75f*d.shapes.baseLine+atom.y*height);
                NoteLayout.Glyph glyph=new NoteLayout.Glyph(atom.id,atom.text,line,new Rectangle2D.Float(x,y,Math.max(f.width,2),height),f.path,f.font);
                page.glyphs.add(glyph);if(!unavailable.contains(atom.id))page.paths.add(f.path);page.lines.get(line).ids.add(atom.id);
            }
            x+=f.width+px(d.page.space)*atom.space;
        }
        return result;
    }
}
