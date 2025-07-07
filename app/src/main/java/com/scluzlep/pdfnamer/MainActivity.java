package com.scluzlep.pdfnamer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.cos.COSName;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private Button buttonSelectFile, buttonStartFromPath;
    private EditText editTextPath;
    private TextView textViewLog;

    // 使用现代的 ActivityResultLauncher 来处理文件选择
    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        startExtraction(uri, null);
                    }
                }
            });

    // 权限请求 Launcher
    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if(isGranted) {
                    Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();
                    startExtractionFromPath();
                } else {
                    logMessage("错误: 必须授予存储权限才能从路径提取。");
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonSelectFile = findViewById(R.id.button_select_file);
        buttonStartFromPath = findViewById(R.id.button_start_from_path);
        editTextPath = findViewById(R.id.edit_text_path);
        textViewLog = findViewById(R.id.text_view_log);
        textViewLog.setMovementMethod(new ScrollingMovementMethod()); // 让日志区域可以滚动

        // 按钮点击事件
        buttonSelectFile.setOnClickListener(v -> openFilePicker());
        buttonStartFromPath.setOnClickListener(v -> startExtractionFromPath());
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        filePickerLauncher.launch(intent);
    }

    private void startExtractionFromPath() {
        if (checkStoragePermissions()) {
            String path = editTextPath.getText().toString();
            if (path.isEmpty()) {
                logMessage("错误: 请先输入文件路径。");
                return;
            }
            File file = new File(path);
            if (!file.exists()) {
                logMessage("错误: 文件不存在于指定路径: " + path);
                return;
            }
            startExtraction(null, file);
        }
    }

    private boolean checkStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
            if (!Environment.isExternalStorageManager()) {
                logMessage("需要“所有文件访问权限”...");
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent); // 用户将被引导至设置页面
                Toast.makeText(this, "请在此页面授予权限后重试", Toast.LENGTH_LONG).show();
                return false;
            }
            return true;
        } else { // Android 10 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                logMessage("需要存储权限...");
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                return false;
            }
            return true;
        }
    }

    private void startExtraction(Uri uri, File file) {
        clearLogs();
        logMessage("初始化提取任务...");
        setUiEnabled(false);

        // 在后台线程中执行耗时操作
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                InputStream inputStream;
                String displayName;

                if (uri != null) {
                    inputStream = getContentResolver().openInputStream(uri);
                    displayName = getFileNameFromUri(uri);
                } else {
                    inputStream = new java.io.FileInputStream(file);
                    displayName = file.getName();
                }

                logMessage("正在处理文件: " + displayName);
                File outputDir = createOutputDirectory(displayName);
                if (outputDir == null) {
                    logMessage("错误: 无法创建输出目录。");
                    return;
                }
                logMessage("图片将保存至: " + outputDir.getAbsolutePath());

                try (PDDocument document = PDDocument.load(inputStream)) {
                    Map<Integer, String> pageToTitleMap = new HashMap<>();
                    PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
                    if (outline != null) {
                        for (PDOutlineItem item : outline.children()) {
                            populatePageTitleMap(document, item, pageToTitleMap);
                        }
                    }

                    int imageCounter = 1;
                    for (int i = 0; i < document.getNumberOfPages(); i++) {
                        PDPage page = document.getPage(i);
                        String title = pageToTitleMap.getOrDefault(i, "page_" + (i + 1));

                        PDResources resources = page.getResources();
                        for (COSName cosName : resources.getXObjectNames()) {
                            PDXObject xObject = resources.getXObject(cosName);
                            if (xObject instanceof PDImageXObject) {
                                PDImageXObject image = (PDImageXObject) xObject;
                                String sanitizedTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                                String fileName = String.format("%03d-%s.%s", imageCounter++, sanitizedTitle, image.getSuffix());
                                File outputFile = new File(outputDir, fileName);

                                try (OutputStream out = new FileOutputStream(outputFile)) {
                                    image.getImage().createGraphics().drawImage(image.getImage(), 0, 0, null);
                                    javax.imageio.ImageIO.write(image.getImage(), image.getSuffix(), out);
                                }
                                logMessage("已保存: " + outputFile.getName());
                            }
                        }
                    }
                    logMessage("\n处理完成！");
                }
            } catch (IOException e) {
                logMessage("\n发生严重错误: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // 确保UI操作在主线程执行
                runOnUiThread(() -> setUiEnabled(true));
            }
        });
    }

    // --- 日志和UI辅助函数 ---

    private void logMessage(String message) {
        runOnUiThread(() -> {
            String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            textViewLog.append("\n" + timeStamp + " - " + message);
        });
    }

    private void clearLogs() {
        runOnUiThread(() -> textViewLog.setText(""));
    }

    private void setUiEnabled(boolean isEnabled) {
        buttonSelectFile.setEnabled(isEnabled);
        buttonStartFromPath.setEnabled(isEnabled);
        editTextPath.setEnabled(isEnabled);
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int colIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (colIndex != -1) {
                      result = cursor.getString(colIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private File createOutputDirectory(String pdfName) {
        String baseName = pdfName.contains(".") ? pdfName.substring(0, pdfName.lastIndexOf('.')) : pdfName;
        File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File outputDir = new File(picturesDir, "PdfExtractorOutput/" + baseName);
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                return null;
            }
        }
        return outputDir;
    }

    // --- PDFBox 核心逻辑 (与之前版本相同) ---
    private static void populatePageTitleMap(PDDocument doc, PDOutlineItem item, Map<Integer, String> map) {
        try {
            PDPage page = item.findDestinationPage(doc);
            if (page != null) {
                int pageIndex = doc.getPages().indexOf(page);
                if (pageIndex != -1) {
                    map.put(pageIndex, item.getTitle());
                }
            }
        } catch (IOException e) {
            // 在安卓端，我们通过日志输出错误，而不是 System.err
        }
        for (PDOutlineItem child : item.children()) {
            populatePageTitleMap(doc, child, map);
        }
    }
}