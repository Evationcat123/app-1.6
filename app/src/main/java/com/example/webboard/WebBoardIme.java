package com.example.webboard;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputConnection;
import android.webkit.*;
import android.widget.*;
import java.util.*;

public class WebBoardIme extends InputMethodService {
    private static final int TEXT = Color.WHITE, MUTED = Color.rgb(185,190,200);
    private static final String PREFS="webboard_prefs", HISTORY="clipboard_history";
    private LinearLayout root, keys;
    private WebView web;
    private EditText url;
    private boolean shift=false, symbols=false, webInputFocused=false;
    private int bgColor, keyColor, specialColor, panelColor;
    private String backgroundMode;

    @Override public void onCreate() {
        super.onCreate();
        loadTheme();
    }

    @Override public void onStartInput(android.view.inputmethod.EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        addCurrentClipboard();
    }

    @Override public boolean onEvaluateFullscreenMode() {
        return true;
    }

    @Override public void onComputeInsets(Insets outInsets) {
        super.onComputeInsets(outInsets);
        // Tell the IME window that the entire content area belongs to the keyboard.
        outInsets.contentTopInsets = 0;
        outInsets.visibleTopInsets = 0;
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT;
    }

    @Override public View onCreateInputView() {
        loadTheme();
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(3),dp(3),dp(3),dp(3));
        applyBackground(root);

        // Full-screen keyboard mode: the input view is dedicated to the keyboard.
        // Browser controls are intentionally kept out of the IME surface so the
        // keyboard can use the complete available height and width.
        keys=new LinearLayout(this);
        keys.setOrientation(LinearLayout.VERTICAL);
        keys.setGravity(Gravity.FILL);
        root.addView(keys,new LinearLayout.LayoutParams(-1,0,1f));
        buildKeys();
        return root;
    }

    private View buildBrowserBar(){
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        url=new EditText(this);
        url.setSingleLine(true); url.setText("https://www.google.com/");
        url.setTextColor(TEXT); url.setHintTextColor(MUTED); url.setHint("Adresse oder Suche");
        url.setTextSize(14); url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        url.setShowSoftInputOnFocus(false); url.setPadding(dp(13),0,dp(8),0);
        url.setBackground(rounded(panelColor,24));
        url.setOnFocusChangeListener((v,has)->{if(has){webInputFocused=false;url.setSelection(url.length());}});
        url.setOnEditorActionListener((v,id,e)->{navigate();return true;});
        bar.addView(url,new LinearLayout.LayoutParams(0,-1,1f));

        // The broken link/speed-link key is intentionally removed.
        Button go=toolbarButton("↗"); go.setOnClickListener(v->navigate());
        Button back=toolbarButton("‹"); back.setOnClickListener(v->{if(web.canGoBack())web.goBack();});
        Button forward=toolbarButton("›"); forward.setOnClickListener(v->{if(web.canGoForward())web.goForward();});
        bar.addView(go,new LinearLayout.LayoutParams(dp(42),-1));
        bar.addView(back,new LinearLayout.LayoutParams(dp(38),-1));
        bar.addView(forward,new LinearLayout.LayoutParams(dp(38),-1));
        return bar;
    }

    private Button toolbarButton(String text){
        Button b=new Button(this); b.setText(text); b.setTextColor(TEXT); b.setTextSize(15);
        b.setAllCaps(false); b.setPadding(0,0,0,0); b.setBackground(rounded(panelColor,22)); return b;
    }

    private void buildKeys(){
        keys.removeAllViews();
        if(symbols){
            addRow("1234567890"); addRow("@#$%&*+-=/");
            LinearLayout r=row();
            addKey(r,"ABC",1.2f,v->{symbols=false;buildKeys();});
            addKey(r,"()[]{}",2f,v->type(((Button)v).getText().toString()));
            addKey(r,"!?;:'\"",2f,v->type(((Button)v).getText().toString()));
            addKey(r,"⌫",1.25f,v->delete());
            keys.addView(r,new LinearLayout.LayoutParams(-1,0,1f));
        }else{
            addRow("qwertzuiopü"); addRow("asdfghjklöä");
            LinearLayout r=row();
            addKey(r,"⇧",1.25f,v->{shift=!shift;buildKeys();});
            for(String c:new String[]{"y","x","c","v","b","n","m",",","."})
                addKey(r,c,1f,v->type(((Button)v).getText().toString()));
            addKey(r,"⌫",1.3f,v->delete());
            keys.addView(r,new LinearLayout.LayoutParams(-1,0,1f));
        }

        LinearLayout bottom=row();
        addKey(bottom,symbols?"ABC":"?123",1.15f,v->{symbols=!symbols;shift=false;buildKeys();});
        addKey(bottom,"📋",1f,v->showClipboard(v));
        addKey(bottom,"🎨",1f,v->showCustomization(v));
        addKey(bottom,"🌐",1f,v->focusBrowserInput());
        addKey(bottom,"Leertaste",3.8f,v->type(" "));
        addKey(bottom,".",1f,v->type("."));
        addKey(bottom,"↵",1.15f,v->enter());
        keys.addView(bottom,new LinearLayout.LayoutParams(-1,0,1f));
    }

    private void showClipboard(View anchor){
        addCurrentClipboard();
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10),dp(10),dp(10),dp(10)); box.setBackground(rounded(panelColor,18));
        TextView title=new TextView(this); title.setText("Zwischenablage • Gedächtnis");
        title.setTextColor(TEXT); title.setTextSize(16); title.setPadding(dp(8),dp(4),dp(8),dp(8)); box.addView(title);

        List<String> items=getHistory();
        if(items.isEmpty()){
            TextView empty=new TextView(this); empty.setText("Noch keine gespeicherten Texte.");
            empty.setTextColor(MUTED); empty.setPadding(dp(8),dp(8),dp(8),dp(8)); box.addView(empty);
        }else{
            for(String item:items){
                Button b=new Button(this); b.setText(item); b.setTextColor(TEXT); b.setTextSize(13);
                b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); b.setAllCaps(false); b.setMaxLines(2);
                b.setEllipsize(android.text.TextUtils.TruncateAt.END); b.setPadding(dp(10),0,dp(8),0);
                b.setBackground(rounded(keyColor,12));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48)); p.setMargins(0,dp(3),0,dp(3)); box.addView(b,p);
                b.setOnClickListener(v->{type(item);});
            }
        }
        PopupWindow popup=new PopupWindow(box,dp(310),Math.min(dp(430),getResources().getDisplayMetrics().heightPixels-dp(80)),true);
        popup.setBackgroundDrawable(rounded(panelColor,18)); popup.setOutsideTouchable(true); popup.setElevation(dp(12));
        popup.showAtLocation(root,Gravity.CENTER,0,0);
    }

    private void addCurrentClipboard(){
        ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(cm==null||!cm.hasPrimaryClip())return;
        ClipData d=cm.getPrimaryClip();
        if(d!=null&&d.getItemCount()>0){
            CharSequence cs=d.getItemAt(0).coerceToText(this);
            if(cs!=null&&!cs.toString().trim().isEmpty())saveHistory(cs.toString());
        }
    }

    private List<String> getHistory(){
        String raw=getSharedPreferences(PREFS,0).getString(HISTORY,"");
        List<String> out=new ArrayList<>();
        if(!raw.isEmpty())for(String x:raw.split("\\u0001",-1))if(!x.isEmpty())out.add(x);
        return out;
    }

    private void saveHistory(String value){
        List<String> h=getHistory(); h.remove(value); h.add(0,value);
        while(h.size()>20)h.remove(h.size()-1);
        getSharedPreferences(PREFS,0).edit().putString(HISTORY,joinHistory(h)).apply();
    }

    private String joinHistory(List<String> h){
        StringBuilder b=new StringBuilder();
        for(String x:h){if(b.length()>0)b.append('\u0001'); b.append(x.replace("\u0001"," "));}
        return b.toString();
    }

    private void showCustomization(View anchor){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12),dp(10),dp(12),dp(12)); box.setBackground(rounded(panelColor,18));
        TextView title=new TextView(this); title.setText("🎨 Tastatur anpassen"); title.setTextColor(TEXT); title.setTextSize(17);
        title.setPadding(dp(6),dp(4),dp(6),dp(8)); box.addView(title);
        TextView c1=section("Tastenfarbe"); box.addView(c1);
        LinearLayout colors=row();
        addThemeButton(colors,"Dunkel",Color.rgb(38,41,47),Color.rgb(52,56,64),"dark");
        addThemeButton(colors,"Hell",Color.rgb(232,234,238),Color.rgb(205,209,216),"light");
        addThemeButton(colors,"Blau",Color.rgb(35,70,115),Color.rgb(55,105,165),"blue");
        box.addView(colors,new LinearLayout.LayoutParams(-1,dp(54)));
        TextView c2=section("Hintergrund"); box.addView(c2);
        LinearLayout backs=row();
        addBackgroundButton(backs,"Schwarz","solid_dark");
        addBackgroundButton(backs,"Graphit","solid_graphite");
        addBackgroundButton(backs,"Blau","gradient_blue");
        box.addView(backs,new LinearLayout.LayoutParams(-1,dp(54)));
        Button done=new Button(this); done.setText("Übernehmen"); done.setAllCaps(false); done.setTextColor(TEXT);
        done.setBackground(rounded(specialColor,12)); box.addView(done,new LinearLayout.LayoutParams(-1,dp(48)));
        PopupWindow popup=new PopupWindow(box,dp(330),dp(300),true);
        popup.setBackgroundDrawable(rounded(panelColor,18)); popup.setOutsideTouchable(true); popup.setElevation(dp(12));
        popup.showAtLocation(root,Gravity.CENTER,0,0);
        done.setOnClickListener(v->{popup.dismiss();rebuildWithTheme();});
    }

    private TextView section(String t){
        TextView x=new TextView(this); x.setText(t); x.setTextColor(MUTED); x.setTextSize(12); x.setPadding(dp(6),dp(8),dp(6),dp(2)); return x;
    }

    private void addThemeButton(LinearLayout row,String label,int kc,int sc,String mode){
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(TEXT); b.setTextSize(12); b.setBackground(rounded(kc,12));
        b.setOnClickListener(v->{getSharedPreferences(PREFS,0).edit().putString("theme",mode).apply();});
        row.addView(b,new LinearLayout.LayoutParams(0,-1,1f));
    }

    private void addBackgroundButton(LinearLayout row,String label,String mode){
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(TEXT); b.setTextSize(12); b.setBackground(rounded(Color.rgb(55,58,65),12));
        b.setOnClickListener(v->{getSharedPreferences(PREFS,0).edit().putString("background",mode).apply();});
        row.addView(b,new LinearLayout.LayoutParams(0,-1,1f));
    }

    private void rebuildWithTheme(){
        loadTheme();
        if(root!=null){applyBackground(root);buildKeys();}
    }

    private void loadTheme(){
        SharedPreferences p=getSharedPreferences(PREFS,0);
        String theme=p.getString("theme","dark");
        if("light".equals(theme)){bgColor=Color.rgb(246,247,249);panelColor=Color.rgb(225,228,234);keyColor=Color.rgb(232,234,238);specialColor=Color.rgb(205,209,216);}
        else if("blue".equals(theme)){bgColor=Color.rgb(12,24,42);panelColor=Color.rgb(25,43,68);keyColor=Color.rgb(35,70,115);specialColor=Color.rgb(55,105,165);}
        else {bgColor=Color.rgb(16,17,20);panelColor=Color.rgb(25,27,31);keyColor=Color.rgb(38,41,47);specialColor=Color.rgb(52,56,64);}
        backgroundMode=p.getString("background","solid_dark");
    }

    private void applyBackground(View v){
        if(v==null)return;
        if("gradient_blue".equals(backgroundMode)){
            GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(10,27,52),Color.rgb(30,65,105)});
            g.setCornerRadius(dp(8)); v.setBackground(g);
        }else if("solid_graphite".equals(backgroundMode))v.setBackgroundColor(Color.rgb(29,31,36));
        else if("solid_dark".equals(backgroundMode))v.setBackgroundColor(Color.rgb(8,9,11));
        else v.setBackgroundColor(bgColor);
    }

    private void focusBrowserInput(){webInputFocused=false;url.requestFocus();url.setSelection(url.length());}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);return r;}

    private void addRow(String chars){
        LinearLayout r=row();
        for(int i=0;i<chars.length();i++){String c=String.valueOf(chars.charAt(i));addKey(r,c,1f,v->type(((Button)v).getText().toString()));}
        keys.addView(r,new LinearLayout.LayoutParams(-1,0,1f));
    }

    private void addKey(LinearLayout row,String label,float weight,View.OnClickListener listener){
        Button b=new Button(this); String shown=label;
        if(shift&&label.length()==1&&Character.isLetter(label.charAt(0)))shown=label.toUpperCase(Locale.GERMANY);
        b.setText(shown); b.setTextColor(TEXT); b.setTextSize(label.equals("Leertaste")?12:16); b.setAllCaps(false);
        b.setPadding(0,0,0,0);
        int c=(label.equals("⇧")||label.equals("⌫")||label.equals("?123")||label.equals("ABC")||label.equals("📋")||label.equals("🎨"))?specialColor:keyColor;
        b.setBackground(rounded(c,10)); b.setOnClickListener(listener);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,weight);p.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(b,p);
    }

    private GradientDrawable rounded(int color,int radiusDp){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radiusDp));return d;}

    private void type(String text){
        if(shift){text=text.toUpperCase(Locale.GERMANY);shift=false;buildKeys();}
        if(url!=null&&url.hasFocus()){int s=Math.max(0,url.getSelectionStart()),e=Math.max(0,url.getSelectionEnd());url.getText().replace(Math.min(s,e),Math.max(s,e),text);url.setSelection(Math.min(s,e)+text.length());return;}
        if(webInputFocused&&web!=null){web.evaluateJavascript("window.WebBoardInsert("+quoteJs(text)+");",null);return;}
        InputConnection ic=getCurrentInputConnection();if(ic!=null)ic.commitText(text,1);
    }

    private void delete(){
        if(url!=null&&url.hasFocus()){int s=url.getSelectionStart(),e=url.getSelectionEnd();if(s!=e)url.getText().delete(Math.min(s,e),Math.max(s,e));else if(s>0)url.getText().delete(s-1,s);return;}
        if(webInputFocused&&web!=null){web.evaluateJavascript("window.WebBoardDelete();",null);return;}
        InputConnection ic=getCurrentInputConnection();if(ic!=null)ic.deleteSurroundingText(1,0);
    }

    private void enter(){
        if(url!=null&&url.hasFocus()){navigate();return;}
        if(webInputFocused&&web!=null){web.evaluateJavascript("window.WebBoardEnter();",null);return;}
        InputConnection ic=getCurrentInputConnection();
        if(ic!=null){ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));}
    }

    private void navigate(){
        if(url==null||web==null)return;String q=url.getText().toString().trim();if(q.isEmpty())return;
        String target=q.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")?q:"https://www.google.com/search?q="+android.net.Uri.encode(q);
        webInputFocused=false;web.loadUrl(target);url.clearFocus();web.requestFocus();
    }

    private void injectFocusBridge(){
        if(web==null)return;
        String js="(function(){if(window.__webboardInstalled)return;window.__webboardInstalled=true;"+
        "window.WebBoardInsert=function(t){var e=document.activeElement;if(!e)return;if(e.isContentEditable){document.execCommand('insertText',false,t);}else if('value'in e){var s=e.selectionStart==null?e.value.length:e.selectionStart,z=e.selectionEnd==null?s:e.selectionEnd,p=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value');if(p&&p.set)p.set.call(e,e.value.slice(0,s)+t+e.value.slice(z));else e.value=e.value.slice(0,s)+t+e.value.slice(z);if(e.setSelectionRange)e.setSelectionRange(s+t.length,s+t.length);e.dispatchEvent(new Event('input',{bubbles:true}));}};"+
        "window.WebBoardDelete=function(){var e=document.activeElement;if(!e)return;if(e.isContentEditable){document.execCommand('delete',false,null);}else if('value'in e){var s=e.selectionStart||0,z=e.selectionEnd||0;if(s===z&&s>0)s--;var p=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value');if(p&&p.set)p.set.call(e,e.value.slice(0,s)+e.value.slice(z));else e.value=e.value.slice(0,s)+e.value.slice(z);if(e.setSelectionRange)e.setSelectionRange(s,s);e.dispatchEvent(new Event('input',{bubbles:true}));}};"+
        "window.WebBoardEnter=function(){var e=document.activeElement;if(!e)return;var ev=new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true});e.dispatchEvent(ev);if(e.tagName==='TEXTAREA'||e.isContentEditable)window.WebBoardInsert('\\n');else if(e.form&&e.type!=='button')e.form.requestSubmit?e.form.requestSubmit():e.form.submit();};"+
        "document.addEventListener('focusin',function(ev){var e=ev.target;if(e&&((e.matches&&e.matches('input,textarea,select,[contenteditable=true]'))||e.isContentEditable))WebBoard.setWebInputFocused(true);},true);"+
        "document.addEventListener('focusout',function(){setTimeout(function(){var e=document.activeElement;if(!(e&&((e.matches&&e.matches('input,textarea,select,[contenteditable=true]'))||e.isContentEditable)))WebBoard.setWebInputFocused(false);},100);},true);})();";
        web.evaluateJavascript(js,null);
    }

    private String quoteJs(String value){return "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";}
    public class BrowserBridge{@JavascriptInterface public void setWebInputFocused(boolean focused){webInputFocused=focused;}}
    private int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+0.5f);}
}
