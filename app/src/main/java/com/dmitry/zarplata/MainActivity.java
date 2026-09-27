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
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.graphics.Color;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int STORAGE_PERMISSION_REQUEST = 1002;
    private static final int VOICE_PERMISSION_REQUEST = 1003;
    private static final int VOICE_INTENT_REQUEST = 1004;
    private static final String PREFS = "driver_salary_native_backup";
    private static final String PREF_STATE = "state_json";
    private static final String BACKUP_FILE = "zarplata_voditelya_auto_backup.json";
    private static final String BACKUP_FOLDER = "ZarplataVoditelya";
    // Вставьте сюда публичный Project API key PostHog (начинается с phc_).
    private static final String ANALYTICS_TOKEN = "";
    private static final String ANALYTICS_URL = "https://eu.i.posthog.com/i/v0/e/";
    private static final String PREF_ANALYTICS_ID = "analytics_distinct_id";

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private SpeechRecognizer speechRecognizer;
    private boolean startVoiceAfterPermission = false;

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

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidData");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && url.startsWith("driverapp://voice")) {
                    launchVoiceIntent();
                    return true;
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request != null ? request.getUrl() : null;
                if (uri != null && "driverapp".equals(uri.getScheme()) && "voice".equals(uri.getHost())) {
                    launchVoiceIntent();
                    return true;
                }
                return false;
            }
        });
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

    public class AndroidBridge {
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

        @JavascriptInterface
        public String saveTextFile(String filename, String mimeType, String content) {
            try {
                writeNamedDownload(filename, mimeType, content);
                return "ok";
            } catch (Exception e) {
                return "error";
            }
        }

        @JavascriptInterface
        public void startVoiceInput() {
            runOnUiThread(() -> launchVoiceIntent());
        }

        @JavascriptInterface
        public boolean isVoiceAvailable() {
            return SpeechRecognizer.isRecognitionAvailable(MainActivity.this) || canLaunchVoiceIntent();
        }

        @JavascriptInterface
        public boolean analyticsConfigured() {
            return ANALYTICS_TOKEN != null && !ANALYTICS_TOKEN.trim().isEmpty();
        }

        @JavascriptInterface
        public String trackEvent(String eventName, String propertiesJson) {
            if (!analyticsConfigured()) return "not-configured";
            if (eventName == null || !eventName.matches("[A-Za-z0-9_.-]{1,80}")) return "invalid-event";
            sendAnalyticsEvent(eventName, propertiesJson);
            return "queued";
        }
    }

    private void requestVoiceInput() {
        launchVoiceIntent();
    }

    private void startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            launchVoiceIntent();
            return;
        }

        if (speechRecognizer != null) {
            try { speechRecognizer.destroy(); } catch (Exception ignored) {}
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { sendVoiceState("listening"); }
            @Override public void onBeginningOfSpeech() { sendVoiceState("speaking"); }
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { sendVoiceState("processing"); }

            @Override
            public void onError(int error) {
                String message;
                switch (error) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                        message = "Не удалось разобрать фразу";
                        break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        message = "Речь не услышана";
                        break;
                    case SpeechRecognizer.ERROR_NETWORK:
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        message = "Ошибка сети при распознавании";
                        break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        launchVoiceIntent();
                        return;
                    case SpeechRecognizer.ERROR_CLIENT:
                    case SpeechRecognizer.ERROR_SERVER:
                        launchVoiceIntent();
                        return;
                    default:
                        message = "Ошибка голосового ввода";
                }
                sendVoiceError(message);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) {
                    sendVoiceResult(list.get(0));
                } else {
                    sendVoiceError("Не удалось разобрать фразу");
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> list = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) sendVoicePartial(list.get(0));
            }

            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Назовите параметры смены");

        sendVoiceState("starting");
        speechRecognizer.startListening(intent);
    }

    private boolean canLaunchVoiceIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        return intent.resolveActivity(getPackageManager()) != null;
    }

    private void launchVoiceIntent() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Назовите параметры смены");
            if (intent.resolveActivity(getPackageManager()) == null) {
                sendVoiceError("На телефоне нет службы распознавания речи");
                return;
            }
            sendVoiceState("listening");
            startActivityForResult(intent, VOICE_INTENT_REQUEST);
        } catch (Exception e) {
            sendVoiceError("Не удалось открыть голосовой ввод");
        }
    }

    private void sendVoiceState(String state) {
        if (webView == null) return;
        webView.post(() -> webView.evaluateJavascript(
                "window.onVoiceState && window.onVoiceState(" + JSONObject.quote(state) + ");", null));
    }

    private void sendVoicePartial(String text) {
        if (webView == null) return;
        webView.post(() -> webView.evaluateJavascript(
                "window.onVoicePartial && window.onVoicePartial(" + JSONObject.quote(text) + ");", null));
    }

    private void sendVoiceResult(String text) {
        if (webView == null) return;
        webView.post(() -> webView.evaluateJavascript(
                "window.onVoiceResult && window.onVoiceResult(" + JSONObject.quote(text) + ");", null));
    }

    private void sendVoiceError(String text) {
        if (webView == null) return;
        webView.post(() -> webView.evaluateJavascript(
                "window.onVoiceError && window.onVoiceError(" + JSONObject.quote(text) + ");", null));
    }

    private String getAnalyticsDistinctId() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String id = prefs.getString(PREF_ANALYTICS_ID, "");
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(PREF_ANALYTICS_ID, id).apply();
        }
        return id;
    }

    private void sendAnalyticsEvent(String eventName, String propertiesJson) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                JSONObject props = new JSONObject();
                if (propertiesJson != null && !propertiesJson.trim().isEmpty()) {
                    JSONObject supplied = new JSONObject(propertiesJson);
                    java.util.Iterator<String> keys = supplied.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        Object value = supplied.opt(key);
                        if (value == null || value == JSONObject.NULL ||
                                value instanceof String || value instanceof Number || value instanceof Boolean) {
                            props.put(key, value);
                        }
                    }
                }

                props.put("app_name", "TRUK WALLET");
                props.put("platform", "android");
                props.put("android_version", Build.VERSION.RELEASE == null ? "" : Build.VERSION.RELEASE);
                try {
                    props.put("app_version", getPackageManager()
                            .getPackageInfo(getPackageName(), 0).versionName);
                } catch (Exception ignored) {}

                JSONObject body = new JSONObject();
                body.put("api_key", ANALYTICS_TOKEN);
                body.put("event", eventName);
                body.put("distinct_id", getAnalyticsDistinctId());
                body.put("properties", props);

                URL url = new URL(ANALYTICS_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload);
                    out.flush();
                }
                int code = connection.getResponseCode();
                if (code >= 200 && code < 300 && connection.getInputStream() != null) {
                    connection.getInputStream().close();
                } else if (connection.getErrorStream() != null) {
                    connection.getErrorStream().close();
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private String safeFilename(String filename) {
        String name = filename == null ? "file.txt" : filename.trim();
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (name.isEmpty()) name = "file.txt";
        return name;
    }

    private void writeNamedDownload(String filename, String mimeType, String content) throws Exception {
        String safeName = safeFilename(filename);
        String safeMime = (mimeType == null || mimeType.trim().isEmpty()) ? "text/plain" : mimeType;
        String text = content == null ? "" : content;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = getContentResolver();
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            Uri target = null;
            String relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + BACKUP_FOLDER + "/";

            String selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND " +
                    MediaStore.Downloads.RELATIVE_PATH + "=?";
            String[] args = new String[]{safeName, relativePath};

            try (Cursor cursor = resolver.query(
                    collection,
                    new String[]{MediaStore.Downloads._ID},
                    selection,
                    args,
                    MediaStore.Downloads.DATE_MODIFIED + " DESC")) {
                if (cursor != null && cursor.moveToFirst()) {
                    target = ContentUris.withAppendedId(collection, cursor.getLong(0));
                }
            }

            if (target == null) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                values.put(MediaStore.Downloads.MIME_TYPE, safeMime);
                values.put(MediaStore.Downloads.RELATIVE_PATH, relativePath);
                target = resolver.insert(collection, values);
            }

            if (target == null) throw new Exception("download uri is null");
            try (OutputStream out = resolver.openOutputStream(target, "wt")) {
                if (out == null) throw new Exception("download stream is null");
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } else {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("storage permission is missing");
            }
            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    BACKUP_FOLDER);
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("cannot create download folder");
            try (FileWriter writer = new FileWriter(new File(dir, safeName), false)) {
                writer.write(text);
            }
        }
    }

    private void writeExternalBackup(String json) throws Exception {
        writeNamedDownload(BACKUP_FILE, "application/json", json);
    }

    private String readExternalBackup() throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = getContentResolver();
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + BACKUP_FOLDER + "/";
            String selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND " +
                    MediaStore.Downloads.RELATIVE_PATH + "=?";
            String[] args = new String[]{BACKUP_FILE, relativePath};

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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == VOICE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (startVoiceAfterPermission) startVoiceRecognition();
            } else {
                sendVoiceError("Разрешите доступ к микрофону для голосового ввода");
            }
            startVoiceAfterPermission = false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            return;
        }
        if (requestCode == VOICE_INTENT_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (results != null && !results.isEmpty()) {
                    sendVoiceResult(results.get(0));
                } else {
                    sendVoiceError("Не удалось разобрать фразу");
                }
            } else {
                sendVoiceError("Голосовой ввод отменён");
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) {
            try { speechRecognizer.destroy(); } catch (Exception ignored) {}
            speechRecognizer = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
