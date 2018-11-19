package com.example.apple.recognizeer;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.SystemClock;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
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

import com.example.apple.recognizeer.ocr.CameraSource;
import com.example.apple.recognizeer.ocr.GraphicOverlay;
import com.example.apple.recognizeer.ocr.OcrDetectorProcessor;
import com.example.apple.recognizeer.ocr.ScreenRecordService;
import com.example.apple.recognizeer.ocr.ScreenRecordServiceNew;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "MainActivity";
    private WebView webView;
    private ImageView ivShow;
    private GraphicOverlay graphicOverlay;

    private MediaProjectionManager mMediaProjectionManager;
    private TextRecognizer textRecognizer;
    private VirtualDisplay mVirtualDisplay;
    private ImageReader imageReader;

    private Thread mProcessingThread;
    private FrameProcessingRunnable mFrameProcessor;
//    private Map<byte[], ByteBuffer> mBytesToByteBuffer = new HashMap<>();

    private boolean isResume;
    private int width;
    private int height;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);
        ivShow = findViewById(R.id.iv_show);

        graphicOverlay = (GraphicOverlay) findViewById(R.id.graphicOverlay);

        webView.loadUrl("https://www.baidu.com/s?wd=TextRecognizer%20%E4%B8%AD%E6%96%87&rsv_spt=1&rsv_iqid=0xcad453060000582a&issp=1&f=8&rsv_bp=1&rsv_idx=2&ie=utf-8&rqlang=cn&tn=baiduhome_pg&rsv_enter=1&oq=google%25E8%25AF%2586%25E5%2588%25AB%25E6%2596%2587%25E5%25AD%2597&rsv_t=f288PqT5fP0IA6PlXMQ5W0NrrA1jilAzBpnIvnZLOGfSBeVGepXoCLysXOUaOhLLSePI&inputT=7629&rsv_pq=a45fe8450000cc93&rsv_sug3=86&rsv_sug1=55&rsv_sug7=000&rsv_sug2=0&rsv_sug4=7629&rsv_sug=1");
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        textRecognizer = new TextRecognizer.Builder(this).build();
        textRecognizer.setProcessor(new OcrDetectorProcessor(graphicOverlay, null));

        mMediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent screenCaptureIntent = mMediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(screenCaptureIntent, 110);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        mFrameProcessor = new FrameProcessingRunnable(textRecognizer);
        mProcessingThread = new Thread(mFrameProcessor);
        mFrameProcessor.setActive(true);
        mProcessingThread.start();


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
                if (!isResume) return;
                Image image = imageReader.acquireLatestImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();

                int pixelStride = image.getPlanes()[0].getPixelStride();
                int rowStride = image.getPlanes()[0].getRowStride();
                int rowPadding = rowStride - pixelStride * width;

                Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(buffer);

                Frame frame = new Frame.Builder()
                        .setBitmap(bitmap)
                        .build();

                SparseArray<TextBlock> items = textRecognizer.detect(frame);
                for (int i = 0; i < items.size(); ++i) {
                    TextBlock item = items.valueAt(i);
                    Log.d("lijing", "value == " + item.getValue());
                }
                image.close();

            }

        }, null);
    }

    Bitmap RGBtoNV21(Image image, byte[] yuv420sp, int width, int height) {
        try {
            final int frameSize = width * height;

            int yIndex = 0;
            int uvIndex = frameSize;
            int pixelStride = image.getPlanes()[0].getPixelStride();
            int rowStride = image.getPlanes()[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();

            Bitmap bitmap = Bitmap.createBitmap(getResources().getDisplayMetrics(), width, height, Bitmap.Config.ARGB_8888);

            int A, R, G, B, Y, U, V;
            int offset = 0;

            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {

                    // Useful link: http://stackoverflow.com/questions/26673127/android-imagereader-acquirelatestimage-returns-invalid-jpg

                    R = (buffer.get(offset) & 0xff) << 16;     // R
                    G = (buffer.get(offset + 1) & 0xff) << 8;  // G
                    B = (buffer.get(offset + 2) & 0xff);       // B
                    A = (buffer.get(offset + 3) & 0xff) << 24; // A
                    offset += pixelStride;

                    int pixel = 0;
                    pixel |= R;     // R
                    pixel |= G;  // G
                    pixel |= B;       // B
                    pixel |= A; // A
                    bitmap.setPixel(j, i, pixel);

                    // RGB to YUV conversion according to
                    // https://en.wikipedia.org/wiki/YUV#Y.E2.80.B2UV444_to_RGB888_conversion
                    Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                    U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                    V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

//                    Y = (int) Math.round(R * .299000 + G * .587000 + B * .114000);
//                    U = (int) Math.round(R * -.168736 + G * -.331264 + B * .500000 + 128);
//                    V = (int) Math.round(R * .500000 + G * -.418688 + B * -.081312 + 128);

                    // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor
                    // of 2 meaning for every 4 Y pixels there are 1 V and 1 U.
                    // Note the sampling is every other pixel AND every other scanline.
                    yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                    if (i % 2 == 0 && j % 2 == 0) {
                        yuv420sp[uvIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                        yuv420sp[uvIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                    }
                }
                offset += rowPadding;
            }


            return bitmap;

        } catch (Exception e) {
            return null;
        }

    }

    public Bitmap nv21ToBitmap(byte[] nv21, int width, int height) {

        RenderScript rs;
        ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;
        Type.Builder yuvType, rgbaType;
        Allocation in, out;

        rs = RenderScript.create(this);
        yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));

        yuvType = new Type.Builder(rs, Element.U8(rs)).setX(nv21.length);
        in = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);

        rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs)).setX(width).setY(height);
        out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT);

        in.copyFrom(nv21);

        yuvToRgbIntrinsic.setInput(in);
        yuvToRgbIntrinsic.forEach(out);

        Bitmap bmpout = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        out.copyTo(bmpout);

        return bmpout;

    }

    @TargetApi(19)
    public byte[] yuvImageToByteArray(Image image) {

        assert (image.getFormat() == ImageFormat.YUV_420_888);

        int width = image.getWidth();
        int height = image.getHeight();

        Image.Plane[] planes = image.getPlanes();
        byte[] result = new byte[width * height * 3 / 2];

        int stride = planes[0].getRowStride();
        if (stride == width) {
            planes[0].getBuffer().get(result, 0, width);
        } else {
            for (int row = 0; row < height; row++) {
                planes[0].getBuffer().position(row * stride);
                planes[0].getBuffer().get(result, row * width, width);
            }
        }

        stride = planes[1].getRowStride();
        assert (stride == planes[2].getRowStride());
        byte[] rowBytesCb = new byte[stride];
        byte[] rowBytesCr = new byte[stride];

        for (int row = 0; row < height / 2; row++) {
            int rowOffset = width * height + width / 2 * row;
            planes[1].getBuffer().position(row * stride);
            planes[1].getBuffer().get(rowBytesCb, 0, width / 2);
            planes[2].getBuffer().position(row * stride);
            planes[2].getBuffer().get(rowBytesCr, 0, width / 2);

            for (int col = 0; col < width / 2; col++) {
                result[rowOffset + col * 2] = rowBytesCr[col];
                result[rowOffset + col * 2 + 1] = rowBytesCb[col];
            }
        }
        return result;
    }

    void RGBtoNV21(byte[] yuv420sp, byte[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uvIndex = frameSize;

        int A, R, G, B, Y, U, V;
        int index = 0;
        int rgbIndex = 0;

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {

                R = argb[rgbIndex++];
                G = argb[rgbIndex++];
                B = argb[rgbIndex++];
                A = argb[rgbIndex++]; // Ignored right now.

                // RGB to YUV conversion according to
                // https://en.wikipedia.org/wiki/YUV#Y.E2.80.B2UV444_to_RGB888_conversion
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor
                // of 2 meaning for every 4 Y pixels there are 1 V and 1 U.
                // Note the sampling is every other pixel AND every other scanline.
                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (i % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[uvIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                }
                index++;
            }
        }
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

    public void release() {
        stop();
        mFrameProcessor.release();
    }

    public void start() {
        mProcessingThread = new Thread(mFrameProcessor);
        mFrameProcessor.setActive(true);
        mProcessingThread.start();
    }

    public void stop() {
        mFrameProcessor.setActive(false);
        if (mProcessingThread != null) {
            try {
                // Wait for the thread to complete to ensure that we can't have multiple threads
                // executing at the same time (i.e., which would happen if we called start too
                // quickly after stop).
                mProcessingThread.join();
            } catch (InterruptedException e) {
                Log.d(TAG, "Frame processing thread interrupted on release.");
            }
            mProcessingThread = null;
        }

        // clear the buffer to prevent oom exceptions
//        mBytesToByteBuffer.clear();

    }

    private class FrameProcessingRunnable implements Runnable {
        private Detector<?> mDetector;
        private long mStartTimeMillis = SystemClock.elapsedRealtime();

        // This lock guards all of the member variables below.
        private final Object mLock = new Object();
        private boolean mActive = true;

        // These pending variables hold the state associated with the new frame awaiting processing.
        private long mPendingTimeMillis;
        private int mPendingFrameId = 0;
        private ByteBuffer mPendingFrameData;

        FrameProcessingRunnable(Detector<?> detector) {
            mDetector = detector;
        }

        /**
         * Releases the underlying receiver.  This is only safe to do after the associated thread
         * has completed, which is managed in camera source's release method above.
         */
        @SuppressLint("Assert")
        void release() {
            assert (mProcessingThread.getState() == Thread.State.TERMINATED);
            mDetector.release();
            mDetector = null;
        }

        /**
         * Marks the runnable as active/not active.  Signals any blocked threads to continue.
         */
        void setActive(boolean active) {
            synchronized (mLock) {
                mActive = active;
                mLock.notifyAll();
            }
        }

        /**
         * Sets the frame data received from the camera.  This adds the previous unused frame buffer
         * (if present) back to the camera, and keeps a pending reference to the frame data for
         * future use.
         */
        void setNextFrame(ByteBuffer data) {
            synchronized (mLock) {
                if (mPendingFrameData != null) {
                    mPendingFrameData = null;
                }

//                if (!mBytesToByteBuffer.containsKey(data)) {
//                    Log.d(TAG,
//                            "Skipping frame.  Could not find ByteBuffer associated with the image " +
//                                    "data from the camera.");
//                    return;
//                }

                // Timestamp and frame ID are maintained here, which will give downstream code some
                // idea of the timing of frames received and when frames were dropped along the way.
                mPendingTimeMillis = SystemClock.elapsedRealtime() - mStartTimeMillis;
                mPendingFrameId++;
                mPendingFrameData = data;

                // Notify the processor thread if it is waiting on the next frame (see below).
                mLock.notifyAll();
            }
        }

        /**
         * As long as the processing thread is active, this executes detection on frames
         * continuously.  The next pending frame is either immediately available or hasn't been
         * received yet.  Once it is available, we transfer the frame info to local variables and
         * run detection on that frame.  It immediately loops back for the next frame without
         * pausing.
         * <p/>
         * If detection takes longer than the time in between new frames from the camera, this will
         * mean that this loop will run without ever waiting on a frame, avoiding any context
         * switching or frame acquisition time latency.
         * <p/>
         * If you find that this is using more CPU than you'd like, you should probably decrease the
         * FPS setting above to allow for some idle time in between frames.
         */
        @Override
        public void run() {
            Frame outputFrame;
            ByteBuffer data;

            while (true) {
                Log.i("lijing", "run");
                synchronized (mLock) {
                    while (mActive && (mPendingFrameData == null)) {
                        try {
                            // Wait for the next frame to be received from the camera, since we
                            // don't have it yet.
                            mLock.wait();
                        } catch (InterruptedException e) {
                            Log.d(TAG, "Frame processing loop terminated.", e);
                            return;
                        }
                    }

                    if (!mActive) {
                        // Exit the loop once this camera source is stopped or released.  We check
                        // this here, immediately after the wait() above, to handle the case where
                        // setActive(false) had been called, triggering the termination of this
                        // loop.
                        return;
                    }

                    outputFrame = new Frame.Builder()
                            .setImageData(mPendingFrameData, width,
                                    height, ImageFormat.NV21)
                            .setId(mPendingFrameId)
                            .setTimestampMillis(mPendingTimeMillis)
                            .setRotation(0)
                            .build();

                    // Hold onto the frame data locally, so that we can use this for detection
                    // below.  We need to clear mPendingFrameData to ensure that this buffer isn't
                    // recycled back to the camera before we are done using that data.
//                    data = mPendingFrameData;
//                    mPendingFrameData = null;
                }

                // The code below needs to run outside of synchronization, because this will allow
                // the camera to add pending frame(s) while we are running detection on the current
                // frame.

                try {
                    mDetector.receiveFrame(outputFrame);
                } catch (Throwable t) {
                    Log.e(TAG, "Exception thrown from receiver.", t);
                } finally {
                }
            }
        }
    }
}
