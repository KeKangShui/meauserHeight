package com.example.myapplicationbb;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.constraintlayout.widget.ConstraintLayout;
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
import com.example.myapplicationbb.network.MeasurementApi;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        previewView = view.findViewById(R.id.preview_view);
        // 确保相机预览视图可见
        previewView.setVisibility(View.VISIBLE);
        photoPreview = view.findViewById(R.id.photo_preview);
        distanceInput = view.findViewById(R.id.distance_input);
        confirmPointsButton = view.findViewById(R.id.confirm_points_button);
        Button submitButton = view.findViewById(R.id.submit_button);
        Button retakeButton = view.findViewById(R.id.retake_button);

        selectedPoints = new ArrayList<>();
        gravity = new float[3];
        hasGravityData = false;

        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // 添加图片点击事件监听
        photoPreview.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (selectedPoints.size() >= 2) {
                    Toast.makeText(requireContext(), "已选择两个点，请先确认或重新开始", Toast.LENGTH_SHORT).show();
                    return true;
                }

                // 记录触摸点坐标
                float[] point = new float[]{event.getX(), event.getY()};
                selectedPoints.add(point);

                // 在图片上显示选中的点
                drawPointOnImage(point);

                // 显示调试信息
                Toast.makeText(requireContext(), "已添加点: (" + point[0] + ", " + point[1] + ")", Toast.LENGTH_SHORT).show();

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

            // 确认选点后，清除之前的标记并重新绘制，以确保标记点与图片位置同步
            ViewGroup parentView = (ViewGroup) photoPreview.getParent();
            if (parentView instanceof ViewGroup) {
                // 找到所有标记并移除
                for (int i = 0; i < parentView.getChildCount(); i++) {
                    View child = parentView.getChildAt(i);
                    if (child instanceof ImageView && child.getId() != R.id.photo_preview) {
                        parentView.removeView(child);
                        i--; // 因为移除了一个元素，所以索引需要减一
                    }
                }
            }

            // 重新绘制所有选中的点
            for (float[] point : selectedPoints) {
                drawPointOnImage(point);
            }
        });

        submitButton.setOnClickListener(v -> submitMeasurement());

        // 添加重新拍摄按钮的点击事件
        retakeButton.setOnClickListener(v -> {
            // 重置UI状态
            previewView.setVisibility(View.VISIBLE);
            View photoPreviewContainer = requireView().findViewById(R.id.photo_preview_container);
            photoPreviewContainer.setVisibility(View.GONE);
            photoPreview.setVisibility(View.GONE);
            selectedPoints.clear();
            confirmPointsButton.setVisibility(View.GONE);
            confirmPointsButton.setEnabled(true);
            submitButton.setVisibility(View.GONE);

            // 清除之前添加的标记
            ViewGroup parentView = (ViewGroup) photoPreview.getParent();
            if (parentView instanceof ViewGroup) {
                for (int i = 0; i < parentView.getChildCount(); i++) {
                    View child = parentView.getChildAt(i);
                    if (child instanceof ImageView && child.getId() != R.id.photo_preview) {
                        parentView.removeView(child);
                        i--;
                    }
                }
            }
        });

        galleryLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        currentPhotoUri = result.getData().getData();
                        showPhotoPreview();
                    }
                });

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(requireActivity(), REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        view.findViewById(R.id.capture_button).setOnClickListener(v -> takePhoto());
        view.findViewById(R.id.gallery_button).setOnClickListener(v -> openGallery());
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
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
                Toast.makeText(requireContext(), "无法启动相机", Toast.LENGTH_SHORT).show();
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

        // 清除之前的选点
        selectedPoints.clear();

        // 清除之前添加的标记
        ViewGroup parentView = (ViewGroup) photoPreview.getParent();
        if (parentView instanceof ViewGroup) {
            // 找到所有标记并移除
            for (int i = 0; i < parentView.getChildCount(); i++) {
                View child = parentView.getChildAt(i);
                if (child instanceof ImageView && child.getId() != R.id.photo_preview) {
                    parentView.removeView(child);
                    i--; // 因为移除了一个元素，所以索引需要减一
                }
            }
        }
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

        // 显示提交参数的日志
        showSubmitParamsDialog(currentPhotoUri, selectedPoints, gravity, knownDistance);

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
                            // 使用AlertDialog代替Toast，显示更详细的错误信息
                            new AlertDialog.Builder(requireContext())
                                    .setTitle("提交失败")
                                    .setMessage("错误信息：" + e.getMessage())
                                    .setPositiveButton("确定", null)
                                    .show();
                        });
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        final String responseBody = response.body().string();
                        requireActivity().runOnUiThread(() -> {
                            try {
                                // 检查Fragment是否仍然附加到Activity
                                if (!isAdded()) {
                                    return;
                                }

                                // 创建并显示结果Fragment
                                MeasurementResultFragment resultFragment = new MeasurementResultFragment();
                                Bundle args = new Bundle();
                                args.putString("measurement_result", responseBody);
                                resultFragment.setArguments(args);

                                // 导航到结果页面
                                if (getActivity() != null && !getActivity().isFinishing()) {
                                    getActivity().getSupportFragmentManager()
                                            .beginTransaction()
                                            .replace(R.id.nav_host_fragment, resultFragment)
                                            .addToBackStack(null)
                                            .commit();
                                }

                                // 重置UI状态
                                previewView.setVisibility(View.VISIBLE);
                                View photoPreviewContainer = requireView().findViewById(R.id.photo_preview_container);
                                photoPreviewContainer.setVisibility(View.GONE);
                                photoPreview.setVisibility(View.GONE);
                                selectedPoints.clear();
                                confirmPointsButton.setVisibility(View.GONE);
                                confirmPointsButton.setEnabled(true);
                                distanceInput.setText("");
                            } catch (Exception e) {
                                Toast.makeText(requireContext(), "处理结果时出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                startCamera();
            } else {
                Toast.makeText(requireContext(), "需要相机权限才能使用此功能", Toast.LENGTH_SHORT).show();
                requireActivity().finish();
            }
        }
    }



    private void drawPointOnImage(float[] point) {
        try {
            // 在图片上绘制选中的点
            ImageView marker = new ImageView(requireContext());
            marker.setImageResource(R.drawable.ic_point_marker);
            marker.setId(View.generateViewId());

            // 获取photoPreview的父视图 - MaterialCardView
            ViewGroup cardView = (ViewGroup) photoPreview.getParent();

            // 设置标记的大小
            int markerSize = getResources().getDimensionPixelSize(R.dimen.point_marker_size);

            // 创建布局参数
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                    markerSize,
                    markerSize
            );

            // 将标记添加到与photoPreview相同的父视图中
            cardView.addView(marker, params);

            // 调整点的位置，考虑图片在ImageView中的实际显示情况
            float[] adjustedPoint = adjustPointToPhotoPreview(point);

            // 设置标记的位置，使其覆盖在photoPreview上的触摸点
            marker.setX(adjustedPoint[0] - markerSize / 2);
            marker.setY(adjustedPoint[1] - markerSize / 2);

            // 确保标记在最上层显示
            marker.bringToFront();
            cardView.invalidate();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "添加标记失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

    }


    private float[] adjustPointToPhotoPreview(float[] point) {
        // 获取图片在ImageView中的实际显示区域
        if (photoPreview.getDrawable() == null) {
            return point; // 如果没有图片，直接返回原始坐标
        }

        float imageWidth = photoPreview.getDrawable().getIntrinsicWidth();
        float imageHeight = photoPreview.getDrawable().getIntrinsicHeight();
        float viewWidth = photoPreview.getWidth();
        float viewHeight = photoPreview.getHeight();

        if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return point; // 防止除以零错误
        }

        // 计算缩放比例
        float scale = Math.min(viewWidth / imageWidth, viewHeight / imageHeight);

        // 计算图片在ImageView中的实际显示尺寸
        float scaledWidth = imageWidth * scale;
        float scaledHeight = imageHeight * scale;

        // 计算图片在ImageView中的偏移量
        float offsetX = (viewWidth - scaledWidth) / 2;
        float offsetY = (viewHeight - scaledHeight) / 2;

        // 检查点是否在图片显示区域内
        if (point[0] < offsetX || point[0] > offsetX + scaledWidth ||
                point[1] < offsetY || point[1] > offsetY + scaledHeight) {
            // 如果点不在图片显示区域内，将其限制在图片边界内
            point[0] = Math.max(offsetX, Math.min(point[0], offsetX + scaledWidth));
            point[1] = Math.max(offsetY, Math.min(point[1], offsetY + scaledHeight));
        }

        // 直接返回调整后的坐标，因为我们已经在drawPointOnImage中使用了这些坐标
        return point;
    }


    /**
     * 显示提交参数的对话框
     * @param imageUri 图片URI
     * @param points 选中的点
     * @param gravityData 重力传感器数据
     * @param distance 已知距离
     */
    private void showSubmitParamsDialog(Uri imageUri, List<float[]> points, float[] gravityData, float distance) {
        // 创建一个包含ScrollView的TextView，以便显示大量文本
        ScrollView scrollView = new ScrollView(requireContext());
        TextView textView = new TextView(requireContext());
        textView.setPadding(30, 30, 30, 30);
        scrollView.addView(textView);

        // 构建参数信息文本
        StringBuilder paramsInfo = new StringBuilder();
        paramsInfo.append("===== 提交参数信息 =====\n\n");

        // 图片信息
        paramsInfo.append("【图片信息】\n");
        paramsInfo.append("URI: ").append(imageUri.toString()).append("\n\n");

        // 选中的点信息
        paramsInfo.append("【选中的点坐标】\n");
        for (int i = 0; i < points.size(); i++) {
            float[] point = points.get(i);
            paramsInfo.append("点").append(i + 1).append(": (");
            paramsInfo.append(String.format(Locale.CHINA, "%.2f", point[0])).append(", ");
            paramsInfo.append(String.format(Locale.CHINA, "%.2f", point[1])).append(")\n");
        }
        paramsInfo.append("\n");

        // 重力传感器数据
        paramsInfo.append("【重力传感器数据】\n");
        paramsInfo.append("X: ").append(String.format(Locale.CHINA, "%.4f", gravityData[0])).append("\n");
        paramsInfo.append("Y: ").append(String.format(Locale.CHINA, "%.4f", gravityData[1])).append("\n");
        paramsInfo.append("Z: ").append(String.format(Locale.CHINA, "%.4f", gravityData[2])).append("\n\n");

        // 已知距离
        paramsInfo.append("【已知距离】\n");
        paramsInfo.append(distance).append(" 厘米\n");

        // 设置文本
        textView.setText(paramsInfo.toString());

        // 创建并显示对话框
        new AlertDialog.Builder(requireContext())
                .setTitle("测量参数确认")
                .setView(scrollView)
                .setPositiveButton("确认提交", null)
                .show();
    }
}