package com.example.apple.recognizeer;

import android.annotation.SuppressLint;
import android.app.Instrumentation;
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
import android.os.Looper;
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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    public static final String YAN = "kdsfgjkgdsklgkdlg";
    public static final String KW = "电影网站";
    public static final String GOAL = "https://m.xigua110.com";

    private WebView webView;
    private ImageView ivShow;

    private MediaProjectionManager mMediaProjectionManager;
    private TextRecognizer textRecognizer;
    private VirtualDisplay mVirtualDisplay;
    private ImageReader imageReader;
    private WebJumpController webJumpController;


    private boolean isResume;
    private boolean isLoading;
    private int width;
    private int height;
    private boolean isNeedRecongnizeer = true;//第一次识别

    private final int MSG_FIRST_GET_INPUT_BOUND = 1;
    private final int MSG_SECOND_GET_INPUT_BOUND = 2;
    private final int MSG_SS = 3;//短暂性的
    private final int MSG_SS_FINISHED = 4;
    private final int MSG_FINDING_SCROLL = 5;
    private final int MSG_FINDED = 6;

    private int currState = MSG_FIRST_GET_INPUT_BOUND;

    private Handler handler;

    private Rect finalInputRect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initHandler();

        webView = findViewById(R.id.webview);
        ivShow = findViewById(R.id.iv_show);

        webJumpController = new WebJumpController(this, webView, new WebJumpController.WebJumpListener() {
            @Override
            public void onFindedTargetPage() {
                restartRecongnizer(MSG_FINDING_SCROLL);
            }

            @Override
            public void onWebViewPageRefreshFinished(String url) {
                if (currState == MSG_FIRST_GET_INPUT_BOUND) {
                    restartRecongnizer(MSG_FIRST_GET_INPUT_BOUND);
                } else if (currState == MSG_SS) {
                    currState = MSG_SS_FINISHED;
                    Message msg = Message.obtain();
                    msg.what = MSG_SS_FINISHED;
                    Bundle bundle = new Bundle();
                    bundle.putString("url", url);
                    msg.setData(bundle);
                    handler.sendMessage(msg);
                } else if (currState == MSG_FINDED) {
                    handler.sendEmptyMessageDelayed(MSG_FINDED,2000);
                }
            }
        });


        textRecognizer = new TextRecognizer.Builder(this).build();

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
                Log.i("lijing", "onImageAvailable: -----------");

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

                    Rect goalRect = null;
                    SparseArray<TextBlock> items = textRecognizer.detect(frame);
                    for (int i = 0; i < items.size(); ++i) {
                        TextBlock item = items.valueAt(i);
                        String value = item.getValue();
                        if (currState == MSG_SECOND_GET_INPUT_BOUND || currState == MSG_FIRST_GET_INPUT_BOUND) {

                            if (YAN.equals(value) || value.contains(YAN)) {
                                Rect boundingBox = item.getBoundingBox();
                                Log.i("lijing", boundingBox.toString());

                                if (currState == MSG_FIRST_GET_INPUT_BOUND) {
                                    Message msg = Message.obtain();
                                    msg.what = MSG_FIRST_GET_INPUT_BOUND;
                                    Bundle bundle = new Bundle();
                                    bundle.putParcelable("rect", boundingBox);
                                    msg.setData(bundle);
                                    handler.sendMessage(msg);
                                } else if (currState == MSG_SECOND_GET_INPUT_BOUND) {
                                    finalInputRect = boundingBox;
                                    handler.sendEmptyMessage(MSG_SECOND_GET_INPUT_BOUND);
                                }

                                break;
                            }
                        } else if (currState == MSG_FINDING_SCROLL) {
                            if (GOAL.equals(value) || value.contains(GOAL)) {
                                goalRect = item.getBoundingBox();
                                break;
                            }
                        }
                    }

                    if (currState == MSG_FINDING_SCROLL) {
                        Message msg = Message.obtain();
                        msg.what = MSG_FINDING_SCROLL;
                        Bundle bundle = new Bundle();
                        bundle.putParcelable("rect", goalRect);
                        msg.setData(bundle);
                        handler.sendMessage(msg);
                    }


                    isNeedRecongnizeer = false;

                }
                if (image != null) {
                    image.close();
                }

            }
        }, null);

        restartRecongnizer(MSG_FIRST_GET_INPUT_BOUND);
    }

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

    private void autoClick(int x, int y) {
        long uptimeMillis = SystemClock.uptimeMillis();
        final long downTime = uptimeMillis;
        MotionEvent motionEvent_down = MotionEvent.obtain(downTime, uptimeMillis, MotionEvent.ACTION_DOWN, x, y, 0);
        dispatchTouchEvent(motionEvent_down);

        MotionEvent motionEvent_up = MotionEvent.obtain(downTime, uptimeMillis, MotionEvent.ACTION_UP, x, y, 0);
        dispatchTouchEvent(motionEvent_up);

    }


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
        new Thread(new Runnable() {
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
        }).start();

    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Log.i("lijing", "event == " + event);
        return super.dispatchKeyEvent(event);
    }

    @SuppressLint("HandlerLeak")
    private void initHandler() {
        handler = new Handler() {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_FIRST_GET_INPUT_BOUND:
                        Bundle data = msg.getData();
                        Rect bound = data.getParcelable("rect");
                        autoClick(bound.centerX(), bound.centerY());

                        postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                restartRecongnizer(MSG_SECOND_GET_INPUT_BOUND);
                            }
                        }, 2000);

                        break;
                    case MSG_SECOND_GET_INPUT_BOUND:
                        autoInputKeyword();

                        break;

                    case MSG_SS:
                        autoClickSSuo();
                        break;

                    case MSG_SS_FINISHED:
                        Bundle urlBundle = msg.getData();
                        String url = urlBundle.getString("url");
                        webJumpController.requestJsoupData(url);
                        break;

                    case MSG_FINDING_SCROLL:

                        Bundle bundle = msg.getData();
                        Rect goalRect = bundle.getParcelable("rect");
                        if (goalRect == null) {
                            autoScroll();
                        } else {
                            autoClick(goalRect.centerX(), goalRect.centerY());
                            currState = MSG_FINDED;
                        }
                        break;

                    case MSG_FINDED:
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                finish();
                                startActivity(new Intent(MainActivity.this, MainActivity.class));
                            }
                        });

                        break;
                }
            }
        };
    }


    private void autoInputKeyword() {

        final String strJS = String.format("javascript:document.getElementById('kw').value='%s';", KW);

        webView.postDelayed(new Runnable() {
            @Override
            public void run() {

                Log.e(MainActivity.class.getSimpleName(), "exe the js");
                webView.evaluateJavascript(strJS, null);
                handler.sendEmptyMessageDelayed(MSG_SS, 500);
            }
        }, 2000);

    }

    private void autoClickSSuo() {
        if (finalInputRect != null) {
            int[] display = Utils.getDisplay(this);

            autoClick(display[0] * 9 / 10, finalInputRect.centerY());
            currState = MSG_SS;
        }
    }

}
