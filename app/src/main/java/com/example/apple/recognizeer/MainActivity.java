package com.example.apple.recognizeer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.webkit.CookieManager;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends BaseActivity {

    public String YAN = Utils.getRandomString();//用于识别输入框的关键字
    public String YAN_UNSAFE = Utils.getRandomString();
    public static final String KW = "活性炭";
    public static final String GOAL = "www.21hxt.com";
    public static final String GOAL1 = "www.lyl.com.cn";
    public static final String GOAL2 = "www.yll.com.cn";

    private WebView webView;
    private TextView tvShow;

    private MediaProjectionManager mMediaProjectionManager;
    private TextRecognizer textRecognizer;
    private VirtualDisplay mVirtualDisplay;
    private ImageReader imageReader;
    private WebJumpController webJumpController;


    private boolean isResume;
    private int width;
    private int height;
    private boolean isNeedRecongnizeer = true;//第一次识别，每次识别完成后就置为false，防止多次识别同一内容

    private final int MSG_INIT = 1;//初始状态
    private final int MSG_FIRST_GET_INPUT_BOUND = 2;//第一次获取输入框位置前的状态
    private final int MSG_SECOND_GET_INPUT_BOUND = 3;//第二次获取输入框位置前的状态
    private final int MSG_SOUSUO = 4;//短暂性的， 点击百度一下按钮后的状态
    private final int MSG_SOUSUO_FINISHED = 5;// 搜索出结果后的状态
    private final int MSG_SOUSUO_FINISHED_NEXTPAGE = 6;// webview点击下一页继续查找
    private final int MSG_FINDING_SCROLL = 7;// 监测到当前内容中包含了识别目标，需要滑动来定位item的位置
    private final int MSG_FINDED = 8;// 定位完成，将要执行下一次循环

    private int currState = MSG_INIT;

    private Handler handler;

    private Rect finalInputRect;// 第二次获取的输入框的位置，

    private TestNecrosisRunnable testNecrosisRunnable;

    public ExecutorService executorService = Executors.newFixedThreadPool(2);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //判断是否需要清除cookie
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(null);


        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        testNecrosisRunnable = new TestNecrosisRunnable();
        executorService.execute(testNecrosisRunnable);

        initHandler();

        webView = findViewById(R.id.webview);
        tvShow = findViewById(R.id.iv_show);

        webJumpController = new WebJumpController(this, webView, tvShow,new WebJumpController.WebJumpListener() {
            @Override
            public void onFindedUnsafeElement() {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        autoReplaceUnsafeElementText();
                    }
                }, 2000);
            }

            @Override
            public void onFindedTargetPage() {
                restartRecongnizer(MSG_FINDING_SCROLL);
                Log.i("lijing", "------------------onFindedTargetPage");
            }

            @Override
            public void onWebViewPageRefreshFinished(String url) {

                if (currState == MSG_INIT) {//加载完成了关键字，将要识别输入框的位置
                    handler.sendEmptyMessageDelayed(MSG_INIT, 500);
                } else if (currState == MSG_SOUSUO) {//搜索完成，开始检测目标网站
                    currState = MSG_SOUSUO_FINISHED;
                    Message msg = Message.obtain();
                    msg.what = MSG_SOUSUO_FINISHED;
                    Bundle bundle = new Bundle();
                    bundle.putString("url", url);
                    msg.setData(bundle);
                    handler.sendMessage(msg);
                } else if (currState == MSG_FINDED) {//完成本次操作
                    handler.sendEmptyMessageDelayed(MSG_FINDED, 2000);
                }

                if (testNecrosisRunnable != null) {
                    //每次webview页面刷新就将时间标识置为当前时间
                    testNecrosisRunnable.makeZero();
                }

            }
        });

        // 初始加载带有关键字的页面
        webView.loadUrl("https://m.baidu.com/s?word=" + YAN);

        textRecognizer = new TextRecognizer.Builder(this).build();

        //请求录屏
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent screenCaptureIntent = mMediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(screenCaptureIntent, 110);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Display defaultDisplay = getWindowManager().getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        defaultDisplay.getMetrics(metrics);
        width = defaultDisplay.getWidth();
        height = defaultDisplay.getHeight();
        int densityDpi = metrics.densityDpi;

        MediaProjection mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);

        mVirtualDisplay = mediaProjection.createVirtualDisplay("screen-mirror", width,
                height, densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);


        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {

                Image image = imageReader.acquireLatestImage();


                if (isResume && isNeedRecongnizeer) {

                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();

                    int pixelStride = image.getPlanes()[0].getPixelStride();
                    int rowStride = image.getPlanes()[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * width;

                    Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);

                    Frame frame = new Frame.Builder()
                            .setBitmap(bitmap)
                            .build();

                    Rect inputRect = null;
                    Rect goalRect = null;
                    Rect unsafeRect = null;

                    //得到所有识别到的文字及其位置
                    SparseArray<TextBlock> items = textRecognizer.detect(frame);

                    for (int i = 0; i < items.size(); ++i) {
                        TextBlock item = items.valueAt(i);
                        String value = item.getValue();
                        if (currState == MSG_SECOND_GET_INPUT_BOUND || currState == MSG_FIRST_GET_INPUT_BOUND) {

                            if (YAN.equals(value) || value.contains(YAN)) {//这个就是输入框中的文字，所对应的位置就是输入框的位置
                                inputRect = item.getBoundingBox();

                                if (currState == MSG_FIRST_GET_INPUT_BOUND) {
                                    if (inputRect.centerY() > height / 2) {//有事可能有两个输入框，底部的输入框不算数
                                        continue;
                                    }

                                    //handler接收到此消息后会执行模拟点击输入框
                                    Message msg = Message.obtain();
                                    msg.what = MSG_FIRST_GET_INPUT_BOUND;
                                    Bundle bundle = new Bundle();
                                    bundle.putParcelable("rect", inputRect);
                                    msg.setData(bundle);
                                    handler.sendMessage(msg);
                                } else if (currState == MSG_SECOND_GET_INPUT_BOUND) {
                                    finalInputRect = inputRect;

                                    //handler接收到此消息后会输入最终关键字
                                    handler.sendEmptyMessage(MSG_SECOND_GET_INPUT_BOUND);
                                }

                                break;
                            }
                        } else if (currState == MSG_FINDING_SCROLL) {//滑动来寻找目标
                            value = value.toLowerCase();
                            if (GOAL.equals(value) || value.contains(GOAL)
                                    || GOAL1.equals(value) || value.contains(GOAL1)
                                    || GOAL2.equals(value) || value.contains(GOAL2)) {
                                //获取到目标了
                                goalRect = item.getBoundingBox();
                                break;
                            } else if (YAN_UNSAFE.equals(value) || value.contains(YAN_UNSAFE)) {//别隐藏起来的内容，百度会把有些不安全连接给隐藏起来，暂时先不做处理
                                unsafeRect = item.getBoundingBox();
                                autoClick(unsafeRect.centerX(), unsafeRect.centerY());
                                break;
                            }
                        }
                    }

                    if (currState == MSG_SECOND_GET_INPUT_BOUND || currState == MSG_FIRST_GET_INPUT_BOUND) {
                        if (inputRect == null) {//如果当前没有输入框，无法执行下一步操作，直接开始下一轮
                            restart();
                        }
                    } else if (currState == MSG_FINDING_SCROLL) {


                        Message msg = Message.obtain();
                        msg.what = MSG_FINDING_SCROLL;
                        Bundle bundle = new Bundle();
                        bundle.putParcelable("goalRect", goalRect);//goalRect为空，需要往下滑，否则就是找到了目标，直接点击完成本次循环
                        bundle.putParcelable("unsafeRect", unsafeRect);
                        msg.setData(bundle);
                        handler.sendMessage(msg);
                    }

                    isNeedRecongnizeer = false;// 防止重复识别浪费性能

                }
                if (image != null) {
                    image.close();
                }

            }
        }, null);

    }

    // 需要识别当前页面的内容时调用
    private void restartRecongnizer(int state) {
        currState = state;
        isNeedRecongnizeer = true;
    }


    @Override
    protected void onResume() {
        super.onResume();
        isResume = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isResume = false;
    }

    /**
     * 模拟点击事件
     *
     * @param x
     * @param y
     */
    private void autoClick(int x, int y) {
        long uptimeMillis = SystemClock.uptimeMillis();
        final long downTime = uptimeMillis;
        MotionEvent motionEvent_down = MotionEvent.obtain(downTime, uptimeMillis, MotionEvent.ACTION_DOWN, x, y, 0);
        dispatchTouchEvent(motionEvent_down);

        MotionEvent motionEvent_up = MotionEvent.obtain(downTime, uptimeMillis, MotionEvent.ACTION_UP, x, y, 0);
        dispatchTouchEvent(motionEvent_up);

    }

    /**
     * 自动滑动，只有当前页面检测到了目标网站才会调用
     */
    private void autoScroll() {
        long uptimeMillis = SystemClock.uptimeMillis();
        final long downTime = uptimeMillis;
        final int downX = 500;
        int downY = 1500;

        MotionEvent motionEvent_down = MotionEvent.obtain(downTime, uptimeMillis, MotionEvent.ACTION_DOWN, downX, downY, 0);
        dispatchTouchEvent(motionEvent_down);

        for (int i = 0; i <= 10; i++) {
            downY -= 100;
            uptimeMillis += 100;
            MotionEvent motionEvent_move = MotionEvent.obtain(downTime, uptimeMillis, MotionEvent.ACTION_MOVE, downX, downY, 0);
            dispatchTouchEvent(motionEvent_move);
        }

        final long finalUptimeMillis = uptimeMillis;
        final int finalDownY = downY;
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                SystemClock.sleep(100);

                MotionEvent motionEvent_move = MotionEvent.obtain(downTime, finalUptimeMillis, MotionEvent.ACTION_MOVE, downX, finalDownY, 0);
                dispatchTouchEvent(motionEvent_move);

                MotionEvent motionEvent_up = MotionEvent.obtain(downTime, finalUptimeMillis, MotionEvent.ACTION_UP, downX, finalDownY, 0);
                dispatchTouchEvent(motionEvent_up);

                SystemClock.sleep(100);
                restartRecongnizer(MSG_FINDING_SCROLL);
            }
        });

    }


    @SuppressLint("HandlerLeak")
    private void initHandler() {
        handler = new Handler() {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_INIT:
                        //页面初次加载完成，开始寻找输入框
                        restartRecongnizer(MSG_FIRST_GET_INPUT_BOUND);
                        Log.i("lijing", "------------------start");
                        break;
                    case MSG_FIRST_GET_INPUT_BOUND:
                        //第一次找到输入框
                        Bundle data = msg.getData();
                        Rect rect = data.getParcelable("rect");
                        Log.i("lijing", "------------------第一次找到输入框");
                        autoClick(rect.centerX(), rect.centerY());

                        postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                //开始第二次寻找输入框
                                restartRecongnizer(MSG_SECOND_GET_INPUT_BOUND);
                            }
                        }, 2000);

                        break;
                    case MSG_SECOND_GET_INPUT_BOUND:
                        Log.i("lijing", "------------------第二次找到输入框-----");
                        //第二次寻找到输入框，键入最终关键字
                        autoInputKeyword();

                        break;

                    case MSG_SOUSUO:
                        //输入关键字完成，百度一下
                        Log.i("lijing", "------------------输入关键字完成");
                        autoClickSouSuo();
                        break;

                    case MSG_SOUSUO_FINISHED:
                        // 搜索完成，开始寻找目标网站
                        Log.i("lijing", "------------------搜索完成");
                        Bundle urlBundle = msg.getData();
                        String url = urlBundle.getString("url");
                        webJumpController.requestJsoupData(url);
                        break;

                    case MSG_FINDING_SCROLL:
                        // 通过向下滑动识别出来当前屏幕显示的内容
                        Bundle bundle = msg.getData();
                        Rect goalRect = bundle.getParcelable("goalRect");
                        Rect unsafeRect = bundle.getParcelable("unsafeRect");
                        if (unsafeRect != null) {//百度对有些不安全网址隐藏
                            restartRecongnizer(MSG_FINDING_SCROLL);
                        } else if (goalRect == null) {//当前屏幕内没有定位到目标网站，继续向下滑动
                            autoScroll();
                        } else {//当前屏幕内已经定位到目标网站，点进去
                            autoClick(goalRect.centerX(), goalRect.centerY());
                            currState = MSG_FINDED;
                        }
                        break;

                    case MSG_FINDED:

                        restart();
                        break;
                }
            }
        };
    }

    private void restart() {

        nip();
        startActivity(new Intent(this, EmptyActivity.class));
    }

    /**
     * 清空回收
     */
    public void nip() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        handler.removeCallbacksAndMessages(null);
        RecognizerApp.getInstance().deleteCookie();
        if (testNecrosisRunnable != null) {
            testNecrosisRunnable.finish();
        }

        finish();
    }


    private void autoInputKeyword() {

        final String strJS = String.format("javascript:document.getElementById('kw').value='%s';", KW);

        webView.evaluateJavascript(strJS, null);
        handler.sendEmptyMessageDelayed(MSG_SOUSUO, 500);

    }

    //替换百度折叠隐藏内容按钮的文字，暂时先不考虑
    private void autoReplaceUnsafeElementText() {

        String strJS = "javascript:document.getElementsByClassName('c-blocka c-color-gray-a hint-unsafe-expand  hint-unsafe-expand1')[0].firstElementChild.innerText='" + YAN_UNSAFE + "'";
        webView.evaluateJavascript(strJS, null);

    }

    //点击百度一下
    private void autoClickSouSuo() {
        if (finalInputRect != null) {
            int[] display = Utils.getDisplay(this);

            autoClick(display[0] * 9 / 10, finalInputRect.centerY());
            currState = MSG_SOUSUO;
        }
    }


    class TestNecrosisRunnable implements Runnable {
        private long time_test_necrosis;
        private boolean execute = true;

        public TestNecrosisRunnable() {
            makeZero();
        }

        public void makeZero() {
            time_test_necrosis = System.currentTimeMillis();
        }

        public void finish() {
            execute = false;
        }

        @Override
        public void run() {
            while (execute) {
                long systemTime = System.currentTimeMillis();
                if (systemTime - time_test_necrosis > 10000) {//当webview10秒没反应就强制退出

                    Log.e("lijing", "------------" + MainActivity.this.hashCode());
                    handler.sendEmptyMessage(MSG_FINDED);
                    return;
                }
                SystemClock.sleep(300);
            }
        }
    }

}
