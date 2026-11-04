package br.com.dehalbuq.chanuodiag;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.opengl.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
  private String report = "Coleta ainda nao concluida";
  private TextView status;
  private static final int SAVE=100;
  private static final int[][] CHECK={{1280,720,30},{1920,1080,30},{1920,1080,60},{2560,1440,30},{3840,2160,30},{3840,2160,60}};
  private void add(StringBuilder b,String key,Object value){b.append(key).append(": ").append(value).append("\n");}
  private Button button(String name, LinearLayout layout, View.OnClickListener click){
    Button b=new Button(this); b.setText(name); b.setAllCaps(false);
    b.setOnClickListener(click); b.setFocusable(true);layout.addView(b);return b;
  }
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    ScrollView scroll=new ScrollView(this);
    LinearLayout layout=new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(22,16,22,28);
    layout.setBackgroundColor(Color.rgb(24,27,35));scroll.addView(layout);
    TextView title=new TextView(this);title.setText("CHAnUO A1 • Diagnóstico");title.setTextColor(Color.WHITE);title.setTextSize(23);
    title.setPadding(0,12,0,12);layout.addView(title);
    TextView help=new TextView(this);
    help.setText("Relatório local, sem internet e sem permissões. Detecta codecs anunciados pelo Android, resolução do framebuffer, modos da tela e OpenGL ES. Não mede o painel óptico.");
    help.setTextColor(0xffdddddd);help.setTextSize(15);layout.addView(help);
    button("Coletar novamente",layout,v->collect());
    button("Salvar relatório TXT",layout,v->save());
    button("Compartilhar relatório",layout,v->share());
    status=new TextView(this);status.setTextColor(0xffffffff);status.setTextSize(15);
    status.setPadding(0,15,0,15);layout.addView(status);
    TextView pattern=new TextView(this);
    pattern.setText("Escala visual: Branco   Cinza-claro   Cinza-médio   Preto");
    pattern.setTextColor(0xffeeeeee);layout.addView(pattern);
    LinearLayout samples=new LinearLayout(this);layout.addView(samples);
    int[] shades={0xffffffff,0xffc0c0c0,0xff808080,0xff404040,0xff202020,0xff000000};
    for(int shade:shades){View v=new View(this);v.setBackgroundColor(shade);samples.addView(v,new LinearLayout.LayoutParams(0,64,1));}
    TextView note=new TextView(this);note.setText("O aplicativo não altera o brilho de outros apps. O relatório mostra capacidades declaradas, não testes reais de desempenho.");
    note.setTextColor(0xffbbbbbb);note.setTextSize(13);note.setPadding(0,14,0,8);layout.addView(note);
    setContentView(scroll);collect();
  }
  private void collect(){
    status.setText("Consultando hardware e codecs…");
    new Thread(()->{
      String r;
      try{r=makeReport();}catch(Throwable t){r="Erro de coleta: "+t;}
      final String out=r;
      runOnUiThread(()->{report=out;status.setText("Relatório pronto ("+out.length()+" caracteres). Use Salvar TXT ou Compartilhar.");});
    }).start();
  }
  private String makeReport(){
    StringBuilder s=new StringBuilder(48000);
    s.append("CHANUO A1 - DIAGNOSTICO v1\nCAPACIDADES DECLARADAS PELO ANDROID, NAO BENCHMARK\n\n");
    add(s,"Data UTC",new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US){{setTimeZone(TimeZone.getTimeZone("UTC"));}}.format(new Date()));
    add(s,"Marca",Build.BRAND);add(s,"Fabricante",Build.MANUFACTURER);add(s,"Modelo",Build.MODEL);
    add(s,"Produto",Build.PRODUCT);add(s,"Device",Build.DEVICE);add(s,"Hardware",Build.HARDWARE);
    add(s,"Placa",Build.BOARD);add(s,"SoC/bootloader",Build.BOOTLOADER);
    add(s,"Android",Build.VERSION.RELEASE);add(s,"API SDK",Build.VERSION.SDK_INT);add(s,"Build ID",Build.ID);
    add(s,"ABI",Build.VERSION.SDK_INT>=21?Arrays.toString(Build.SUPPORTED_ABIS):Build.CPU_ABI);
    add(s,"CPU cores anunciados",Runtime.getRuntime().availableProcessors());
    try{
      ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
      ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();am.getMemoryInfo(mi);
      add(s,"RAM total bytes",mi.totalMem);add(s,"RAM disponivel bytes",mi.availMem);
      add(s,"Low RAM device",am.isLowRamDevice());add(s,"GLES configuracao",am.getDeviceConfigurationInfo().getGlEsVersion());
    }catch(Throwable t){add(s,"RAM",t);}
    s.append("\n== DISPLAY ANDROID ==\n");
    try{
      Display display=getWindowManager().getDefaultDisplay();
      android.graphics.Point real=new android.graphics.Point();display.getRealSize(real);
      add(s,"Framebuffer real",real.x+"x"+real.y);
      android.graphics.Point logical=new android.graphics.Point();display.getSize(logical);
      add(s,"Area da app",logical.x+"x"+logical.y);
      add(s,"Refresh atual",display.getRefreshRate());
      DisplayMetrics dm=new DisplayMetrics();display.getMetrics(dm);
      add(s,"Densidade",dm.densityDpi+"dpi");
      if(Build.VERSION.SDK_INT>=23){
        Display.Mode mode=display.getMode();
        add(s,"Modo ativo",mode.getPhysicalWidth()+"x"+mode.getPhysicalHeight()+"@"+mode.getRefreshRate());
        for(Display.Mode m:display.getSupportedModes())add(s,"Modo suportado",m.getPhysicalWidth()+"x"+m.getPhysicalHeight()+"@"+m.getRefreshRate());
      }
      if(Build.VERSION.SDK_INT>=24){
        Display.HdrCapabilities hdr=display.getHdrCapabilities();
        if(hdr!=null)add(s,"HDR anunciado",Arrays.toString(hdr.getSupportedHdrTypes()));
      }
      if(Build.VERSION.SDK_INT>=26)add(s,"Gamut amplo",display.isWideColorGamut());
    }catch(Throwable t){add(s,"Falha display",t);}
    s.append("\n== GPU / OPENGL ES ==\n");
    try{gpu(s);}catch(Throwable t){add(s,"Falha GPU",t);}
    s.append("\n== CODECS MEDIA CODEC (DECODERS + ENCODERS) ==\n");
    try{
      MediaCodecInfo[] codecs;
      if(Build.VERSION.SDK_INT>=21)codecs=new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
      else{int n=MediaCodecList.getCodecCount();codecs=new MediaCodecInfo[n];for(int i=0;i<n;i++)codecs[i]=MediaCodecList.getCodecInfoAt(i);}
      add(s,"Numero de codecs",codecs.length);
      for(MediaCodecInfo codec:codecs){
        s.append("\n### ").append(codec.getName()).append(codec.isEncoder()?" ENCODER\n":" DECODER\n");
        if(Build.VERSION.SDK_INT>=29){
          add(s,"HW accel flag",codec.isHardwareAccelerated());
          add(s,"Software only flag",codec.isSoftwareOnly());
          add(s,"Vendor flag",codec.isVendor());
        }else add(s,"Aceleracao","flag publica indisponivel neste Android; nome nao comprova HW");
        for(String type:codec.getSupportedTypes()){
          add(s," MIME",type);
          try{
            MediaCodecInfo.CodecCapabilities c=codec.getCapabilitiesForType(type);
            add(s," Color formats",Arrays.toString(c.colorFormats));
            for(MediaCodecInfo.CodecProfileLevel p:c.profileLevels)add(s," Profile/level",Integer.toHexString(p.profile)+"/"+Integer.toHexString(p.level));
            add(s," Adaptive",c.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback));
            add(s," Secure",c.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback));
            add(s," Tunneled",c.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback));
            if(Build.VERSION.SDK_INT>=23)add(s," Max instancias",c.getMaxSupportedInstances());
            if(Build.VERSION.SDK_INT>=21&&type.startsWith("video/")){
              MediaCodecInfo.VideoCapabilities vc=c.getVideoCapabilities();
              if(vc!=null){
                add(s," Width range",vc.getSupportedWidths());add(s," Height range",vc.getSupportedHeights());
                add(s," FPS range",vc.getSupportedFrameRates());add(s," Alignment",vc.getWidthAlignment()+"x"+vc.getHeightAlignment());
                for(int[] q:CHECK)try{add(s," "+q[0]+"x"+q[1]+"@"+q[2],vc.areSizeAndRateSupported(q[0],q[1],q[2]));}catch(Throwable x){add(s," Size check",x);}
                if(Build.VERSION.SDK_INT>=23)for(int[] q:new int[][]{{1280,720},{1920,1080},{3840,2160}})
                  try{add(s," Achievable FPS "+q[0]+"x"+q[1],vc.getAchievableFrameRatesFor(q[0],q[1]));}catch(Throwable ignore){}
              }
            }
            if(Build.VERSION.SDK_INT>=21&&type.startsWith("audio/")){
              MediaCodecInfo.AudioCapabilities ac=c.getAudioCapabilities();
              if(ac!=null){add(s," Audio channels max",ac.getMaxInputChannelCount());add(s," Audio sample rates",Arrays.toString(ac.getSupportedSampleRates()));}
            }
          }catch(Throwable t){add(s," Erro capabilities",t);}
        }
      }
    }catch(Throwable t){add(s,"Erro MediaCodecList",t);}
    s.append("\nFIM. Dados locais; nenhuma informacao foi enviada automaticamente.\n");
    return s.toString();
  }
  private void gpu(StringBuilder s){
    EGLDisplay d=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    if(d==EGL14.EGL_NO_DISPLAY){s.append("EGL_NO_DISPLAY\n");return;}
    EGLContext c=EGL14.EGL_NO_CONTEXT;EGLSurface p=EGL14.EGL_NO_SURFACE;
    int[] vers=new int[2];boolean init=false;
    try{
      if(!EGL14.eglInitialize(d,vers,0,vers,1)){s.append("EGL init falhou\n");return;}init=true;
      add(s,"EGL",vers[0]+"."+vers[1]);
      int[] a={EGL14.EGL_RENDERABLE_TYPE,4,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_NONE};
      EGLConfig[] conf=new EGLConfig[1];int[] num=new int[1];
      if(!EGL14.eglChooseConfig(d,a,0,conf,0,1,num,0)||num[0]==0){s.append("Sem config GLES2\n");return;}
      c=EGL14.eglCreateContext(d,conf[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
      if(c==EGL14.EGL_NO_CONTEXT)return;
      p=EGL14.eglCreatePbufferSurface(d,conf[0],new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
      if(p==EGL14.EGL_NO_SURFACE||!EGL14.eglMakeCurrent(d,p,p,c))return;
      add(s,"GL_VENDOR",GLES20.glGetString(GLES20.GL_VENDOR));
      add(s,"GL_RENDERER",GLES20.glGetString(GLES20.GL_RENDERER));
      add(s,"GL_VERSION",GLES20.glGetString(GLES20.GL_VERSION));
      add(s,"GLSL",GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION));
      int[] max=new int[1];GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE,max,0);
      add(s,"GL_MAX_TEXTURE_SIZE",max[0]);add(s,"GL_EXTENSIONS",GLES20.glGetString(GLES20.GL_EXTENSIONS));
    }finally{
      if(c!=EGL14.EGL_NO_CONTEXT)EGL14.eglMakeCurrent(d,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
      if(p!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(d,p);
      if(c!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(d,c);
      if(init)EGL14.eglTerminate(d);
    }
  }
  private void save(){
    try{
      Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
      i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/plain");
      i.putExtra(Intent.EXTRA_TITLE,"chanuo-a1-diagnostico.txt");startActivityForResult(i,SAVE);
    }catch(Throwable t){Toast.makeText(this,"Seletor de arquivos indisponivel: use Compartilhar",Toast.LENGTH_LONG).show();}
  }
  private void share(){
    Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");
    i.putExtra(Intent.EXTRA_TEXT,report);i.putExtra(Intent.EXTRA_SUBJECT,"Chanuo A1 - diagnostico");
    try{startActivity(Intent.createChooser(i,"Compartilhar diagnostico"));}catch(Throwable t){Toast.makeText(this,"Compartilhamento indisponivel",Toast.LENGTH_LONG).show();}
  }
  @Override protected void onActivityResult(int req,int result,Intent data){
    super.onActivityResult(req,result,data);
    if(req==SAVE&&result==RESULT_OK&&data!=null&&data.getData()!=null){
      try(OutputStream o=getContentResolver().openOutputStream(data.getData())){
        if(o==null)throw new IOException("Sem OutputStream");
        o.write(report.getBytes(StandardCharsets.UTF_8));
        Toast.makeText(this,"TXT salvo",Toast.LENGTH_LONG).show();
      }catch(Exception e){Toast.makeText(this,"Erro salvando: "+e,Toast.LENGTH_LONG).show();}
    }
  }
}
