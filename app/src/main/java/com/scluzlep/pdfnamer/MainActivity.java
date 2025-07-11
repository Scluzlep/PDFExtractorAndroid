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

    private Button buttonSelectFile, buttonStartFromPath, buttonExportLog;
    private EditText editTextPath;
    private TextView textViewLog;

    // 日志缓冲区 & 节流控制
    private final StringBuilder logBuffer = new StringBuilder(4096);
    private long lastFlush = 0;
    private static final long LOG_FLUSH_INTERVAL_MS = 500;

    // **新增**: 保存最后一次的输出目录
    private File lastOutputDir = null;

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

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> {
                boolean allGranted = true;
                for (Boolean isGranted : permissions.values()) {
                    if (!isGranted) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    Toast.makeText(this, "权限已授予，请再次点击按钮。", Toast.LENGTH_SHORT).show();
                } else {
                    logMessage("错误: 必须授予存储权限才能执行操作。");
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonSelectFile = findViewById(R.id.button_select_file);
        buttonStartFromPath = findViewById(R.id.button_start_from_path);
        buttonExportLog = findViewById(R.id.button_export_log);
        editTextPath = findViewById(R.id.edit_text_path);
        textViewLog = findViewById(R.id.text_view_log);
        textViewLog.setMovementMethod(new ScrollingMovementMethod());

        buttonSelectFile.setOnClickListener(v -> openFilePicker());
        buttonStartFromPath.setOnClickListener(v -> startExtractionFromPath());
        buttonExportLog.setOnClickListener(v -> exportLogs());
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
            String[] permissionsToRequest = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
            boolean hasAllPermissions = true;
            for (String permission : permissionsToRequest) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    hasAllPermissions = false;
                    break;
                }
            }
            if (!hasAllPermissions) {
                logMessage("需要读写存储权限...");
                requestPermissionsLauncher.launch(permissionsToRequest);
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
                this.lastOutputDir = outputDir; // **修改**: 保存输出目录
                logMessage("图片将保存至: " + outputDir.getAbsolutePath());

                try (PDDocument document = PDDocument.load(inputStream)) {
                    Map<Integer, String> pageToTitleMap = new HashMap<>();
                    PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
                    if (outline != null) {
                        for (PDOutlineItem item : outline.children()) {
                            populatePageTitleMap(document, item, pageToTitleMap);
                        }
                    }

                    // ... (图片提取逻辑保持不变)
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

                                try {
                                    Bitmap bitmap = image.getImage();
                                    if (bitmap != null) {
                                        Bitmap.CompressFormat format = "png".equalsIgnoreCase(suffix)
                                                ? Bitmap.CompressFormat.PNG
                                                : Bitmap.CompressFormat.JPEG;
                                        try (OutputStream out = new FileOutputStream(outputFile)) {
                                            bitmap.compress(format, 95, out);
                                        }
                                        bitmap.recycle();
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
            } catch (Exception e) {
                logMessage("\n发生严重错误: " + e.getMessage());
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                logMessage("堆栈跟踪: " + sw.toString().substring(0, Math.min(sw.toString().length(), 500)));
            } finally {
                runOnUiThread(() -> setUiEnabled(true));
                flushLogToUi();
                executor.shutdown();
            }
        });
    }

    private void exportLogs() {
        // **修改**: 检查是否已执行过提取
        if (this.lastOutputDir == null) {
            Toast.makeText(this, "请先执行一次提取，以确定日志保存位置。", Toast.LENGTH_LONG).show();
            return;
        }

        String logContent;
        synchronized (logBuffer) {
            logContent = logBuffer.toString();
        }

        if (logContent.trim().isEmpty()) {
            Toast.makeText(this, "日志为空，无需导出。", Toast.LENGTH_SHORT).show();
            return;
        }

        // **修改**: 直接在图片输出目录创建日志文件
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "log_" + timeStamp + ".txt";
        File logFile = new File(this.lastOutputDir, fileName);

        try (FileOutputStream fos = new FileOutputStream(logFile)) {
            fos.write(logContent.getBytes());
            String successMsg = "日志已保存至: " + logFile.getAbsolutePath();
            Toast.makeText(this, successMsg, Toast.LENGTH_LONG).show();
            logMessage(successMsg);
        } catch (IOException e) {
            String errorMsg = "错误: 导出日志失败 - " + e.getMessage();
            logMessage(errorMsg);
            Toast.makeText(this, "导出日志失败。", Toast.LENGTH_SHORT).show();
        }
    }

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

    private void flushLogToUi() {
        runOnUiThread(() -> {
            textViewLog.setText(logBuffer.toString());
            textViewLog.post(() -> {
                if (textViewLog.getLayout() == null) return;
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
        buttonExportLog.setEnabled(isEnabled);
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