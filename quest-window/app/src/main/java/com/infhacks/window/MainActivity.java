package com.infhacks.window;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;

public class MainActivity extends Activity {
    private WebView web;
    private EditText url;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        url = new EditText(this);
        url.setSingleLine(true);
        url.setImeOptions(EditorInfo.IME_ACTION_GO);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String u) { url.setText(u); }
        });
        url.setOnEditorActionListener((v, id, e) -> { go(url.getText().toString()); return true; });
        root.addView(url, new LinearLayout.LayoutParams(-1, -2));
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        go(b == null ? "https://www.google.com" : b.getString("u", "https://www.google.com"));
    }

    private void go(String s) {
        s = s.trim();
        if (!s.contains("://")) s = s.contains(".") && !s.contains(" ")
            ? "https://" + s : "https://www.google.com/search?q=" + android.net.Uri.encode(s);
        web.loadUrl(s);
    }

    @Override protected void onSaveInstanceState(Bundle o) { o.putString("u", web.getUrl()); }

    @Override public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_BACK && web.canGoBack()) { web.goBack(); return true; }
        return super.onKeyDown(k, e);
    }
}
