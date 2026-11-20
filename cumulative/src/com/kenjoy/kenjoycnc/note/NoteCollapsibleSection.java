package com.kenjoy.kenjoycnc.note;

import java.awt.*;
import javax.swing.*;

/** A sidebar section keeps its editor components alive while its body is hidden. */
public final class NoteCollapsibleSection extends JPanel {
    private final JPanel body;
    private final JToggleButton toggle;
    public NoteCollapsibleSection(String title,String name,JPanel content){
        super(new BorderLayout(0,2));body=content;setName(name);setAlignmentX(LEFT_ALIGNMENT);
        body.setName(name+"Body");body.setBorder(BorderFactory.createEmptyBorder(0,3,4,3));
        toggle=new JToggleButton(title,new DisclosureIcon(),true);toggle.setName(name+"Toggle");toggle.setHorizontalAlignment(SwingConstants.LEFT);
        toggle.setMargin(new Insets(4,6,4,6));toggle.setIconTextGap(6);
        add(toggle,BorderLayout.NORTH);add(body,BorderLayout.CENTER);
        toggle.addItemListener(event->{body.setVisible(toggle.isSelected());toggle.setToolTipText((toggle.isSelected()?"收起":"展开")+title);toggle.repaint();revalidate();repaint();});
        toggle.setToolTipText("收起"+title);
    }
    public boolean isExpanded(){return toggle.isSelected();}
    public void setExpanded(boolean expanded){toggle.setSelected(expanded);}
    public Dimension getMinimumSize(){Dimension size=getPreferredSize();size.width=0;return size;}
    public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
    private static final class DisclosureIcon implements Icon {
        public int getIconWidth(){return 12;}public int getIconHeight(){return 12;}
        public void paintIcon(Component component,Graphics graphics,int x,int y){Graphics2D g=(Graphics2D)graphics.create();try{
            g.translate(x,y);g.setColor(component.getForeground());g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            boolean expanded=component instanceof AbstractButton&&((AbstractButton)component).isSelected();
            g.fillPolygon(expanded?new int[]{2,10,6}:new int[]{4,9,4},expanded?new int[]{4,4,9}:new int[]{2,6,10},3);
        }finally{g.dispose();}}
    }
}
