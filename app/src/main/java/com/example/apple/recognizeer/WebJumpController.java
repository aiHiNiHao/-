package com.example.apple.recognizeer;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


/**
 * webView页面
 * Created by li'jing'da'niu on 2018/12/01.
 */
public class WebJumpController {

    private MainActivity mainActivity;
    private WebView webview;
    private TextView textView;
    private boolean isLoading;
    private WebJumpListener listener;


    int page;

    public WebJumpController(MainActivity activity, final WebView webview, TextView tvShow, @NonNull final WebJumpListener webJumpListener) {

        this.mainActivity = activity;
        this.webview = webview;
        this.textView = tvShow;
        this.listener = webJumpListener;

        WebSettings settings = webview.getSettings();
        String currUseragent = RecognizerApp.getInstance().getCurrUseragent();
        settings.setUserAgentString(currUseragent);
        settings.setJavaScriptEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                String hostIP = RecognizerApp.getInstance().getHostIP();
                textView.setText(hostIP);
                isLoading = true;
            }

            @Override
            public void onPageFinished(final WebView view, String url) {
                isLoading = false;
                Log.i("lijing web url", url);

                if (listener != null) {
                    listener.onWebViewPageRefreshFinished(url);
                }
            }

        });
    }

    void requestJsoupData(final String url) {
        mainActivity.executorService.execute(new Runnable() {
            @Override
            public void run() {
                jsoupListData(url);
            }
        });
    }

    private void jsoupListData(String url) {

        try {//捕捉异常

            Document document = Jsoup.connect(url).get();//这里可用get也可以post方式，具体区别请自行了解

            Element result = document.getElementById("results");//请求的列表正文

            //判断当前请求的页面有没有目标
            Elements list = result.getElementsByAttribute("order");
            for (int i = 0; i < list.size(); i++) {
                Element element = list.get(i);
                String text = element.attr("data-log");

                if (!TextUtils.isEmpty(text)) {
                    JSONObject jsonObject = new JSONObject(text);
                    String mu = jsonObject.getString("mu");

                    if (!TextUtils.isEmpty(mu)) {
                        Log.i("lijing", "mu: " + mu);
                        if (mu.startsWith(MainActivity.GOAL) || mu.contains(MainActivity.GOAL)) {//当前页面中包含目标网站

                            if (this.listener != null) {
                                this.listener.onFindedTargetPage();
                            }
                            return;
                        }
                    }
                }

            }

            Element page = document.getElementById("page-controller");
            Elements pageControllers = page.getElementsByAttributeValue("class", "new-pagenav c-flexbox");

            for (Element element : pageControllers) {

                Elements pageOnlyLeft = element.select("[class=new-nextpage-only]");
                if (pageOnlyLeft != null && pageOnlyLeft.size() > 0) {//搜索结果第一页
                    for (Element link : pageOnlyLeft) {
                        String pageOnlyLeftHref = link.attr("href");
                        loadNextPageUrl(pageOnlyLeftHref);
                    }
                } else {//搜索结果不是第一页
                    Elements pageLeft = element.select("[class=new-pagenav-left]");
                    Elements pageRight = element.select("[class=new-pagenav-right]");

                    Elements a = pageRight.select("a[href]");
                    for (Element link : a) {
                        String href = link.attr("href");
                        loadNextPageUrl(href);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadNextPageUrl(String url) {
        Log.i("lijingweb url", url);

        if (page > 5){
            if (listener != null) {
                listener.onPageCountOutOfBound();
            }
            return;
        }
        final String finalUrl = url;
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                webview.loadUrl(finalUrl);
            }
        });

        while (isLoading) {
            SystemClock.sleep(50);
        }
        requestJsoupData(finalUrl);
        page++;
    }


    public interface WebJumpListener {
        /**
         * 存在百度隐藏折叠内容的情况
         */
        void onFindedUnsafeElement();

        /**
         * 当前页面包含有目标
         */
        void onFindedTargetPage();

        /**
         * webview页面刷新
         */
        void onWebViewPageRefreshFinished(String url);

        /**
         * webview页面刷新
         */
        void onPageCountOutOfBound();
    }
}

