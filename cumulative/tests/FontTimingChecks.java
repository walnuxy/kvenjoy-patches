package com.kenjoy.kenjoycnc.fonts;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
/** Separate filesystem, header, sample decode and EDT costs on the same library. */
public final class FontTimingChecks {
  static double ms(long n){return (System.nanoTime()-n)/1e6;}
  public static void main(String[] args)throws Exception{
    for(int run=0;run<3;run++){
      long start=System.nanoTime(); java.util.List<Path> files=new ArrayList<>();
      try(java.util.stream.Stream<Path> s=Files.walk(Paths.get(args[0]))){s.filter(p->Files.isRegularFile(p)&&p.toString().endsWith(".gfont")).sorted().forEach(files::add);}
      double scan=ms(start),parse=0,preview=0;java.util.List<v> fonts=new ArrayList<>();
      for(Path p:files){long n=System.nanoTime();v header=new v();try(java.io.InputStream in=Files.newInputStream(p)){header.a(in);}parse+=ms(n);header.f();
        n=System.nanoTime();v f=new v();try(java.io.InputStream in=Files.newInputStream(p)){f.b(in);}if(f.d()!=null)f.d().a();preview+=ms(n);fonts.add(f);}
      start=System.nanoTime();SwingUtilities.invokeAndWait(()->{DefaultListModel model=new DefaultListModel();JList list=new JList(model);list.setCellRenderer(new com.kenjoy.kenjoycnc.l.a());for(v f:fonts)model.addElement(f);list.setSize(650,600);BufferedImage b=new BufferedImage(650,600,2);Graphics2D g=b.createGraphics();list.paint(g);g.dispose();});
      System.out.printf(java.util.Locale.ROOT,"run=%d files=%d scan_ms=%.2f header_ms=%.2f preview_parse_ms=%.2f ui_ms=%.2f%n",run,files.size(),scan,parse,preview,ms(start));
      for(v f:fonts)f.f();
    }System.exit(0);
  }
}
