package in.sanjusk.tvdisplay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.KeyEvent;
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
    private final Runnable imageAdvance=()->step(1);
    private final Runnable autoHide=()->{if(MainActivity.this.panel!=null) MainActivity.this.panel.setVisibility(View.GONE);};
    private SharedPreferences prefs;
    private MediaLibrary library;
    private RemoteServer server;
    private List<MediaLibrary.Item> playlist=new ArrayList<>();
    private FrameLayout root,canvas;
    private LinearLayout panel;
    private ImageView image;
    private VideoView video;
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
        video=new VideoView(this);video.setVisibility(View.GONE);
        canvas.addView(image,new FrameLayout.LayoutParams(-1,-1));
        canvas.addView(video,new FrameLayout.LayoutParams(-1,-1));
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(20,15,20,16);panel.setBackgroundColor(0xdd101526);
        heading=text("SK DIGITAL • TV DISPLAY",28,true);
        details=text("",19,false);details.setPadding(0,8,0,14);
        panel.addView(heading);panel.addView(details);
        addRow(panel,button("▶ Play / Pause",()->{if(playing)pause();else resume();}),
            button("◀ Previous",()->step(-1)),button("Next ▶",()->step(1)),
            button("⟳ Rotation",()->setRotation(rotation==0?90:rotation==90?270:0)));
        addRow(panel,button("USB Folder",this::pickFolder),
            button("Fit / Fill",()->setFit(!cover)),
            button("Refresh Files",()->refresh(true)),
            button("Hide Menu",()->panel.setVisibility(View.GONE)));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        canvas.addView(panel,lp);
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
        String current=playlist.isEmpty()?"":playlist.get(Math.min(position,playlist.size()-1)).key();
        playlist=library.list();
        position=0;
        for(int i=0;i<playlist.size();i++) if(playlist.get(i).key().equals(current)){position=i;break;}
        if(start || playlist.isEmpty()) playCurrent();
        else if(playing && !video.isPlaying()) playCurrent();
        updateLabels();
    }
    void mediaChanged(){runOnUiThread(()->refresh(true));}
    private void applyRotation(){
        if(root==null)return;
        int w=root.getWidth(),h=root.getHeight();
        if(w==0||h==0)return;
        boolean turn=rotation!=0;
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(turn?h:w,turn?w:h,Gravity.CENTER);
        canvas.setLayoutParams(p);
        canvas.setPivotX((turn?h:w)/2f);canvas.setPivotY((turn?w:h)/2f);
        canvas.setRotation(rotation);
        updateLabels();
    }
    private void setRotation(int degrees){
        if(degrees!=0&&degrees!=90&&degrees!=270)return;
        rotation=degrees;prefs.edit().putInt("rotation",degrees).apply();
        applyRotation();
    }
    private void setFit(boolean fill){
        cover=fill;prefs.edit().putBoolean("cover",fill).apply();
        image.setScaleType(fill?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.FIT_CENTER);
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
    private void playCurrent(){
        handler.removeCallbacks(imageAdvance);
        try{video.stopPlayback();}catch(Exception ignored){}
        video.setVisibility(View.GONE);
        if(playlist.isEmpty()){
            image.setImageDrawable(null);
            playing=false;showPanel();updateLabels();return;
        }
        if(position<0||position>=playlist.size())position=0;
        MediaLibrary.Item item=playlist.get(position);
        playing=true;
        if(item.video){
            image.setVisibility(View.GONE);video.setVisibility(View.VISIBLE);
            video.setOnCompletionListener(mp->step(1));
            video.setOnErrorListener((mp,what,extra)->{handler.postDelayed(()->step(1),500);return true;});
            try {video.setVideoURI(item.uri);video.start();}
            catch(Exception e){handler.postDelayed(()->step(1),500);}
        }else{
            image.setVisibility(View.VISIBLE);
            try{loadImage(item.uri);}catch(Exception e){image.setImageDrawable(null);}
            handler.postDelayed(imageAdvance,seconds*1000L);
        }
        updateLabels();
    }
    private void pause(){
        playing=false;handler.removeCallbacks(imageAdvance);
        if(video.getVisibility()==View.VISIBLE)video.pause();
        updateLabels();
    }
    private void resume(){
        if(playlist.isEmpty()){refresh(true);return;}
        playing=true;
        if(video.getVisibility()==View.VISIBLE)video.start();
        else handler.postDelayed(imageAdvance,seconds*1000L);
        updateLabels();
    }
    private void step(int delta){
        if(playlist.isEmpty()){refresh(true);return;}
        if(shuffle && playlist.size()>1){
            int next=position;
            while(next==position)next=random.nextInt(playlist.size());
            position=next;
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
                try{seconds=Math.min(120,Math.max(2,Integer.parseInt(value)));prefs.edit().putInt("seconds",seconds).apply();if(playing&&video.getVisibility()!=View.VISIBLE){handler.removeCallbacks(imageAdvance);handler.postDelayed(imageAdvance,seconds*1000L);}}catch(Exception ignored){}
                break;
            case "fit":setFit("cover".equals(value));break;
            case "shuffle":shuffle=o.optBoolean("value",false);prefs.edit().putBoolean("shuffle",shuffle).apply();break;
            case "move":
                int i=o.optInt("index",-1),j=i+("up".equals(o.optString("direction"))?-1:1);
                if(i>=0&&j>=0&&i<playlist.size()&&j<playlist.size()){
                    MediaLibrary.Item active=playlist.get(position);
                    Collections.swap(playlist,i,j);library.saveOrder(playlist);
                    position=playlist.indexOf(active);updateLabels();
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
        try{video.stopPlayback();}catch(Exception ignored){}
        super.onDestroy();
    }
}
