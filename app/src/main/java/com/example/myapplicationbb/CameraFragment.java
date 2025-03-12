package com.example.myapplicationbb;


import android.Manifest;
import com.example.myapplicationbb.network.MeasurementApi;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.view.MotionEvent;
import androidx.constraintlayout.widget.ConstraintLayout;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class CameraFragment extends Fragment implements SensorEventListener {
    private PreviewView previewView;
    private ImageView photoPreview;
    private ImageCapture imageCapture;
    private EditText distanceInput;
    private Button confirmPointsButton;
    private Uri currentPhotoUri;
    private List<float[]> selectedPoints;
    private float[] gravity;
    private boolean hasGravityData;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final int REQUEST_IMAGE_PICK = 100;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
    private ActivityResultLauncher<Intent> galleryLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        previewView = view.findViewById(R.id.preview_view);
        photoPreview = view.findViewById(R.id.photo_preview);
        distanceInput = view.findViewById(R.id.distance_input);
        confirmPointsButton = view.findViewById(R.id.confirm_points_button);
        Button submitButton = view.findViewById(R.id.submit_button);
        Button retakeButton = view.findViewById(R.id.retake_button);
        Button galleryButton = view.findViewById(R.id.gallery_button);

        selectedPoints = new ArrayList<>();
        gravity = new float[3];
        hasGravityData = false;

        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // 添加重拍按钮点击事件
        retakeButton.setOnClickListener(v -> {
            // 清除当前照片和选点状态
            currentPhotoUri = null;
            selectedPoints.clear();
            // 移除所有标记点
            ViewGroup cardView = (ViewGroup) photoPreview.getParent();
            for (int i = cardView.getChildCount() - 1; i >= 0; i--) {
                View child = cardView.getChildAt(i);
                if (child instanceof ImageView && child != photoPreview) {
                    cardView.removeView(child);
                }
            }
            // 重置按钮状态
            confirmPointsButton.setVisibility(View.GONE);
            confirmPointsButton.setEnabled(true);
            submitButton.setVisibility(View.GONE);
            // 切换回相机预览
            previewView.setVisibility(View.VISIBLE);
            View photoPreviewContainer = requireView().findViewById(R.id.photo_preview_container);
            photoPreviewContainer.setVisibility(View.GONE);
            photoPreview.setVisibility(View.GONE);
        });

        // 添加图片点击事件监听
        photoPreview.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (selectedPoints.size() >= 2) {
                    Toast.makeText(requireContext(), "已选择两个点，请先确认或重新开始", Toast.LENGTH_SHORT).show();
                    return true;
                }

                float[] point = new float[]{event.getX(), event.getY()};
                selectedPoints.add(point);

                // 在图片上显示选中的点
                drawPointOnImage(point);

                if (selectedPoints.size() == 2) {
                    confirmPointsButton.setVisibility(View.VISIBLE);
                }

                return true;
            }
            return false;
        });

        confirmPointsButton.setOnClickListener(v -> {
            submitButton.setVisibility(View.VISIBLE);
            confirmPointsButton.setEnabled(false);
        });

        submitButton.setOnClickListener(v -> submitMeasurement());

        galleryLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        currentPhotoUri = result.getData().getData();
                        showPhotoPreview();
                    }
                });

        // 添加从相册选择图片的点击事件
        galleryButton.setOnClickListener(v -> openGallery());

        view.findViewById(R.id.capture_button).setOnClickListener(v -> takePhoto());

        // 检查权限并延迟初始化相机，确保视图已准备好
        if (allPermissionsGranted()) {
            // 延迟初始化相机，确保PreviewView已完全准备好
            previewView.post(() -> {
                if (isAdded() && !isDetached() && !isRemoving()) {
                    startCamera();
                }
            });
        } else {
            ActivityCompat.requestPermissions(requireActivity(), REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void startCamera() {
        // 添加额外的安全检查，确保Fragment仍然附加到Activity
        if (!isAdded() || isDetached() || isRemoving()) {
            return;
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            // 再次检查Fragment状态，避免在异步回调中Fragment已被销毁
            if (!isAdded() || isDetached() || isRemoving()) {
                return;
            }

            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(getViewLifecycleOwner(), cameraSelector, preview, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                if (isAdded() && !isDetached() && !isRemoving()) {
                    Toast.makeText(requireContext(), "无法启动相机", Toast.LENGTH_SHORT).show();
                }
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        File photoFile = new File(requireContext().getExternalCacheDir(),
                new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.CHINA)
                        .format(System.currentTimeMillis()) + ".jpg");

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, executor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Uri savedUri = Uri.fromFile(photoFile);
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "照片已保存", Toast.LENGTH_SHORT).show();
                            currentPhotoUri = savedUri;
                            showPhotoPreview();
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        requireActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), "拍照失败", Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private void showPhotoPreview() {
        previewView.setVisibility(View.GONE);
        View photoPreviewContainer = requireView().findViewById(R.id.photo_preview_container);
        photoPreviewContainer.setVisibility(View.VISIBLE);
        photoPreview.setVisibility(View.VISIBLE);
        photoPreview.setImageURI(currentPhotoUri);
        selectedPoints.clear();
    }

    private void submitMeasurement() {
        if (selectedPoints.size() != 2 || currentPhotoUri == null || !hasGravityData) {
            Toast.makeText(requireContext(), "请确保已选择两个点并获取传感器数据", Toast.LENGTH_SHORT).show();
            return;
        }

        String distanceStr = distanceInput.getText().toString();
        if (distanceStr.isEmpty()) {
            Toast.makeText(requireContext(), "请输入拍摄距离", Toast.LENGTH_SHORT).show();
            return;
        }

        float knownDistance = Float.parseFloat(distanceStr);

        MeasurementApi api = new MeasurementApi();
        api.submitMeasurement(
                requireContext(),
                currentPhotoUri,
                selectedPoints,
                gravity,
                knownDistance,
                new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "提交失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        String responseBody = response.body().string();
                        requireActivity().runOnUiThread(() -> {
                            try {
                                // 创建并显示结果Fragment
                                MeasurementResultFragment resultFragment = new MeasurementResultFragment();
                                Bundle args = new Bundle();
                                args.putString("measurement_result", responseBody);
                                resultFragment.setArguments(args);

                                // 导航到结果页面
                                if (isAdded() && !isRemoving()) {
                                    requireActivity().getSupportFragmentManager()
                                            .beginTransaction()
                                            .replace(R.id.nav_host_fragment, resultFragment)
                                            .addToBackStack(null)
                                            .commit();
                                }
                            } catch (Exception e) {
                                Log.e("CameraFragment", "Error navigating to result fragment", e);
                                Toast.makeText(requireContext(), "处理结果时发生错误", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
        );
    }



    @Override
    public void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravity, 0, 3);
            hasGravityData = true;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 不需要处理精度变化
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                // 使用延迟初始化相机，确保PreviewView已完全准备好
                previewView.post(() -> {
                    if (isAdded() && !isDetached() && !isRemoving()) {
                        startCamera();
                    }
                });
            } else {
                Toast.makeText(requireContext(), "需要相机权限才能使用此功能", Toast.LENGTH_SHORT).show();
                requireActivity().finish();
            }
        }
    }



    private void drawPointOnImage(float[] point) {
        // 在图片上绘制选中的点
        ImageView marker = new ImageView(requireContext());
        marker.setImageResource(R.drawable.ic_point_marker);
        marker.setId(View.generateViewId());

        // 获取photoPreview的父视图（MaterialCardView）
        ViewGroup cardView = (ViewGroup) photoPreview.getParent();

        // 创建相对布局参数
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        // 设置标记的大小
        int markerSize = getResources().getDimensionPixelSize(R.dimen.point_marker_size);
        params.width = markerSize;
        params.height = markerSize;

        // 计算标记的位置，考虑到标记的大小和图片的实际显示区域
        float[] adjustedPoint = adjustPointToPhotoPreview(point);
        params.leftMargin = (int) adjustedPoint[0] - markerSize / 2;
        params.topMargin = (int) adjustedPoint[1] - markerSize / 2;

        // 添加标记到MaterialCardView
        cardView.addView(marker, params);
        marker.bringToFront();
    }

    private float[] adjustPointToPhotoPreview(float[] point) {
        // 获取图片在ImageView中的实际显示区域
        float imageWidth = photoPreview.getDrawable().getIntrinsicWidth();
        float imageHeight = photoPreview.getDrawable().getIntrinsicHeight();
        float viewWidth = photoPreview.getWidth();
        float viewHeight = photoPreview.getHeight();

        // 计算缩放比例
        float scale = Math.min(viewWidth / imageWidth, viewHeight / imageHeight);

        // 计算图片在ImageView中的实际显示尺寸
        float scaledWidth = imageWidth * scale;
        float scaledHeight = imageHeight * scale;

        // 计算图片在ImageView中的偏移量
        float offsetX = (viewWidth - scaledWidth) / 2;
        float offsetY = (viewHeight - scaledHeight) / 2;

        // 调整点的坐标
        return new float[]{
                point[0] * (scaledWidth / viewWidth) + offsetX,
                point[1] * (scaledHeight / viewHeight) + offsetY
        };
    }
}