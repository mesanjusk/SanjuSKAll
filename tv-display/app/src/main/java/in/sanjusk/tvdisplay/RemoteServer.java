package in.sanjusk.tvdisplay;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class RemoteServer {
    private static final int PORT=8765;
    private static final long MAX_UPLOAD=500L*1024*1024;
    private final MainActivity activity;
    private final MediaLibrary library;
    private final String pin, token;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService workers=Executors.newFixedThreadPool(4);
    private volatile ServerSocket server;
    private volatile boolean running=false;
    private int attempts=0;
    private long lockedUntil=0;

    RemoteServer(MainActivity activity,String pin,String token,MediaLibrary library){
        this.activity=activity;this.pin=pin;this.token=token;this.library=library;
    }
    void start(){
        if(running)return;running=true;
        Thread thread=new Thread(()->{
            try(ServerSocket s=new ServerSocket(PORT)){
                server=s;
                while(running){
                    Socket incoming=s.accept();
                    workers.submit(()->handle(incoming));
                }
            }catch(Exception ignored) {}
            finally{running=false;}
        },"sk-tv-remote");
        thread.setDaemon(true);thread.start();
    }
    void stop(){
        running=false;
        try{if(server!=null)server.close();}catch(IOException ignored){}
        workers.shutdownNow();
    }
    private static final class Request {
        String method,target,path,rawQuery;
        Map<String,String> headers=new HashMap<>();
        InputStream body;
        long length;
    }
    private Request readRequest(InputStream in)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        int state=0,next;
        while((next=in.read())!=-1){
            bytes.write(next);
            if(bytes.size()>16384)throw new IOException("Headers too large");
            if((state==0||state==2)&&next=='\r')state++;
            else if((state==1||state==3)&&next=='\n')state++;
            else state=next=='\r'?1:0;
            if(state==4)break;
        }
        if(bytes.size()==0)throw new IOException("Empty request");
        String header=new String(bytes.toByteArray(),StandardCharsets.ISO_8859_1);
        String[] lines=header.split("\r\n");
        String[] parts=lines[0].split(" ");
        if(parts.length<2)throw new IOException("Invalid request");
        Request r=new Request();r.method=parts[0];r.target=parts[1];
        URI uri=URI.create(r.target);r.path=uri.getPath();r.rawQuery=uri.getRawQuery();
        for(int i=1;i<lines.length;i++){
            int colon=lines[i].indexOf(':');
            if(colon>0)r.headers.put(lines[i].substring(0,colon).trim().toLowerCase(Locale.US),lines[i].substring(colon+1).trim());
        }
        String n=r.headers.getOrDefault("content-length","0");
        try{r.length=Long.parseLong(n);}catch(Exception e){throw new IOException("Invalid Content-Length");}
        if(r.length<0)throw new IOException("Invalid body length");
        r.body=in;return r;
    }
    private byte[] readSmall(Request r)throws IOException{
        if(r.length>16384)throw new IOException("Body too large");
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        byte[] chunk=new byte[4096];long left=r.length;
        while(left>0){
            int n=r.body.read(chunk,0,(int)Math.min(chunk.length,left));
            if(n<0)throw new IOException("Incomplete request");
            b.write(chunk,0,n);left-=n;
        }
        return b.toByteArray();
    }
    private void respond(OutputStream out,int code,String mime,byte[] data)throws IOException{
        String status=code==200?"OK":code==201?"Created":code==400?"Bad Request":code==401?"Unauthorized":code==404?"Not Found":code==413?"Payload Too Large":code==429?"Too Many Requests":"Internal Server Error";
        String h="HTTP/1.1 "+code+" "+status+"\r\nContent-Type: "+mime+"\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nContent-Length: "+data.length+"\r\nConnection: close\r\n\r\n";
        out.write(h.getBytes(StandardCharsets.UTF_8));out.write(data);out.flush();
    }
    private void json(OutputStream out,int code,String body)throws IOException{
        respond(out,code,"application/json; charset=utf-8",body.getBytes(StandardCharsets.UTF_8));
    }
    private String param(String query,String key){
        if(query==null)return "";
        for(String part:query.split("&")){
            String[] pair=part.split("=",2);
            if(pair.length==2&&pair[0].equals(key)){
                try{return URLDecoder.decode(pair[1],"UTF-8");}catch(Exception ignored){}
            }
        }
        return "";
    }
    private boolean authorized(Request r){return token.equals(r.headers.getOrDefault("x-device-token",""));}
    private synchronized boolean pairingAllowed(String submitted){
        long now=System.currentTimeMillis();
        if(now<lockedUntil)return false;
        if(pin.equals(submitted)){attempts=0;return true;}
        attempts++;
        if(attempts>=10){lockedUntil=now+60000;attempts=0;}
        return false;
    }
    private JSONObject from(Request r)throws Exception{return new JSONObject(new String(readSmall(r),StandardCharsets.UTF_8));}
    private String statusOnUi(){
        final String[] result={"{}"};
        CountDownLatch done=new CountDownLatch(1);
        main.post(()->{
            try{result[0]=activity.statusJson().toString();}finally{done.countDown();}
        });
        try{done.await(4,TimeUnit.SECONDS);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}
        return result[0];
    }
    private void upload(Request r,OutputStream out)throws Exception{
        if(r.length<=0){json(out,400,"{\"error\":\"Empty file\"}");return;}
        if(r.length>MAX_UPLOAD){json(out,413,"{\"error\":\"Limit is 500 MB\"}");return;}
        String proposed=param(r.rawQuery,"name");
        String name=proposed.replace('\\','/').replaceAll(".*/","").trim();
        if(name.isEmpty()||name.startsWith(".")||!MediaLibrary.supported(name)){
            json(out,400,"{\"error\":\"Use JPG, PNG, WEBP, MP4, M4V or WEBM\"}");return;
        }
        name=name.replaceAll("[\\x00-\\x1F]","_");
        String base=name,ext="";
        int dot=name.lastIndexOf('.');
        if(dot>0){base=name.substring(0,dot);ext=name.substring(dot);}
        File dest=new File(library.uploadsDir(),name);
        int n=2;
        while(dest.exists()){name=base+" ("+n+++ ")"+ext;dest=new File(library.uploadsDir(),name);}
        File tmp=File.createTempFile(".receiving-",".tmp",library.uploadsDir());
        boolean ok=false;
        try(FileOutputStream f=new FileOutputStream(tmp)){
            byte[] buf=new byte[64*1024];long remaining=r.length;
            while(remaining>0){
                int got=r.body.read(buf,0,(int)Math.min(buf.length,remaining));
                if(got<0)throw new IOException("Incomplete upload");
                f.write(buf,0,got);remaining-=got;
            }
            f.flush();
            ok=tmp.renameTo(dest);
            if(!ok)throw new IOException("Could not save file");
        }finally{if(!ok)tmp.delete();}
        activity.mediaChanged();
        json(out,201,"{\"ok\":true}");
    }
    private void handle(Socket socket){
        try(Socket s=socket){
            s.setSoTimeout(60000);
            Request r=readRequest(s.getInputStream());
            OutputStream out=s.getOutputStream();
            if("GET".equals(r.method)&&"/".equals(r.path)){
                try(InputStream asset=activity.getAssets().open("remote.html")){
                    ByteArrayOutputStream b=new ByteArrayOutputStream();
                    byte[] buf=new byte[8192];int len;
                    while((len=asset.read(buf))!=-1)b.write(buf,0,len);
                    respond(out,200,"text/html; charset=utf-8",b.toByteArray());
                }
                return;
            }
            if("POST".equals(r.method)&&"/api/pair".equals(r.path)){
                String supplied=from(r).optString("pin");
                if(!pairingAllowed(supplied)){json(out,401,"{\"error\":\"Wrong PIN or temporarily locked\"}");return;}
                JSONObject paired=new JSONObject();paired.put("token",token);paired.put("ok",true);
                json(out,200,paired.toString());return;
            }
            if(!authorized(r)){json(out,401,"{\"error\":\"Pair with the TV PIN\"}");return;}
            if("GET".equals(r.method)&&"/api/status".equals(r.path)){
                json(out,200,statusOnUi());return;
            }
            if("POST".equals(r.method)&&"/api/command".equals(r.path)){
                JSONObject cmd=from(r);
                CountDownLatch done=new CountDownLatch(1);
                final String[] appliedStatus={"{}"};
                main.post(()->{
                    try{
                        activity.command(cmd);
                        appliedStatus[0]=activity.statusJson().toString();
                    }finally{done.countDown();}
                });
                if(!done.await(4,TimeUnit.SECONDS)){
                    json(out,500,"{\"error\":\"TV did not apply command\"}");
                    return;
                }
                json(out,200,appliedStatus[0]);return;
            }
            if("POST".equals(r.method)&&"/api/upload".equals(r.path)){upload(r,out);return;}
            if("POST".equals(r.method)&&"/api/delete".equals(r.path)){
                String name=from(r).optString("name");
                if(library.deleteUploaded(name)){activity.mediaChanged();json(out,200,"{\"ok\":true}");}
                else json(out,400,"{\"error\":\"Only phone-uploaded files can be deleted\"}");
                return;
            }
            json(out,404,"{\"error\":\"Not found\"}");
        }catch(Exception ignored){
            // An interrupted connection must not stop the kiosk server.
        }
    }
}
