package in.sanjusk.tvdisplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import org.json.JSONArray;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class MediaLibrary {
    static final class Item {
        final String name;
        final Uri uri;
        final boolean video;
        final boolean uploaded;
        Item(String name, Uri uri, boolean video, boolean uploaded) {
            this.name=name; this.uri=uri; this.video=video; this.uploaded=uploaded;
        }
        String key() { return (uploaded ? "up:" : "usb:") + name; }
    }
    private final Context context;
    private final SharedPreferences prefs;
    private final File uploads;
    MediaLibrary(Context context, SharedPreferences prefs) {
        this.context=context; this.prefs=prefs;
        uploads=new File(context.getFilesDir(),"uploads");
        if (!uploads.exists()) uploads.mkdirs();
    }
    File uploadsDir() { return uploads; }
    static boolean supported(String name) {
        String n=name.toLowerCase(Locale.US);
        return n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".png")
            ||n.endsWith(".webp")||n.endsWith(".mp4")||n.endsWith(".m4v")||n.endsWith(".webm");
    }
    static boolean video(String name) {
        String n=name.toLowerCase(Locale.US);
        return n.endsWith(".mp4")||n.endsWith(".m4v")||n.endsWith(".webm");
    }
    List<Item> list() {
        List<Item> result=new ArrayList<>();
        File[] local=uploads.listFiles();
        if(local!=null) for(File f:local) {
            if(f.isFile()&&supported(f.getName())) result.add(new Item(f.getName(),Uri.fromFile(f),video(f.getName()),true));
        }
        String saved=prefs.getString("folderUri","");
        if(!saved.isEmpty()) try {
            Uri tree=Uri.parse(saved);
            String id=DocumentsContract.getTreeDocumentId(tree);
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
            String[] cols={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};
            try(Cursor c=context.getContentResolver().query(children,cols,null,null,null)) {
                if(c!=null) while(c.moveToNext()) {
                    String childId=c.getString(0), name=c.getString(1), mime=c.getString(2);
                    if(name!=null && supported(name) && !DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        result.add(new Item(name,DocumentsContract.buildDocumentUriUsingTree(tree,childId),video(name),false));
                    }
                }
            }
        } catch(Exception ignored) { }
        Collections.sort(result,Comparator.comparing(i->i.name.toLowerCase(Locale.US)));
        try {
            JSONArray savedOrder=new JSONArray(prefs.getString("order","[]"));
            Map<String,Integer> positions=new HashMap<>();
            for(int i=0;i<savedOrder.length();i++) positions.put(savedOrder.optString(i),i);
            result.sort((a,b)->{
                int ai=positions.getOrDefault(a.key(),Integer.MAX_VALUE),bi=positions.getOrDefault(b.key(),Integer.MAX_VALUE);
                if(ai!=bi) return Integer.compare(ai,bi);
                return a.name.compareToIgnoreCase(b.name);
            });
        } catch(Exception ignored) { }
        return result;
    }
    void saveOrder(List<Item> items) {
        JSONArray a=new JSONArray();
        for(Item i:items) a.put(i.key());
        prefs.edit().putString("order",a.toString()).apply();
    }
    boolean deleteUploaded(String name) {
        if(name==null || name.contains("/")||name.contains("\\")||name.contains("..")) return false;
        File f=new File(uploads,name);
        return f.isFile() && f.delete();
    }
}
