package com.example.myapplicationbb;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.text.DecimalFormat;

public class MeasurementResultFragment extends Fragment {
    private TextView heightValue;
    private TextView generalAdviceContent;
    private TextView specificAdviceContent;
    private Button saveResultButton;
    private float measuredHeight;
    private JsonObject nutritionAdvice;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_measurement_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        heightValue = view.findViewById(R.id.height_value);
        generalAdviceContent = view.findViewById(R.id.general_advice_content);
        specificAdviceContent = view.findViewById(R.id.specific_advice_content);
        saveResultButton = view.findViewById(R.id.save_result_button);

        // 获取传递的测量结果数据
        Bundle args = getArguments();
        if (args != null) {
            String resultJson = args.getString("measurement_result");
            if (resultJson != null) {
                JsonObject result = new Gson().fromJson(resultJson, JsonObject.class);
                displayResult(result);
            }
        }

        saveResultButton.setOnClickListener(v -> saveResult());
    }

    private void displayResult(JsonObject result) {
        if (result.has("height") && result.has("nutrition_advice")) {
            measuredHeight = result.get("height").getAsFloat();
            nutritionAdvice = result.getAsJsonObject("nutrition_advice");

            // 显示身高
            DecimalFormat df = new DecimalFormat("#.0");
            heightValue.setText(df.format(measuredHeight) + " cm");

            // 显示一般建议
            StringBuilder generalAdvice = new StringBuilder();
            for (JsonElement advice : nutritionAdvice.getAsJsonArray("general").asList()) {
                generalAdvice.append("• ").append(advice).append("\n");
            }
            generalAdviceContent.setText(generalAdvice.toString());

            // 显示个性化建议
            StringBuilder specificAdvice = new StringBuilder();
            for (JsonElement advice : nutritionAdvice.getAsJsonArray("specific").asList()) {
                specificAdvice.append("• ").append(advice).append("\n");
            }
            specificAdviceContent.setText(specificAdvice.toString());
        }
    }

    private void saveResult() {
        // TODO: 实现保存结果到本地数据库的功能
        Toast.makeText(requireContext(), "结果已保存", Toast.LENGTH_SHORT).show();
    }
}