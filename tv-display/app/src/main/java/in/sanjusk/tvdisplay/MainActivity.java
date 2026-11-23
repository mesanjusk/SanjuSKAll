package in.sanjusk.tvdisplay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class MainActivity extends Activity {
    private static final int PICK_FOLDER=41;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final SecureRandom random=new SecureRandom();
    private final Runnable imageAdvance=()->{if(MainActivity.this.playing)step(1);};
    private final List<Integer> history=new ArrayList<>();
    private int historyCursor=-1;
    private long imageDeadline=0L;
    private long imageRemainingMs=0L;
    private final Runnable autoHide=()->{if(MainActivity.this.panel!=null) MainActivity.this.panel.setVisibility(View.GONE);};
    private SharedPreferences prefs;
    private MediaLibrary library;
    private RemoteServer server;
    private List<MediaLibrary.Item> playlist=new ArrayList<>();
    private FrameLayout root,canvas;
    private LinearLayout panel;
    private ImageView image;
    private TextureView video;
    private MediaPlayer mediaPlayer;
    private Uri currentVideoUri;
    private boolean videoPrepared=false;
    private TextView heading,details;
    private int position=0, rotation=0, seconds=8;
    private boolean playing=true,shuffle=false,cover=false;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(5894|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        prefs=getSharedPreferences("tv_settings",MODE_PRIVATE);
        library=new MediaLibrary(this,prefs);
        rotation=prefs.getInt("rotation",0);
        seconds=prefs.getInt("seconds",8);
        cover=prefs.getBoolean("cover",false);
        shuffle=prefs.getBoolean("shuffle",false);
        String pin=prefs.getString("pin","");
        if(pin.isEmpty()){
            pin=String.format(java.util.Locale.US,"%06d",random.nextInt(1000000));
            prefs.edit().putString("pin",pin).apply();
        }
        String token=prefs.getString("token","");
        if(token.isEmpty()){
            byte[] bytes=new byte[32]; random.nextBytes(bytes);
            token=android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP|android.util.Base64.URL_SAFE|android.util.Base64.NO_PADDING);
            prefs.edit().putString("token",token).apply();
        }
        buildScreen();
        refresh(false);
        root.post(this::applyRotation);
        server=new RemoteServer(this,pin,token,library);
        server.start();
        updateLabels();
        showPanel();
    }
    private TextView text(String s,int size,boolean bold){
        TextView v=new TextView(this);
        v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(size);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }
    private Button button(String label,Runnable click){
        Button b=new Button(this);b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff4246a1));
        b.setOnClickListener(v->{click.run();showPanel();});
        b.setFocusable(true);
        return b;
    }
    private void addRow(LinearLayout parent,Button... buttons){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        for(Button b:buttons)row.addView(b,new LinearLayout.LayoutParams(0,65,1f));
        parent.addView(row);
    }
    private void buildScreen(){
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        canvas=new FrameLayout(this);canvas.setBackgroundColor(Color.BLACK);
        root.addView(canvas,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        image=new ImageView(this);image.setBackgroundColor(Color.BLACK);
        image.setScaleType(cover?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.FIT_CENTER);
        video=new TextureView(this);video.setVisibility(View.GONE);
        video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
            @Override public void onSurfaceTextureAvailable(SurfaceTexture texture,int width,int height){
                if(video.getVisibility()==View.VISIBLE && currentVideoUri!=null) startVideo();
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int width,int height){
                updateVideoScaling();
            }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){
                releaseVideo();return true;
            }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture texture){}
        });
        canvas.addView(image,new FrameLayout.LayoutParams(-1,-1));
        canvas.addView(video,new FrameLayout.LayoutParams(-1,-1));
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(20,15,20,16);panel.setBackgroundColor(0xdd101526);
        heading=text("SK DIGITAL • TV DISPLAY",28,true);
        details=text("",19,false);details.setPadding(0,8,0,14);
        panel.addView(heading);panel.addView(details);
        addRow(panel,button("▶ Play / Pause",()->{if(playing)pause();else resume();}),
            button("◀ Previous",()->step(-1)),button("Next ▶",()->step(1)),
            button("⟳ Rotation",()->setRotation(rotation==0?90:rotation==90?180:rotation==180?270:0)));
        addRow(panel,button("USB Folder",this::pickFolder),
            button("Fit / Fill",()->setFit(!cover)),
            button("Refresh Files",()->refresh(true)),
            button("Hide Menu",()->panel.setVisibility(View.GONE)));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        // Keep controls horizontal while the media rotates in portrait mode.
        root.addView(panel,lp);
        setContentView(root);
        panel.getChildAt(2).requestFocus();
    }
    private void pickFolder(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(i,PICK_FOLDER); }
        catch(Exception e){ android.widget.Toast.makeText(this,"TV has no folder picker. Upload from your phone instead.",android.widget.Toast.LENGTH_LONG).show(); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==PICK_FOLDER && result==RESULT_OK && data!=null && data.getData()!=null){
            Uri uri=data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                prefs.edit().putString("folderUri",uri.toString()).apply();
                refresh(true);
            } catch(Exception e){
                android.widget.Toast.makeText(this,"Cannot read this USB folder.",android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }
    void refresh(boolean start){
        boolean wasEmpty=playlist.isEmpty();
        String current=wasEmpty?"":playlist.get(Math.min(position,playlist.size()-1)).key();
        playlist=library.list();
        history.clear();
        historyCursor=-1;
        position=0;
        for(int i=0;i<playlist.size();i++) if(playlist.get(i).key().equals(current)){position=i;break;}
        if(wasEmpty && !playlist.isEmpty())playing=true;
        if(start || wasEmpty || playlist.isEmpty())playCurrent();
        updateLabels();
    }
    void mediaChanged(){runOnUiThread(()->refresh(true));}
    private void applyRotation(){
        if(root==null)return;
        int w=root.getWidth(),h=root.getHeight();
        if(w==0||h==0)return;
        boolean turn=rotation==90||rotation==270;
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(turn?h:w,turn?w:h,Gravity.CENTER);
        canvas.setLayoutParams(p);
        canvas.setPivotX((turn?h:w)/2f);canvas.setPivotY((turn?w:h)/2f);
        canvas.setRotation(rotation);
        video.post(this::updateVideoScaling);
        updateLabels();
    }
    private void setRotation(int degrees){
        if(degrees!=0&&degrees!=90&&degrees!=180&&degrees!=270)return;
        rotation=degrees;prefs.edit().putInt("rotation",degrees).apply();
        applyRotation();
    }
    private void setFit(boolean fill){
        cover=fill;prefs.edit().putBoolean("cover",fill).apply();
        image.setScaleType(fill?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.FIT_CENTER);
        updateVideoScaling();
        updateLabels();
    }
    private void loadImage(Uri uri)throws Exception{
        BitmapFactory.Options probe=new BitmapFactory.Options();probe.inJustDecodeBounds=true;
        try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,probe);}
        int sample=1;
        while(probe.outWidth/sample>2160||probe.outHeight/sample>2160)sample*=2;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample;
        try(InputStream in=getContentResolver().openInputStream(uri)){
            Bitmap bitmap=BitmapFactory.decodeStream(in,null,options);
            if(bitmap==null)throw new IllegalArgumentException("Unsupported image");
            image.setImageBitmap(bitmap);
        }
    }
    private void releaseVideo(){
        videoPrepared=false;
        if(mediaPlayer!=null){
            try{mediaPlayer.reset();}catch(Exception ignored){}
            try{mediaPlayer.release();}catch(Exception ignored){}
            mediaPlayer=null;
        }
    }
    private void startVideo(){
        if(currentVideoUri==null || video==null || !video.isAvailable()) return;
        releaseVideo();
        Uri uri=currentVideoUri;
        try{
            MediaPlayer mp=new MediaPlayer();
            mediaPlayer=mp;
            mp.setDataSource(this,uri);
            Surface surface=new Surface(video.getSurfaceTexture());
            try{mp.setSurface(surface);}finally{surface.release();}
            mp.setOnPreparedListener(ready->{
                if(mediaPlayer!=ready)return;
                videoPrepared=true;
                updateVideoScaling();
                if(playing)ready.start();
            });
            mp.setOnVideoSizeChangedListener((player,w,h)->updateVideoScaling());
            mp.setOnCompletionListener(done->{if(mediaPlayer==done)step(1);});
            mp.setOnErrorListener((bad,what,extra)->{
                if(mediaPlayer==bad){
                    handler.postDelayed(()->{
                        if(playing && currentVideoUri!=null && currentVideoUri.equals(uri))step(1);
                    },500);
                }
                return true;
            });
            mp.prepareAsync();
        }catch(Exception e){
            handler.postDelayed(()->{
                if(playing && currentVideoUri!=null && currentVideoUri.equals(uri))step(1);
            },500);
        }
    }
    private void updateVideoScaling(){
        if(video==null||mediaPlayer==null||!videoPrepared)return;
        int vw=video.getWidth(),vh=video.getHeight();
        int mediaW=mediaPlayer.getVideoWidth(),mediaH=mediaPlayer.getVideoHeight();
        if(vw<=0||vh<=0||mediaW<=0||mediaH<=0)return;
        float scaleX=(float)vw/mediaW,scaleY=(float)vh/mediaH;
        float factor=cover?Math.max(scaleX,scaleY):Math.min(scaleX,scaleY);
        Matrix transform=new Matrix();
        transform.setScale(mediaW*factor/vw,mediaH*factor/vh,vw/2f,vh/2f);
        video.setTransform(transform);
    }
    private void scheduleImageAdvance(long delayMs){
        handler.removeCallbacks(imageAdvance);
        imageRemainingMs=Math.max(100,delayMs);
        imageDeadline=android.os.SystemClock.uptimeMillis()+imageRemainingMs;
        if(playing)handler.postDelayed(imageAdvance,imageRemainingMs);
    }
    private void playCurrent(){
        handler.removeCallbacks(imageAdvance);
        imageRemainingMs=seconds*1000L;
        releaseVideo();
        currentVideoUri=null;
        video.setVisibility(View.GONE);
        if(playlist.isEmpty()){
            image.setImageDrawable(null);
            playing=false;showPanel();updateLabels();return;
        }
        if(position<0||position>=playlist.size())position=0;
        MediaLibrary.Item item=playlist.get(position);
        // Changing slides must NOT cancel a user's pause.
        if(item.video){
            image.setVisibility(View.GONE);video.setVisibility(View.VISIBLE);
            currentVideoUri=item.uri;
            if(video.isAvailable())startVideo();
        }else{
            image.setVisibility(View.VISIBLE);
            try{loadImage(item.uri);}catch(Exception e){image.setImageDrawable(null);}
            scheduleImageAdvance(seconds*1000L);
        }
        updateLabels();
    }
    private void pause(){
        if(!playing)return;
        playing=false;
        if(video.getVisibility()==View.VISIBLE){
            if(mediaPlayer!=null && videoPrepared)try{mediaPlayer.pause();}catch(Exception ignored){}
        }else{
            imageRemainingMs=Math.max(100,imageDeadline-android.os.SystemClock.uptimeMillis());
        }
        handler.removeCallbacks(imageAdvance);
        updateLabels();
    }
    private void resume(){
        if(playlist.isEmpty()){
            playing=true;
            refresh(true);
            return;
        }
        if(playing)return;
        playing=true;
        if(video.getVisibility()==View.VISIBLE){
            if(mediaPlayer!=null && videoPrepared){
                try{mediaPlayer.start();}catch(Exception e){startVideo();}
            }else if(mediaPlayer==null && currentVideoUri!=null && video.isAvailable())startVideo();
        }else{
            scheduleImageAdvance(imageRemainingMs>0?imageRemainingMs:seconds*1000L);
        }
        updateLabels();
    }
    private void step(int delta){
        if(playlist.isEmpty())return;
        if(shuffle && playlist.size()>1){
            if(delta<0 && historyCursor>0){
                position=history.get(--historyCursor);
            }else if(delta>0 && historyCursor<history.size()-1){
                position=history.get(++historyCursor);
            }else if(delta>0){
                if(historyCursor<0){history.add(position);historyCursor=0;}
                int next=position;
                while(next==position)next=random.nextInt(playlist.size());
                position=next;
                history.add(position);
                historyCursor++;
                if(history.size()>1000){history.remove(0);historyCursor--;}
            }else{
                // No earlier shuffled item: preserve current item.
                return;
            }
        }else{
            position=(position+delta+playlist.size())%playlist.size();
        }
        playCurrent();
    }
    private void showPanel(){
        if(panel==null)return;
        panel.setVisibility(View.VISIBLE);
        handler.removeCallbacks(autoHide);
        handler.postDelayed(autoHide,30000);
        updateLabels();
    }
    private void updateLabels(){
        if(details==null)return;
        String name=playlist.isEmpty()?"No media yet — upload photos or select a USB folder":playlist.get(Math.min(position,playlist.size()-1)).name;
        String addr=lanIp();
        details.setText("Phone: http://"+addr+":8765   |   PIN: "+prefs.getString("pin","")
           +"\n"+name+" ("+(playlist.isEmpty()?0:position+1)+"/"+playlist.size()+")  |  "
           +(playing?"Playing":"Paused")+"  |  Rotation: "+rotation+"°  |  Images: "+seconds+" sec"
           +"\nMENU / OK on the TV remote shows controls. Phone and TV must use same Wi-Fi.");
    }
    private static String lanIp(){
        try {
            Enumeration<NetworkInterface> n=NetworkInterface.getNetworkInterfaces();
            while(n.hasMoreElements()){
                NetworkInterface it=n.nextElement();
                if(!it.isUp()||it.isLoopback())continue;
                Enumeration<java.net.InetAddress> ips=it.getInetAddresses();
                while(ips.hasMoreElements()){
                    java.net.InetAddress a=ips.nextElement();
                    if(a instanceof Inet4Address && a.isSiteLocalAddress())return a.getHostAddress();
                }
            }
        }catch(Exception ignored){}
        return "TV-IP";
    }
    JSONObject statusJson(){
        JSONObject obj=new JSONObject();
        try {
            obj.put("device","SK Digital TV Display");
            obj.put("ip",lanIp());obj.put("port",8765);
            obj.put("playing",playing);obj.put("rotation",rotation);
            obj.put("actualRotation",(int)canvas.getRotation());
            obj.put("mediaType",playlist.isEmpty()?"none":playlist.get(Math.min(position,playlist.size()-1)).video?"video":"image");
            obj.put("seconds",seconds);obj.put("cover",cover);obj.put("shuffle",shuffle);
            obj.put("position",position);obj.put("total",playlist.size());
            obj.put("name",playlist.isEmpty()?"":playlist.get(position).name);
            JSONArray arr=new JSONArray();
            for(MediaLibrary.Item item:playlist){
                JSONObject m=new JSONObject();m.put("name",item.name);m.put("video",item.video);m.put("uploaded",item.uploaded);arr.put(m);
            }
            obj.put("playlist",arr);
        }catch(Exception ignored){}
        return obj;
    }
    void command(JSONObject o){
        String action=o.optString("action"), value=o.optString("value");
        switch(action){
            case "play":resume();break;
            case "pause":pause();break;
            case "next":step(1);break;
            case "prev":step(-1);break;
            case "rotate":
                try{setRotation(Integer.parseInt(value));}catch(Exception ignored){}
                break;
            case "duration":
                try{seconds=Math.min(120,Math.max(2,Integer.parseInt(value)));prefs.edit().putInt("seconds",seconds).apply();if(video.getVisibility()!=View.VISIBLE)scheduleImageAdvance(seconds*1000L);}catch(Exception ignored){}
                break;
            case "fit":setFit("cover".equals(value));break;
            case "shuffle":
                shuffle=o.optBoolean("value",false);
                history.clear();historyCursor=-1;
                prefs.edit().putBoolean("shuffle",shuffle).apply();
                break;
            case "move":
                int i=o.optInt("index",-1),j=i+("up".equals(o.optString("direction"))?-1:1);
                if(i>=0&&j>=0&&i<playlist.size()&&j<playlist.size()){
                    MediaLibrary.Item active=playlist.get(position);
                    Collections.swap(playlist,i,j);library.saveOrder(playlist);
                    position=playlist.indexOf(active);history.clear();historyCursor=-1;updateLabels();
                }
                break;
            case "refresh":refresh(true);break;
            case "menu":showPanel();break;
        }
        updateLabels();
    }
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_INFO){
            showPanel();return true;
        }
        if(key==KeyEvent.KEYCODE_BACK){
            if(panel.getVisibility()==View.VISIBLE)panel.setVisibility(View.GONE);
            else showPanel();
            return true;
        }
        if(panel.getVisibility()!=View.VISIBLE){
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){showPanel();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_RIGHT){step(1);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_LEFT){step(-1);return true;}
        }
        return super.onKeyDown(key,event);
    }
    @Override protected void onDestroy(){
        handler.removeCallbacksAndMessages(null);
        if(server!=null)server.stop();
        releaseVideo();
        super.onDestroy();
    }
}
