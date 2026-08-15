package com.teacher.pro;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.print.PrintAttributes;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import android.util.Base64;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    private static final int FILE_CHOOSER_REQUEST = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // إنشاء WebView
        webView = new WebView(this);
        setContentView(webView);

        // إعدادات WebView
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setDatabaseEnabled(true);

        // السماح بتحميل ملفات HTML محلية
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // WebViewClient
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(
                    WebView view,
                    String url) {

                super.onPageFinished(view, url);

                injectAndroidFunctions();
            }
        });

        // اختيار الملفات من الهاتف
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {

                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }

                filePathCallback = callback;

                try {

                    Intent intent = params.createIntent();

                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST
                    );

                } catch (ActivityNotFoundException e) {

                    filePathCallback = null;

                    Toast.makeText(
                            MainActivity.this,
                            "لا يمكن فتح مدير الملفات",
                            Toast.LENGTH_LONG
                    ).show();

                    return false;
                }

                return true;
            }
        });

        // JavaScript Bridge
        webView.addJavascriptInterface(
                new AndroidBridge(this),
                "AndroidBridge"
        );

        // تحميل التطبيق
        webView.loadUrl(
                "file:///android_asset/index.html"
        );
    }


    /*
     * ربط وظائف JavaScript مع Android
     */
    private void injectAndroidFunctions() {

        String js =
                "(function() {" +

                // حفظ الملفات
                "window.saveAs = function(blob, filename) {" +

                "  var reader = new FileReader();" +

                "  reader.onloadend = function() {" +

                "    AndroidBridge.saveFile(" +
                "reader.result, filename);" +

                "  };" +

                "  reader.readAsDataURL(blob);" +

                "};" +


                // المشاركة
                "if (!navigator.share) {" +

                "navigator.share = function(data) {" +

                "  if (data && data.files && data.files.length) {" +

                "    var file = data.files[0];" +

                "    return new Promise(function(resolve, reject) {" +

                "      var reader = new FileReader();" +

                "      reader.onloadend = function() {" +

                "        AndroidBridge.shareFile(" +
                "reader.result," +
                "file.name," +
                "file.type);" +

                "        resolve();" +

                "      };" +

                "      reader.onerror = reject;" +

                "      reader.readAsDataURL(file);" +

                "    });" +

                "  }" +

                "  return Promise.reject(" +
                "'لا توجد ملفات للمشاركة'" +
                ");" +

                "};" +

                "}" +

                // canShare
                "navigator.canShare = function() {" +
                "return true;" +
                "};" +

                "})();";

        webView.evaluateJavascript(
                js,
                null
        );
    }


    /*
     * جسر JavaScript مع Android
     */
    public class AndroidBridge {

        private Context context;

        AndroidBridge(Context context) {
            this.context = context;
        }


        /*
         * حفظ الملف
         */
        @JavascriptInterface
        public void saveFile(
                String dataUrl,
                String filename) {

            try {

                // استخراج Base64
                String base64 =
                        dataUrl.substring(
                                dataUrl.indexOf(",") + 1
                        );

                byte[] data =
                        Base64.decode(
                                base64,
                                Base64.DEFAULT
                        );


                /*
                 * مجلد التنزيلات
                 */
                File downloads =
                        Environment
                                .getExternalStoragePublicDirectory(
                                        Environment.DIRECTORY_DOWNLOADS
                                );

                if (!downloads.exists()) {
                    downloads.mkdirs();
                }


                // إنشاء الملف
                File file =
                        new File(
                                downloads,
                                filename
                        );


                // كتابة البيانات
                FileOutputStream output =
                        new FileOutputStream(file);

                output.write(data);
                output.flush();
                output.close();


                runOnUiThread(() -> {

                    Toast.makeText(
                            MainActivity.this,
                            "تم حفظ الملف في مجلد التنزيلات",
                            Toast.LENGTH_LONG
                    ).show();

                });


            } catch (Exception e) {

                runOnUiThread(() -> {

                    Toast.makeText(
                            MainActivity.this,
                            "فشل حفظ الملف",
                            Toast.LENGTH_LONG
                    ).show();

                });
            }
        }


        /*
         * مشاركة الملف
         */
        @JavascriptInterface
        public void shareFile(
                String dataUrl,
                String filename,
                String mimeType) {

            try {

                // استخراج Base64
                String base64 =
                        dataUrl.substring(
                                dataUrl.indexOf(",") + 1
                        );

                byte[] data =
                        Base64.decode(
                                base64,
                                Base64.DEFAULT
                        );


                // مجلد المشاركة المؤقت
                File shareDir =
                        new File(
                                getCacheDir(),
                                "shared_files"
                        );

                if (!shareDir.exists()) {
                    shareDir.mkdirs();
                }


                // إنشاء الملف
                File file =
                        new File(
                                shareDir,
                                filename
                        );


                FileOutputStream output =
                        new FileOutputStream(file);

                output.write(data);
                output.flush();
                output.close();


                /*
                 * إنشاء Uri آمن
                 */
                Uri uri =
                        FileProvider.getUriForFile(
                                MainActivity.this,
                                getPackageName()
                                        + ".fileprovider",
                                file
                        );


                /*
                 * Intent المشاركة
                 */
                Intent shareIntent =
                        new Intent(
                                Intent.ACTION_SEND
                        );


                shareIntent.setType(
                        mimeType != null
                                ? mimeType
                                : "application/pdf"
                );


                shareIntent.putExtra(
                        Intent.EXTRA_STREAM,
                        uri
                );


                shareIntent.addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );


                startActivity(
                        Intent.createChooser(
                                shareIntent,
                                "مشاركة الملف"
                        )
                );


            } catch (Exception e) {

                Toast.makeText(
                        MainActivity.this,
                        "تعذر مشاركة الملف",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }


    /*
     * استقبال الملف المختار من الهاتف
     */
    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );


        if (requestCode ==
                FILE_CHOOSER_REQUEST) {

            if (filePathCallback == null) {
                return;
            }


            Uri[] results = null;


            if (resultCode ==
                    RESULT_OK &&
                    data != null) {


                /*
                 * أكثر من ملف
                 */
                if (data.getClipData() != null) {

                    int count =
                            data.getClipData()
                                    .getItemCount();

                    results =
                            new Uri[count];


                    for (int i = 0;
                         i < count;
                         i++) {

                        results[i] =
                                data.getClipData()
                                        .getItemAt(i)
                                        .getUri();
                    }


                }

                /*
                 * ملف واحد
                 */
                else if (data.getData() != null) {

                    results =
                            new Uri[]{
                                    data.getData()
                            };
                }
            }


            filePathCallback.onReceiveValue(
                    results
            );

            filePathCallback = null;
        }
    }


    /*
     * طباعة صفحة WebView
     *
     * استخدمنا Object + Reflection
     * حتى لا يتوقف التجميع بسبب PrintManager
     */
    public void printPage(String jobName) {

        try {

            /*
             * الحصول على PrintManager
             * بدون الاعتماد المباشر على class
             */
            Class<?> printManagerClass =
                    Class.forName(
                            "android.print.PrintManager"
                    );


            Object printManager =
                    getSystemService(
                            Context.PRINT_SERVICE
                    );


            /*
             * إنشاء PrintDocumentAdapter
             */
            Object adapter =
                    webView.createPrintDocumentAdapter(
                            jobName
                    );


            /*
             * إنشاء PrintAttributes
             */
            PrintAttributes attributes =
                    new PrintAttributes.Builder()
                            .setMediaSize(
                                    PrintAttributes.MediaSize.ISO_A4
                            )
                            .setResolution(
                                    new PrintAttributes.Resolution(
                                            "pdf",
                                            "pdf",
                                            600,
                                            600
                                    )
                            )
                            .setMinMargins(
                                    PrintAttributes.Margins.NO_MARGINS
                            )
                            .build();


            /*
             * استدعاء print عن طريق Reflection
             */
            java.lang.reflect.Method printMethod =
                    printManagerClass.getMethod(
                            "print",
                            String.class,
                            android.print.PrintDocumentAdapter.class,
                            PrintAttributes.class
                    );


            printMethod.invoke(
                    printManager,
                    jobName,
                    adapter,
                    attributes
            );


        } catch (Exception e) {

            Toast.makeText(
                    MainActivity.this,
                    "تعذر فتح الطباعة",
                    Toast.LENGTH_LONG
            ).show();
        }
    }


    /*
     * زر الرجوع
     */
    @Override
    public void onBackPressed() {

        if (webView != null &&
                webView.canGoBack()) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }
}
