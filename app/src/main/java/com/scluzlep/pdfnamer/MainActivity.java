package com.scluzlep.pdfnamer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.graphics.PDXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
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

    // 日志缓冲区 & 节流控制
    private final StringBuilder logBuffer = new StringBuilder(4096);
    private long lastFlush = 0;               // 上次刷新到 UI 的时间戳
    private static final long LOG_FLUSH_INTERVAL_MS = 500; // 500ms 刷一次

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

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "权限已授予，请再次点击按钮开始提取。", Toast.LENGTH_SHORT).show();
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
        textViewLog.setMovementMethod(new ScrollingMovementMethod());

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
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                    Toast.makeText(this, "请在此页面授予权限后重试", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    logMessage("无法打开权限设置页面，请手动授予权限。");
                }
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

                if (inputStream == null) {
                    logMessage("错误：无法打开文件输入流。");
                    runOnUiThread(() -> setUiEnabled(true));
                    return;
                }

                logMessage("正在处理文件: " + displayName);
                File outputDir = createOutputDirectory(displayName);
                if (outputDir == null) {
                    runOnUiThread(() -> setUiEnabled(true));
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
                                String suffix = image.getSuffix() != null ? image.getSuffix() : "jpg";
                                String fileName = String.format(Locale.US, "%03d-%s.%s", imageCounter++, sanitizedTitle, suffix);
                                File outputFile = new File(outputDir, fileName);

                                // 保持 PNG/JPEG 输出，解码后立即释放 Bitmap
                                try {
                                    Bitmap bitmap = image.getImage();
                                    if (bitmap != null) {
                                        Bitmap.CompressFormat format = Bitmap.CompressFormat.JPEG;
                                        if ("png".equalsIgnoreCase(suffix)) {
                                            format = Bitmap.CompressFormat.PNG;
                                        }
                                        try (OutputStream out = new FileOutputStream(outputFile)) {
                                            bitmap.compress(format, 95, out);
                                        }
                                        // ★ 立刻回收，降低 native-heap 峰值
                                        bitmap.recycle();
                                        bitmap = null;
                                        logMessage("已保存: " + outputFile.getName());
                                    } else {
                                        logMessage("警告: 无法解码图片 " + cosName.getName());
                                    }
                                } catch (IOException e) {
                                    logMessage("错误: 保存图片时发生 IO 异常 " + outputFile.getName() + " - " + e.getMessage());
                                }
                            }
                        }
                    }
                    logMessage("\n处理完成！");
                }
            } catch (Exception e) { // 捕获所有可能的异常
                logMessage("\n发生严重错误: " + e.getMessage());
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                logMessage("堆栈跟踪: " + sw.toString().substring(0, Math.min(sw.toString().length(), 500))); // 打印部分堆栈信息
            } finally {
                runOnUiThread(() -> setUiEnabled(true));
                flushLogToUi();           // 把剩余日志推到界面
                executor.shutdown();      // 关闭线程池
            }
        });
    }

    /**
     * 日志缓冲并节流输出到 UI
     */
    private void logMessage(String message) {
        long now = System.currentTimeMillis();
        String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(now));

        synchronized (logBuffer) {
            logBuffer.append(timeStamp).append(" - ").append(message).append('\n');
            if (now - lastFlush >= LOG_FLUSH_INTERVAL_MS) {
                flushLogToUi();
            }
        }
    }

    /** 立即将缓冲日志刷新到 TextView */
    private void flushLogToUi() {
        runOnUiThread(() -> {
            textViewLog.setText(logBuffer.toString());
            // 滚动到底部，方便查看最新日志
            textViewLog.post(() -> {
                int scrollAmount = textViewLog.getLayout().getLineTop(textViewLog.getLineCount()) - textViewLog.getHeight();
                if (scrollAmount > 0)
                    textViewLog.scrollTo(0, scrollAmount);
                else
                    textViewLog.scrollTo(0, 0);
            });
        });
        lastFlush = System.currentTimeMillis();
    }

    private void clearLogs() {
        synchronized (logBuffer) {
            logBuffer.setLength(0);
        }
        runOnUiThread(() -> textViewLog.setText(""));
    }

    private void setUiEnabled(boolean isEnabled) {
        buttonSelectFile.setEnabled(isEnabled);
        buttonStartFromPath.setEnabled(isEnabled);
        editTextPath.setEnabled(isEnabled);
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int colIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (colIndex != -1) {
                        result = cursor.getString(colIndex);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) result = result.substring(cut + 1);
            }
        }
        return result != null ? result : "unknown_file.pdf";
    }

    private File createOutputDirectory(String pdfName) {
        String baseName = pdfName.contains(".") ? pdfName.substring(0, pdfName.lastIndexOf('.')) : pdfName;
        File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File outputDir = new File(picturesDir, "PdfExtractorOutput/" + baseName);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            logMessage("严重警告: 无法创建目录 " + outputDir.getAbsolutePath());
            return null;
        }
        return outputDir;
    }

    private void populatePageTitleMap(PDDocument doc, PDOutlineItem item, Map<Integer, String> map) {
        try {
            PDPage page = item.findDestinationPage(doc);
            if (page != null) {
                int pageIndex = doc.getPages().indexOf(page);
                if (pageIndex != -1) {
                    map.put(pageIndex, item.getTitle());
                }
            }
        } catch (IOException e) {
            logMessage("无法为书签 '" + item.getTitle() + "' 查找页面: " + e.getMessage());
        }
        for (PDOutlineItem child : item.children()) {
            populatePageTitleMap(doc, child, map);
        }
    }
}
