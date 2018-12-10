package com.example.testkeycode;

import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final WebView webview = findViewById(R.id.webview);
        WebSettings settings = webview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        /**
         * 监听WebView的加载状态    分别为 ： 加载的 前 中 后期
         * */
        webview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
            }

            @Override
            public void onPageFinished(final WebView view, String url) {
                String str = "javascript:document.getElementsByClassName('c-blocka c-color-gray-a hint-unsafe-expand  hint-unsafe-expand1')[0].firstElementChild.innerText='lijing'";
                webview.evaluateJavascript(str, new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        Log.i("lijing", "onReceiveValue: value == "+ value);
                    }
                });
            }

        });

        webview.loadUrl("https://m.baidu.com/s?word=%E7%88%B1%E5%A5%87%E8%89%BA%E6%92%AD%E6%94%BE%E5%99%A8%E5%AE%98%E6%96%B9&sa=tb&ts=5531915&t_kt=0&ie=utf-8&rsv_t=2e38T5seqKYL5NCMiU3mglgDbxLHqn3V25jXY%252BugGfdwqukVX%252BRQ&rsv_pq=7650761966536858103&ss=100&tj=1&rqlang=zh&rsv_sug4=142&oq=%E7%88%B1%E5%A5%87%E8%89%BA%E6%92%AD%E6%94%BE%E5%99%A8%E5%AE%98%E6%96%B9");

    }

}
