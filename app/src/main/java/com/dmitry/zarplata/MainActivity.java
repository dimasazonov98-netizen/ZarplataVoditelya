package com.dmitry.zarplata;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Color;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int STORAGE_PERMISSION_REQUEST = 1002;
    private static final String PREFS = "driver_salary_native_backup";
    private static final String PREF_STATE = "state_json";
    private static final String BACKUP_FILE = "zarplata_voditelya_auto_backup.json";
    private static final String BACKUP_FOLDER = "ZarplataVoditelya";

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(7,17,31));
        getWindow().setNavigationBarColor(Color.rgb(5,11,20));

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST);
        }

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);

        webView.addJavascriptInterface(new BackupBridge(), "AndroidData");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallbackNew,
                    FileChooserParams fileChooserParams) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = filePathCallbackNew;
                try {
                    Intent intent = fileChooserParams.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    public class BackupBridge {
        @JavascriptInterface
        public String getState() {
            return getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_STATE, "");
        }

        @JavascriptInterface
        public String getExternalBackup() {
            try {
                return readExternalBackup();
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public String saveState(String json) {
            if (json == null || json.trim().isEmpty()) return "empty";
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_STATE, json).apply();
            try {
                writeExternalBackup(json);
                return "ok";
            } catch (Exception e) {
                return "local-only";
            }
        }

        @JavascriptInterface
        public String getBackupLocation() {
            return "Загрузки/" + BACKUP_FOLDER + "/" + BACKUP_FILE;
        }
    }

    private void writeExternalBackup(String json) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = getContentResolver();
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            Uri target = null;

            String selection = MediaStore.Downloads.DISPLAY_NAME + "=?";
            String[] args = new String[]{BACKUP_FILE};
            try (Cursor c = resolver.query(
                    collection,
                    new String[]{MediaStore.Downloads._ID},
                    selection,
                    args,
                    MediaStore.Downloads.DATE_MODIFIED + " DESC")) {
                if (c != null && c.moveToFirst()) {
                    target = ContentUris.withAppendedId(collection, c.getLong(0));
                }
            }

            if (target == null) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, BACKUP_FILE);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                values.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + BACKUP_FOLDER);
                target = resolver.insert(collection, values);
            }

            if (target == null) throw new Exception("backup uri is null");
            try (OutputStream out = resolver.openOutputStream(target, "wt")) {
                if (out == null) throw new Exception("backup stream is null");
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } else {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("storage permission is missing");
            }
            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    BACKUP_FOLDER);
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("cannot create backup folder");
            try (FileWriter writer = new FileWriter(new File(dir, BACKUP_FILE), false)) {
                writer.write(json);
            }
        }
    }

    private String readExternalBackup() throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = getContentResolver();
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String selection = MediaStore.Downloads.DISPLAY_NAME + "=?";
            String[] args = new String[]{BACKUP_FILE};

            try (Cursor c = resolver.query(
                    collection,
                    new String[]{MediaStore.Downloads._ID},
                    selection,
                    args,
                    MediaStore.Downloads.DATE_MODIFIED + " DESC")) {
                if (c != null && c.moveToFirst()) {
                    Uri uri = ContentUris.withAppendedId(collection, c.getLong(0));
                    try (InputStream in = resolver.openInputStream(uri)) {
                        if (in != null) return readStream(in);
                    }
                }
            }
            return "";
        } else {
            File file = new File(
                    new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), BACKUP_FOLDER),
                    BACKUP_FILE);
            if (!file.exists()) return "";
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        }
    }

    private String readStream(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
