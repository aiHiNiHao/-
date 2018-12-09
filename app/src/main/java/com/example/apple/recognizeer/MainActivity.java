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
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ImageView;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.nio.ByteBuffer;

public class MainActivity extends BaseActivity {

    public static final String YAN = "kdsfgjkgdsklgkdlg";
    public static final String YAN_UNSAFE = "lijingggggg";
    public static final String KW = "活性炭";
    public static final String GOAL = "www.lyll.com.cn";
    public static final String GOAL1 = "www.lyl.com.cn";
    public static final String GOAL2 = "www.yll.com.cn";

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

    private final int MSG_INIT = 1;
    private final int MSG_FIRST_GET_INPUT_BOUND = 2;
    private final int MSG_SECOND_GET_INPUT_BOUND = 3;
    private final int MSG_SS = 4;//短暂性的
    private final int MSG_SS_FINISHED = 5;
    private final int MSG_FINDING_SCROLL = 6;
    private final int MSG_FINDED = 7;

    private int currState = MSG_INIT;

    private Handler handler;

    private Rect finalInputRect;

    private TestNecrosis testNecrosisThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        testNecrosisThread = new TestNecrosis();
        testNecrosisThread.start();

        initHandler();

        webView = findViewById(R.id.webview);
        ivShow = findViewById(R.id.iv_show);

        webJumpController = new WebJumpController(this, webView, new WebJumpController.WebJumpListener() {
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
            }

            @Override
            public void onWebViewPageRefreshFinished(String url) {

                if (currState == MSG_INIT) {
                    handler.sendEmptyMessageDelayed(MSG_INIT, 500);
                } else if (currState == MSG_SS) {
                    currState = MSG_SS_FINISHED;
                    Message msg = Message.obtain();
                    msg.what = MSG_SS_FINISHED;
                    Bundle bundle = new Bundle();
                    bundle.putString("url", url);
                    msg.setData(bundle);
                    handler.sendMessage(msg);
                } else if (currState == MSG_FINDED) {
                    handler.sendEmptyMessageDelayed(MSG_FINDED, 2000);
                }

                if (testNecrosisThread!=null){
                    testNecrosisThread.makeZero();
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

                    Rect inputRect = null;
                    Rect goalRect = null;
                    Rect unsafeRect = null;
                    SparseArray<TextBlock> items = textRecognizer.detect(frame);
                    for (int i = 0; i < items.size(); ++i) {
                        TextBlock item = items.valueAt(i);
                        String value = item.getValue();
                        if (currState == MSG_SECOND_GET_INPUT_BOUND || currState == MSG_FIRST_GET_INPUT_BOUND) {

                            if (YAN.equals(value) || value.contains(YAN)) {
                                inputRect = item.getBoundingBox();
                                Log.i("lijing", inputRect.toString());

                                if (currState == MSG_FIRST_GET_INPUT_BOUND) {
                                    Message msg = Message.obtain();
                                    msg.what = MSG_FIRST_GET_INPUT_BOUND;
                                    Bundle bundle = new Bundle();
                                    bundle.putParcelable("rect", inputRect);
                                    msg.setData(bundle);
                                    handler.sendMessage(msg);
                                } else if (currState == MSG_SECOND_GET_INPUT_BOUND) {
                                    finalInputRect = inputRect;
                                    handler.sendEmptyMessage(MSG_SECOND_GET_INPUT_BOUND);
                                }

                                break;
                            }
                        } else if (currState == MSG_FINDING_SCROLL) {
                            value = value.toLowerCase();
                            if (GOAL.equals(value) || value.contains(GOAL)
                                    || GOAL1.equals(value) || value.contains(GOAL1)
                                    || GOAL2.equals(value) || value.contains(GOAL2)) {
                                goalRect = item.getBoundingBox();
                                break;
                            } else if (YAN_UNSAFE.equals(value) || value.contains(YAN_UNSAFE)) {
                                unsafeRect = item.getBoundingBox();
                                autoClick(unsafeRect.centerX(), unsafeRect.centerY());
                                break;
                            }
                        }
                    }

                    if (currState == MSG_SECOND_GET_INPUT_BOUND || currState == MSG_FIRST_GET_INPUT_BOUND) {
                        if (inputRect == null){
                            restart();
                        }
                    } else if (currState == MSG_FINDING_SCROLL) {
                        Message msg = Message.obtain();
                        msg.what = MSG_FINDING_SCROLL;
                        Bundle bundle = new Bundle();
                        bundle.putParcelable("goalRect", goalRect);
                        bundle.putParcelable("unsafeRect", unsafeRect);
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
                    case MSG_INIT:
                        restartRecongnizer(MSG_FIRST_GET_INPUT_BOUND);
                        break;
                    case MSG_FIRST_GET_INPUT_BOUND:
                        Bundle data = msg.getData();
                        Rect rect = data.getParcelable("rect");

                        autoClick(rect.centerX(), rect.centerY());

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
                        Rect goalRect = bundle.getParcelable("goalRect");
                        Rect unsafeRect = bundle.getParcelable("unsafeRect");
                        if (unsafeRect != null) {
                            restartRecongnizer(MSG_FINDING_SCROLL);
                        } else if (goalRect == null) {
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
                                restart();
                            }
                        });

                        break;
                }
            }
        };
    }

    private void restart() {

        nip();
        startActivity(new Intent(this, EmptyActivity.class));
    }

    public void nip(){
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        handler.removeCallbacksAndMessages(null);
        RecognizerApp.getInstance().deleteCookie();
        if (testNecrosisThread != null) {
            testNecrosisThread.finish();
            testNecrosisThread.interrupt();
            testNecrosisThread = null;
        }

        finish();
    }


    private void autoInputKeyword() {

        final String strJS = String.format("javascript:document.getElementById('kw').value='%s';", KW);

        webView.evaluateJavascript(strJS, null);
        handler.sendEmptyMessageDelayed(MSG_SS, 500);

    }

    private void autoReplaceUnsafeElementText() {

        String strJS = "javascript:document.getElementsByClassName('c-blocka c-color-gray-a hint-unsafe-expand  hint-unsafe-expand1')[0].firstElementChild.innerText='" + YAN_UNSAFE + "'";
        webView.evaluateJavascript(strJS, null);

    }

    private void autoClickSSuo() {
        if (finalInputRect != null) {
            int[] display = Utils.getDisplay(this);

            autoClick(display[0] * 9 / 10, finalInputRect.centerY());
            currState = MSG_SS;
        }
    }


    class TestNecrosis extends Thread {
        private long time_test_necrosis;
        private boolean execute = true;

        public TestNecrosis() {
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
                Log.e("lijing", "code == "+ MainActivity.this.hashCode());
                long systemTime = System.currentTimeMillis();
                if (systemTime - time_test_necrosis > 20000) {
                    restart();
                    return;
                }
                SystemClock.sleep(300);
            }
        }
    }

}
