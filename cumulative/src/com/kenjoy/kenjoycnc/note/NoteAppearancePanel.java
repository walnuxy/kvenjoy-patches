package com.kenjoy.kenjoycnc.note;

import java.awt.*;
import javax.swing.*;
import javax.swing.event.*;

/** A document-owned effect editor; layout and font I/O stay in the host. */
public final class NoteAppearancePanel extends JPanel {
    public interface Host {
        NoteAppearance.Config current(boolean selected);
        boolean busy();
        void apply(NoteAppearance.Config config,boolean selected);
        void rerandomize(boolean selected);
        default void capture(boolean selected) {}
        default void notice(String message) {}
    }
    private final Host host;
    private boolean binding,dirty,shuffleQueued;
    private int currentScope;
    private final JComboBox<String> norm=new JComboBox<String>(new String[]{"关闭","自然","更整齐"});
    private final JComboBox<String> random=new JComboBox<String>(new String[]{"关闭","独立随机","关联随机"});
    private final JComboBox<String> scope=new JComboBox<String>(new String[]{"整篇文章","所选文字"});
    private final JComboBox<String> preset=new JComboBox<String>(new String[]{"轻微","自然","明显","自定义"});
    private final JSlider degree=new JSlider(0,100,100),strength=new JSlider(0,400,200);
    private final JLabel degreeLabel=new JLabel(),strengthLabel=new JLabel();
    private final JSpinner latinSize=number(80,40,120,1),numberSize=number(80,40,120,1);
    private final JSpinner size=number(6,0,20,.5),gap=number(6,0,20,.5),dx=number(2,0,10,.5),dy=number(5,0,15,.5),angle=number(1.2,0,5,.1),correlation=number(75,0,100,5),continuity=number(5,1,12,1),minGap=number(.1,0,5,.05);
    private final javax.swing.Timer timer=new javax.swing.Timer(250,e->flush());
    private final JButton shuffle=new JButton("重新随机误差"),reset=new JButton("重置字形参数");
    private final JPanel details=new JPanel(new GridBagLayout());
    public NoteAppearancePanel(Host host) {
        super(new GridBagLayout());this.host=host;setName("NoteAppearancePanel");timer.setRepeats(false);
        row(this,0,"规整方式",norm);row(this,1,"规整程度",slider(degree,degreeLabel));
        row(this,2,"英文大小 %",latinSize);row(this,3,"数字大小 %",numberSize);
        latinSize.setName("NoteLatinSize");numberSize.setName("NoteNumberSize");latinSize.setToolTipText("相对中文主体大小；启用规整后生效，改变大小时保持对齐");numberSize.setToolTipText("数字相对中文主体大小；启用规整后生效，小数点随数字调整");
        row(this,4,"随机方式",random);row(this,5,"误差强度",slider(strength,strengthLabel));
        row(this,6,"误差预设",preset);row(this,7,"作用范围",scope);
        JToggleButton advanced=new JToggleButton("详细参数",new DisclosureIcon());advanced.setName("NoteAppearanceAdvanced");wide(this,8,advanced);
        row(details,0,"大小 ±%",size);row(details,1,"字距 ±% 字高",gap);row(details,2,"横移 ±% 字高",dx);row(details,3,"上下 ±% 字高",dy);row(details,4,"旋转 ±°",angle);row(details,5,"关联程度 %",correlation);row(details,6,"起伏长度 / 字",continuity);row(details,7,"最小间隙 mm",minGap);details.setVisible(false);wide(this,9,details);
        JPanel buttons=new JPanel(new GridLayout(2,1,0,4));buttons.add(shuffle);buttons.add(reset);wide(this,10,buttons);
        norm.setName("NoteNormalizeMode");random.setName("NoteRandomMode");degree.setName("NoteNormalizeDegree");strength.setName("NoteRandomStrength");scope.setName("NoteAppearanceScope");shuffle.setName("NoteShuffleEffects");reset.setName("NoteResetEffects");
        size.setName("NoteEffectSize");gap.setName("NoteEffectGap");dx.setName("NoteEffectX");dy.setName("NoteEffectY");angle.setName("NoteEffectAngle");
        advanced.addActionListener(e->{details.setVisible(advanced.isSelected());advanced.repaint();revalidate();});
        norm.addActionListener(e->queue());random.addActionListener(e->queue());
        latinSize.addChangeListener(e->queue());numberSize.addChangeListener(e->queue());
        degree.addChangeListener(e->{labels();if(binding)return;captureGesture();if(!degree.getValueIsAdjusting())timer.restart();});
        strength.addChangeListener(e->{labels();if(binding)return;captureGesture();setAmplitude(strength.getValue()/100f);labels();if(!strength.getValueIsAdjusting())timer.restart();});
        preset.addActionListener(e->{if(binding||preset.getSelectedIndex()==3)return;captureGesture();int amount=new int[]{100,200,400}[preset.getSelectedIndex()];binding=true;strength.setValue(amount);binding=false;setAmplitude(amount/100f);labels();timer.restart();});
        scope.addActionListener(e->{if(binding)return;if(hasPending()){binding=true;scope.setSelectedIndex(currentScope);binding=false;host.notice("当前参数正在应用，完成后再切换作用范围。");return;}currentScope=scope.getSelectedIndex();load(host.current(selected()));});
        for(JSpinner s:new JSpinner[]{size,gap,dx,dy,angle,correlation,continuity,minGap})s.addChangeListener(e->{if(binding)return;binding=true;preset.setSelectedIndex(3);binding=false;labels();queue();});
        shuffle.addActionListener(e->{if(!dirty&&!shuffleQueued)host.capture(selected());shuffleQueued=true;timer.restart();});
        reset.addActionListener(e->{timer.stop();host.capture(selected());load(new NoteAppearance.Config());dirty=true;flush();});
        setToolTipText("参数为最大误差；接近页面或行边界时自动减弱。保留已选字体，设置随文章保存。");
        load(new NoteAppearance.Config());
    }
    private static JSpinner number(double value,double min,double max,double step){JSpinner s=new JSpinner(new SpinnerNumberModel(value,min,max,step));((JSpinner.NumberEditor)s.getEditor()).getTextField().setColumns(4);return s;}
    private static JPanel slider(JSlider s,JLabel label){JPanel p=new JPanel(new BorderLayout(3,0));s.setPreferredSize(new Dimension(90,24));p.add(s,BorderLayout.CENTER);p.add(label,BorderLayout.EAST);return p;}
    private static void row(JPanel p,int y,String label,Component value){GridBagConstraints g=new GridBagConstraints();g.gridx=0;g.gridy=y;g.anchor=GridBagConstraints.WEST;g.insets=new Insets(2,3,2,3);p.add(new JLabel(label),g);g.gridx=1;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL;p.add(value,g);}
    private static void wide(JPanel p,int y,Component value){GridBagConstraints g=new GridBagConstraints();g.gridx=0;g.gridy=y;g.gridwidth=2;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL;g.insets=new Insets(2,3,2,3);p.add(value,g);}
    private boolean selected(){return scope.getSelectedIndex()==1;}
    private static float value(JSpinner s){try{s.commitEdit();}catch(java.text.ParseException e){throw new IllegalArgumentException("请输入有效的误差数值");}return ((Number)s.getValue()).floatValue();}
    private void labels(){
        degreeLabel.setText(degree.getValue()+"%");float k=strength.getValue()/100f;
        boolean linked=Math.abs(((Number)size.getValue()).doubleValue()-3*k)<.0001&&Math.abs(((Number)gap.getValue()).doubleValue()-3*k)<.0001&&Math.abs(((Number)dx.getValue()).doubleValue()-k)<.0001&&Math.abs(((Number)dy.getValue()).doubleValue()-2.5*k)<.0001&&Math.abs(((Number)angle.getValue()).doubleValue()-.6*k)<.0001;
        strengthLabel.setText(linked?strength.getValue()+"%":"自定义");
    }
    private void setAmplitude(float k){boolean old=binding;binding=true;size.setValue((double)(3*k));gap.setValue((double)(3*k));dx.setValue((double)k);dy.setValue((double)(2.5*k));angle.setValue((double)(.6*k));preset.setSelectedIndex(k==1?0:k==2?1:k==4?2:3);binding=old;}
    private void captureGesture(){boolean first=!dirty&&!shuffleQueued;dirty=true;if(first)host.capture(selected());}
    private void queue(){if(binding)return;captureGesture();timer.restart();}
    private void flush(){
        if(!dirty&&!shuffleQueued)return;if(host.busy()||degree.getValueIsAdjusting()||strength.getValueIsAdjusting()){timer.restart();return;}
        if(!dirty){shuffleQueued=false;try{host.rerandomize(selected());}catch(RuntimeException error){JOptionPane.showMessageDialog(this,error.getMessage(),"字形与随机",JOptionPane.ERROR_MESSAGE);}return;}
        try{NoteAppearance.Config c=NoteAppearance.copy(host.current(selected()));c.normalizeMode=norm.getSelectedIndex();c.normalization=degree.getValue()/100f;c.latinSize=value(latinSize)/100;c.numberSize=value(numberSize)/100;c.randomMode=random.getSelectedIndex();c.size=value(size)/100;c.gap=value(gap)/100;c.dx=value(dx)/100;c.dy=value(dy)/100;c.rotation=value(angle);c.correlation=value(correlation)/100;c.continuity=Math.round(value(continuity));c.minGapMm=value(minGap);dirty=false;host.apply(c,selected());}
        catch(RuntimeException error){dirty=false;shuffleQueued=false;JOptionPane.showMessageDialog(this,error.getMessage(),"字形与随机",JOptionPane.ERROR_MESSAGE);}
        if(shuffleQueued)timer.restart();
    }
    public void refresh(){
        // Pending/dragged controls belong to the user's next transaction.
        if(dirty||degree.getValueIsAdjusting()||strength.getValueIsAdjusting())return;
        load(host.current(selected()));
    }
    private static final class DisclosureIcon implements Icon {
        public int getIconWidth(){return 12;}
        public int getIconHeight(){return 12;}
        public void paintIcon(Component c,Graphics raw,int x,int y){
            Graphics2D g=(Graphics2D)raw.create();try{
                g.translate(x,y);g.setColor(c.getForeground());g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                boolean open=c instanceof AbstractButton&&((AbstractButton)c).isSelected();
                g.fillPolygon(open?new int[]{2,10,6}:new int[]{4,9,4},open?new int[]{4,4,9}:new int[]{2,6,10},3);
            }finally{g.dispose();}
        }
    }
    private void load(NoteAppearance.Config c){
        if(c==null)c=new NoteAppearance.Config();binding=true;
        norm.setSelectedIndex(c.normalizeMode);random.setSelectedIndex(c.randomMode);degree.setValue(Math.round(c.normalization*100));
        latinSize.setValue((double)c.latinSize*100);numberSize.setValue((double)c.numberSize*100);
        size.setValue((double)c.size*100);gap.setValue((double)c.gap*100);dx.setValue((double)c.dx*100);dy.setValue((double)c.dy*100);angle.setValue((double)c.rotation);correlation.setValue((double)c.correlation*100);continuity.setValue((double)c.continuity);minGap.setValue((double)c.minGapMm);
        float k=c.size/.03f;boolean match=Math.abs(c.gap-.03*k)<1e-6&&Math.abs(c.dx-.01*k)<1e-6&&Math.abs(c.dy-.025*k)<1e-6&&Math.abs(c.rotation-.6*k)<1e-5;
        preset.setSelectedIndex(match&&Math.abs(k-1)<1e-5?0:match&&Math.abs(k-2)<1e-5?1:match&&Math.abs(k-4)<1e-5?2:3);
        strength.setValue(Math.max(0,Math.min(400,Math.round(k*100))));labels();binding=false;
    }
    public boolean hasPending(){return dirty||shuffleQueued||degree.getValueIsAdjusting()||strength.getValueIsAdjusting();}
}
