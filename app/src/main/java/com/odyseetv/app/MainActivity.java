package com.odyseetv.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;

public class MainActivity extends Activity {
    private WebView webView;
    private FrameLayout root;
    private PointerView pointer;

    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private boolean centerHeld = false;
    private boolean centerUsedAsModifier = false;

    private float pointerX = 400;
    private float pointerY = 300;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int moveX = 0;
    private int moveY = 0;
    private boolean moving = false;
    private long lastMove = 0;
    private long lastEdgeScroll = 0;

    private static final int STEP = 8;
    private static final int POINTER_RADIUS = 13;

    private final Runnable moveRunnable = new Runnable() {
        @Override
        public void run() {
            if (!moving) return;

            long now = System.currentTimeMillis();
            if (now - lastMove >= 16) {
                pointerX += moveX * STEP;
                pointerY += moveY * STEP;
                clampPointer();
                pointer.invalidate();
                lastMove = now;

                // Scroll when the pointer reaches the top or bottom edge.
                boolean atBottom = pointerY >= root.getHeight() - POINTER_RADIUS - 1;
                boolean atTop = pointerY <= POINTER_RADIUS + 1;

                if (moveY != 0 && (atBottom || atTop)
                        && now - lastEdgeScroll >= 300) {
                    scrollPage(atBottom);
                    lastEdgeScroll = now;
                }
            }
            handler.postDelayed(this, 24);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        root = new FrameLayout(this);

        webView = new WebView(this);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(
                    View view,
                    CustomViewCallback callback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }

                fullscreenView = view;
                fullscreenCallback = callback;

                webView.setVisibility(View.GONE);
                pointer.setVisibility(View.GONE);

                FrameLayout.LayoutParams params =
                    new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    );
                root.addView(fullscreenView, params);
                fullscreenView.setFocusable(true);
                fullscreenView.setFocusableInTouchMode(true);
                fullscreenView.requestFocus();
            }

            @Override
            public void onHideCustomView() {
                hideFullscreen();
            }
        });

        root.addView(webView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        pointer = new PointerView();
        pointer.setFocusable(false);
        pointer.setClickable(false);

        root.addView(pointer, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setContentView(root);

        root.post(() -> {
            pointerX = root.getWidth() / 2f;
            pointerY = root.getHeight() / 2f;
            pointer.invalidate();
        });

        if (savedInstanceState != null) {
            if (webView.restoreState(savedInstanceState) == null) {
                webView.loadUrl("https://odysee.com/");
            }
        } else {
            webView.loadUrl("https://odysee.com/");
        }

        webView.requestFocus(View.FOCUS_DOWN);
    }

    private void hideFullscreen() {
        if (fullscreenView == null) return;

        root.removeView(fullscreenView);
        fullscreenView = null;

        webView.setVisibility(View.VISIBLE);
        pointer.setVisibility(View.VISIBLE);
        pointer.invalidate();

        WebChromeClient.CustomViewCallback callback = fullscreenCallback;
        fullscreenCallback = null;

        if (callback != null) {
            callback.onCustomViewHidden();
        }

        webView.requestFocus();
    }

    private void clampPointer() {
        if (root == null) return;

        pointerX = Math.max(POINTER_RADIUS,
            Math.min(pointerX, root.getWidth() - POINTER_RADIUS));
        pointerY = Math.max(POINTER_RADIUS,
            Math.min(pointerY, root.getHeight() - POINTER_RADIUS));
    }

    private void startMoving(int dx, int dy) {
        moveX = dx;
        moveY = dy;

        if (!moving) {
            moving = true;
            lastMove = 0;
            handler.post(moveRunnable);
        }
    }

    private void stopMoving() {
        moving = false;
        handler.removeCallbacks(moveRunnable);
    }

    private void scrollPage(boolean down) {
        if (webView == null || fullscreenView != null) return;

        int distance = Math.max(200, (int) (webView.getHeight() * 0.70f));
        int direction = down ? 1 : -1;

        String js =
            "(function() {" +
            "var x=" + pointerX + ", y=" + pointerY + ";" +
            "var e=document.elementFromPoint(x,y);" +
            "while(e && e!==document.body && e!==document.documentElement) {" +
            "var st=getComputedStyle(e);" +
            "if(e.scrollHeight>e.clientHeight+10 && " +
            "(st.overflowY==='auto'||st.overflowY==='scroll'||" +
            "e.scrollHeight>e.clientHeight+100)) {" +
            "e.scrollBy({top:" + (distance * direction) + ",behavior:'auto'});" +
            "return 'scrolled element';" +
            "}" +
            "e=e.parentElement;" +
            "}" +
            "var d=document.scrollingElement||document.documentElement;" +
            "d.scrollBy({top:" + (distance * direction) + ",behavior:'auto'});" +
            "return 'scrolled document';" +
            "})()";

        webView.evaluateJavascript(js, null);
    }

    private void clickAtPointer() {
        if (webView == null || fullscreenView != null) return;

        long now = System.currentTimeMillis();

        MotionEvent down = MotionEvent.obtain(
            now, now, MotionEvent.ACTION_DOWN,
            pointerX, pointerY, 0
        );
        down.setSource(android.view.InputDevice.SOURCE_MOUSE);
        webView.dispatchTouchEvent(down);

        MotionEvent up = MotionEvent.obtain(
            now, now + 80, MotionEvent.ACTION_UP,
            pointerX, pointerY, 0
        );
        up.setSource(android.view.InputDevice.SOURCE_MOUSE);
        webView.dispatchTouchEvent(up);

        down.recycle();
        up.recycle();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        int action = event.getAction();

        if (fullscreenView != null) {
            if (code == KeyEvent.KEYCODE_BACK
                    && action == KeyEvent.ACTION_UP) {
                hideFullscreen();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        if (code == KeyEvent.KEYCODE_BACK
                && action == KeyEvent.ACTION_UP) {
            stopMoving();
            navigateBackOrHome();
            return true;
        }

        if (code == KeyEvent.KEYCODE_DPAD_CENTER
                || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_BUTTON_A) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (!centerHeld) {
                    centerHeld = true;
                    centerUsedAsModifier = false;
                }
                return true;
            }

            if (action == KeyEvent.ACTION_UP) {
                boolean shouldClick =
                    centerHeld && !centerUsedAsModifier;
                centerHeld = false;

                if (shouldClick) {
                    clickAtPointer();
                }
                return true;
            }
        }

        if (code == KeyEvent.KEYCODE_PAGE_DOWN
                || code == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            if (action == KeyEvent.ACTION_DOWN
                    && event.getRepeatCount() == 0) {
                scrollPage(true);
            }
            return true;
        }

        if (code == KeyEvent.KEYCODE_PAGE_UP
                || code == KeyEvent.KEYCODE_CHANNEL_UP) {
            if (action == KeyEvent.ACTION_DOWN
                    && event.getRepeatCount() == 0) {
                scrollPage(false);
            }
            return true;
        }

        int dx = 0;
        int dy = 0;

        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                dx = -1;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                dx = 1;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                dy = -1;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                dy = 1;
                break;
        }

        if (dx != 0 || dy != 0) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (centerHeld) {
                    centerUsedAsModifier = true;

                    if (event.getRepeatCount() == 0) {
                        stopMoving();
                        scrollPage(dy > 0);
                    }
                    return true;
                }

                if (event.getRepeatCount() == 0) {
                    startMoving(dx, dy);
                }
                return true;
            }

            if (action == KeyEvent.ACTION_UP) {
                stopMoving();
                return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) {
            webView.saveState(outState);
        }
        super.onSaveInstanceState(outState);
    }

    private void navigateBackOrHome() {
        if (fullscreenView != null) {
            hideFullscreen();
            return;
        }

        if (webView == null) {
            finish();
            return;
        }

        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }

        String url = webView.getUrl();

        if (url != null &&
                !url.equals("https://odysee.com/") &&
                !url.equals("https://odysee.com")) {
            webView.loadUrl("https://odysee.com/");
        } else {
            // Odysee may keep navigation inside its single-page app.
            // Ask its browser history to go back without closing the app.
            webView.evaluateJavascript("history.back()", null);
        }
    }

    @Override
    public void onBackPressed() {
        navigateBackOrHome();
    }

    @Override
    protected void onDestroy() {
        stopMoving();

        if (fullscreenView != null) {
            root.removeView(fullscreenView);
            fullscreenView = null;
        }

        if (webView != null) {
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }

    private class PointerView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PointerView() {
            super(MainActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
            paint.setColor(Color.BLACK);
            canvas.drawCircle(pointerX, pointerY, POINTER_RADIUS, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(
                pointerX, pointerY, POINTER_RADIUS - 3, paint
            );

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setColor(Color.BLACK);
            canvas.drawCircle(
                pointerX, pointerY, POINTER_RADIUS - 3, paint
            );
        }
    }
}
