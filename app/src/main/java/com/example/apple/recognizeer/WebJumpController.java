package com.example.apple.recognizeer;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

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

    private Activity activity;
    private WebView webview;
    private boolean isLoading;
    private WebJumpListener listener;
    private boolean containGoalOnScreen;

    public WebJumpController(Activity activity, final WebView webview, @NonNull final WebJumpListener webJumpListener) {

        this.activity = activity;
        this.webview = webview;
        this.listener = webJumpListener;

        WebSettings settings = webview.getSettings();
        settings.setUserAgentString(RecognizerApp.getInstance().getCurrUseragent());
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
                isLoading = true;
            }

            @Override
            public void onPageFinished(final WebView view, String url) {
                isLoading = false;


                if (listener != null) {
                    listener.onWebViewPageRefreshFinished(url);
                }
            }

        });


    }


    void requestJsoupData(final String url) {
        //这里需要放在子线程中完成，否则报这个错android.os.NetworkOnMainThreadException
        new Thread(new Runnable() {
            @Override
            public void run() {
                jsoupListData(url);
            }
        }).start();
    }


    private void jsoupListData(String url) {

        try {//捕捉异常

            Document document = Jsoup.connect(url).get();//这里可用get也可以post方式，具体区别请自行了解


            Element result = document.getElementById("results");//请求的列表正文

            //判断当前请求的页面有没有目标
            Elements list = result.getElementsByAttribute("order");
            for (int i = 0; i< list.size() ; i++) {
                Element element = list.get(i);
                String text = element.attr("data-log");

                if (!TextUtils.isEmpty(text)) {
                    JSONObject jsonObject = new JSONObject(text);
                    String mu = jsonObject.getString("mu");

                    if (!TextUtils.isEmpty(mu)) {

                        if (mu.startsWith(MainActivity.GOAL) || mu.contains(MainActivity.GOAL)) {



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
                if (pageOnlyLeft != null) {
                    for (Element link : pageOnlyLeft) {
                        String pageOnlyLeftHref = link.attr("href");
                        loadNextPageUrl(pageOnlyLeftHref);
                        Log.i("lijing", "pageOnlyLeftHref == " + pageOnlyLeftHref);
                    }
                } else {
                    Elements pageLeft = element.select("[class=new-pagenav-left]");
                    Elements pageRight = element.select("[class=new-pagenav-right]");

                    Elements a = pageRight.select("a[href]");
                    for (Element link : a) {
                        String href = link.attr("href");
                        loadNextPageUrl(href);
                        Log.i("lijing", "rightHref == " + href);
                    }
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void loadNextPageUrl(String url) {


        final String finalUrl = url;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                webview.loadUrl(finalUrl);
            }
        });


        while (isLoading) {
            SystemClock.sleep(50);
        }
        requestJsoupData(finalUrl);

    }

    public interface WebJumpListener {
        void onFindedUnsafeElement();
        void onFindedTargetPage();

        void onWebViewPageRefreshFinished(String url);
    }
}

