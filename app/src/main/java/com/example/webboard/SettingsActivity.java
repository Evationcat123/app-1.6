package com.example.webboard;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;

public class SettingsActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32,32,32,32);
        TextView title = new TextView(this); title.setText("WebBoard"); title.setTextSize(28); root.addView(title);
        TextView info = new TextView(this); info.setText("Eine Tastatur mit integriertem Browser. Öffne Androids Tastaturauswahl und wähle WebBoard."); info.setTextSize(16); info.setPadding(0,20,0,20); root.addView(info);
        Button enable = new Button(this); enable.setText("Tastaturen verwalten"); enable.setOnClickListener(v -> startActivity(new android.content.Intent("android.settings.INPUT_METHOD_SETTINGS"))); root.addView(enable);
        Button picker = new Button(this); picker.setText("Tastatur auswählen"); picker.setOnClickListener(v -> ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker()); root.addView(picker);
        setContentView(root);
    }
}
