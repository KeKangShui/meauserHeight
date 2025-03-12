package com.example.myapplicationbb.network;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import com.google.gson.Gson;
import okhttp3.*;
import java.io.File;
import java.io.IOException;
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

        File imageFile = new File(imageUri.getPath());
        String mimeType = getMimeType(imageUri);

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