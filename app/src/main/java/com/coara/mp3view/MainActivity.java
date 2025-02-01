package com.coara.mp3view;

import android.Manifest;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Button;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int STORAGE_PERMISSION_CODE = 1;
    private String mp3FilePath;

    // ActivityResultLauncherを定義
    private final ActivityResultLauncher<Intent> resultLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri selectedFileUri = result.getData().getData();
                mp3FilePath = getFilePath(selectedFileUri);
                Toast.makeText(this, "File selected: " + mp3FilePath, Toast.LENGTH_SHORT).show();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 権限確認
        checkPermissions();

        // WebViewの設定
        WebView webView = findViewById(R.id.webView);
        webView.loadUrl("file:///android_asset/player.html");

        // WebViewにカスタムWebChromeClientを設定
        webView.setWebChromeClient(new WebChromeClient());

        // MP3ファイル選択ボタン
        Button pickFileButton = findViewById(R.id.pickFileButton);
        pickFileButton.setOnClickListener(v -> openFilePicker());
    }

    // ストレージ権限の確認
    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
        }
    }

    // 権限リクエストの結果処理
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ファイルピッカーを開く
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("audio/mp3");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        resultLauncher.launch(intent);
    }

    // URIからファイルパスを取得
    private String getFilePath(Uri uri) {
        String path = "";
        if (uri != null) {
            if (uri.getScheme().equals("content")) {
                try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        path = cursor.getString(index);
                    }
                }
            }
        }
        return path;
    }

    // バックボタン無効化
    @Override
    public void onBackPressed() {
        WebView webView = findViewById(R.id.webView);
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            // onBackPressedDispatcherを使う
            getOnBackPressedDispatcher().onBackPressed();
        }
    }
}
