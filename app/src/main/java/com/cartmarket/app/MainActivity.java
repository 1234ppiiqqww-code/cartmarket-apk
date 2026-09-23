package com.cartmarket.app;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final String APP_URL = "https://cartmarket-shared.gso8022.chatgpt.site/app.html";
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1002;
    private static final String CHAT_CHANNEL_ID = "cartmarket_chat";

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(true);
        }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int bottomInset = insets.getSystemWindowInsetBottom();
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) webView.getLayoutParams();
            params.bottomMargin = bottomInset + dp(10);
            webView.setLayoutParams(params);
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        createNotificationChannel();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        settings.setTextZoom(100);
        webView.setInitialScale(0);
        webView.addJavascriptInterface(new CartMarketBridge(), "CartMarketAndroid");

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.clearCache(true);
        webView.setWebViewClient(new WebViewClient() {
            private boolean openExternal(String url) {
                if (url != null && url.startsWith("mailto:")) {
                    try { startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse(url))); }
                    catch (Exception e) { Toast.makeText(MainActivity.this, "메일 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show(); }
                    return true;
                }
                return false;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return openExternal(request.getUrl().toString()) || super.shouldOverrideUrlLoading(view, request);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return openExternal(url) || super.shouldOverrideUrlLoading(view, url);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState == null) webView.loadUrl(APP_URL);
        else webView.restoreState(savedInstanceState);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHAT_CHANNEL_ID, "채팅 알림", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("카트마켓 새 채팅 알림");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void showChatNotification(String title, String message) {
        runOnUiThread(() -> {
            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new Notification.Builder(this, CHAT_CHANNEL_ID) : new Notification.Builder(this);
            builder.setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle(title).setContentText(message).setAutoCancel(true).setPriority(Notification.PRIORITY_HIGH);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify((int) (System.currentTimeMillis() & 0x7fffffff), builder.build());
        });
    }

    private class CartMarketBridge {
        @JavascriptInterface public void notifyChat(String title, String message) { showChatNotification(title, message); }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }
}
