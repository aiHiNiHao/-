package com.example.apple.recognizeer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.apple.recognizeer.ocr.GraphicOverlay;
import com.example.apple.recognizeer.ocr.OcrDetectorProcessor;
import com.example.apple.recognizeer.ocr.ScreenRecordService;
import com.example.apple.recognizeer.ocr.ScreenRecordServiceNew;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ImageView ivShow;
    private GraphicOverlay graphicOverlay;

    private MediaProjectionManager mMediaProjectionManager;
    private TextRecognizer textRecognizer;
    private VirtualDisplay mVirtualDisplay;
    private ImageReader imageReader;

    boolean isResume;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);
        ivShow = findViewById(R.id.iv_show);

        graphicOverlay = (GraphicOverlay) findViewById(R.id.graphicOverlay);

//        webView.loadUrl("https://www.baidu.com/s?word=%E5%BD%95%E5%B1%8F&ts=8072133&t_kt=0&ie=utf-8&fm_kl=021394be2f&rsv_iqid=3259783768&rsv_t=b39atb4WgjYrHvo4SnKzw%252B2gDJ6qtxWMoQ%252FfSnsrWJcjtTlXAlR0HaqYzQ&sa=ib&ms=1&rsv_pq=3259783768&rsv_sug4=10587&tj=1&inputT=2660&ss=100&from=844b&isid=44006&mod=0&async=1");
//        WebSettings settings = webView.getSettings();
//        settings.setJavaScriptEnabled(true);
//        settings.setUseWideViewPort(true);
//        settings.setLoadWithOverviewMode(true);
//        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        textRecognizer = new TextRecognizer.Builder(this).build();
        textRecognizer.setProcessor(new OcrDetectorProcessor(graphicOverlay, null));

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
        final int width = defaultDisplay.getWidth();
        final int height = defaultDisplay.getHeight();
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
                if (!isResume) return;

                Image image = imageReader.acquireNextImage();

                final Image.Plane[] planes = image.getPlanes();
                final ByteBuffer buffer = planes[0].getBuffer();
//                int offset = 0;
//                int pixelStride = planes[0].getPixelStride();
//                int rowStride = planes[0].getRowStride();
//                int rowPadding = rowStride - pixelStride * width;
                // create bitmap
//                Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
//                bitmap.copyPixelsFromBuffer(buffer);
//
//                ivShow.setImageBitmap(bitmap);


                try{
                    buffer.position(0);
                    Frame outputFrame = new Frame.Builder()
                            .setImageData(buffer, width, height, ImageFormat.NV21)
                            .build();

                    textRecognizer.receiveFrame(outputFrame);
                }catch (Exception e){
                    e.printStackTrace();
                }finally {
                    image.close();
                }


            }

        }, null);
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

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
