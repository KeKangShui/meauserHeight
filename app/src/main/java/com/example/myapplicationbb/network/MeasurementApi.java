package com.example.myapplicationbb.network;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import com.google.gson.Gson;
import okhttp3.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class MeasurementApi {
    private static final String BASE_URL = "http://192.168.3.248:8000/measure_height/";
    private final OkHttpClient client;
    private final Gson gson;

    public MeasurementApi() {
        client = new OkHttpClient();
        gson = new Gson();
    }

    public void submitMeasurement(
            Context context,
            Uri imageUri,
            List<float[]> points,
            float[] gravity,
            float knownDistance,
            Callback callback
    ) {
        if (!isNetworkAvailable(context)) {
            callback.onFailure(null, new IOException("网络连接不可用，请检查网络设置"));
            return;
        }

        try {
            // 验证参数
            if (imageUri == null) {
                callback.onFailure(null, new IOException("图片URI为空"));
                return;
            }

            if (points == null || points.size() != 2) {
                callback.onFailure(null, new IOException("选择的点数据无效，需要两个点"));
                return;
            }

            // 使用ContentResolver处理Uri，适用于从相册选择的图片
            InputStream inputStream = null;
            try {
                inputStream = context.getContentResolver().openInputStream(imageUri);
                if (inputStream == null) {
                    callback.onFailure(null, new IOException("无法读取图片文件，请确保图片存在且有访问权限"));
                    return;
                }
            } catch (SecurityException se) {
                callback.onFailure(null, new IOException("无权限访问图片文件: " + se.getMessage(), se));
                return;
            } catch (Exception e) {
                callback.onFailure(null, new IOException("打开图片文件时出错: " + e.getMessage(), e));
                return;
            }

            // 创建临时文件
            File tempFile = null;
            FileOutputStream fos = null;
            try {
                tempFile = File.createTempFile("image", ".jpg", context.getCacheDir());
                fos = new FileOutputStream(tempFile);

                // 复制文件内容
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
            } catch (Exception e) {
                callback.onFailure(null, new IOException("处理图片文件时出错: " + e.getMessage(), e));
                return;
            } finally {
                // 确保流被关闭
                try {
                    if (fos != null) fos.close();
                    if (inputStream != null) inputStream.close();
                } catch (IOException e) {
                    // 记录关闭流时的错误，但不中断流程
                    e.printStackTrace();
                }
            }

            File imageFile = tempFile;
            String mimeType = getMimeType(imageUri);
            if (mimeType == null || mimeType.isEmpty()) {
                // 如果无法获取MIME类型，使用默认值
                mimeType = "image/jpeg";
            }

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                            "image",
                            imageFile.getName(),
                            RequestBody.create(MediaType.parse(mimeType), imageFile)
                    )
                    .addFormDataPart("points", gson.toJson(points))
                    .addFormDataPart("gravity", gson.toJson(gravity))
                    .addFormDataPart("known_distance", String.valueOf(knownDistance))
                    .build();

            Request request = new Request.Builder()
                    .url(BASE_URL + "measure_height/")
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(callback);
        } catch (Exception e) {
            // 捕获所有可能的异常并通过回调返回错误信息
            callback.onFailure(null, new IOException("提交测量数据时出错: " + e.getMessage(), e));
        }
    }

    private String getMimeType(Uri uri) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    }

    private boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }
}