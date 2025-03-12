package com.example.myapplicationbb;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import android.graphics.Color;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.components.Legend;


public class HistoryFragment extends Fragment {
    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private List<MeasurementRecord> measurementRecords;
    private LineChart heightChart;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        // 初始化图表
        heightChart = view.findViewById(R.id.height_chart);
        setupChart();

        // 初始化SwipeRefreshLayout
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this::loadMeasurementHistory);

        recyclerView = view.findViewById(R.id.history_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        measurementRecords = new ArrayList<>();
        adapter = new HistoryAdapter(measurementRecords, record -> {
            // TODO: 实现点击查看详情
            Toast.makeText(requireContext(), "查看详情功能开发中", Toast.LENGTH_SHORT).show();
        });

        recyclerView.setAdapter(adapter);

        // 加载历史记录
        loadMeasurementHistory();

        return view;
    }

    private void setupChart() {
        // 配置图表基本属性
        heightChart.getDescription().setEnabled(false);
        heightChart.setTouchEnabled(true);
        heightChart.setDragEnabled(true);
        heightChart.setScaleEnabled(true);
        heightChart.setPinchZoom(true);
        heightChart.setDrawGridBackground(false);
        heightChart.setBackgroundColor(Color.WHITE);
        heightChart.setNoDataText("暂无数据");
        heightChart.setNoDataTextColor(Color.GRAY);
        heightChart.animateX(1500);
        heightChart.setMinOffset(15f);
        heightChart.setExtraOffsets(10f, 10f, 10f, 20f);
        heightChart.setHighlightPerDragEnabled(true);
        heightChart.setHighlightPerTapEnabled(true);
        heightChart.setMarker(new CustomMarkerView(requireContext(), R.layout.marker_view));

        // 配置X轴
        XAxis xAxis = heightChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.DKGRAY);
        xAxis.setTextSize(12f);
        xAxis.setLabelRotationAngle(-45);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < measurementRecords.size()) {
                    return measurementRecords.get(index).date;
                }
                return "";
            }
        });

        // 配置Y轴
        YAxis leftAxis = heightChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.LTGRAY);
        leftAxis.setGridLineWidth(0.5f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setTextColor(Color.DKGRAY);
        leftAxis.setTextSize(12f);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format("%.1f厘米", value);
            }
        });
        heightChart.getAxisRight().setEnabled(false);

        // 配置图例
        heightChart.getLegend().setTextSize(12f);
        heightChart.getLegend().setTextColor(Color.DKGRAY);
        heightChart.getLegend().setForm(Legend.LegendForm.CIRCLE);
        heightChart.getLegend().setWordWrapEnabled(true);
    }

    private void updateChart() {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < measurementRecords.size(); i++) {
            entries.add(new Entry(i, measurementRecords.get(i).height));
        }

        LineDataSet dataSet = new LineDataSet(entries, "身高变化趋势");
        dataSet.setColor(getResources().getColor(R.color.primary));
        dataSet.setCircleColor(getResources().getColor(R.color.primary));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextSize(10f);
        dataSet.setValueTextColor(Color.DKGRAY);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.2f);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(getResources().getColor(R.color.primary_dark));
        dataSet.setFillAlpha(50);
        dataSet.setHighlightEnabled(true);
        dataSet.setDrawHorizontalHighlightIndicator(false);
        dataSet.setHighLightColor(getResources().getColor(R.color.accent));

        LineData lineData = new LineData(dataSet);
        lineData.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format("%.1f", value);
            }
        });

        heightChart.setData(lineData);
        heightChart.invalidate();
    }

    private void loadMeasurementHistory() {
        // TODO: 从数据库加载历史记录
        // 临时添加测试数据
        measurementRecords.clear();
        measurementRecords.add(new MeasurementRecord("2024-01-01", 175.5f));
        measurementRecords.add(new MeasurementRecord("2024-01-02", 176.0f));
        measurementRecords.add(new MeasurementRecord("2024-01-03", 175.8f));
        measurementRecords.add(new MeasurementRecord("2024-01-04", 176.2f));

        adapter.notifyDataSetChanged();
        updateChart();

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    // 数据模型类
    public static class MeasurementRecord {
        String date;
        float height;

        MeasurementRecord(String date, float height) {
            this.date = date;
            this.height = height;
        }
    }

    // RecyclerView适配器
    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private final List<MeasurementRecord> records;
        private final OnItemClickListener listener;

        interface OnItemClickListener {
            void onItemClick(MeasurementRecord record);
        }

        HistoryAdapter(List<MeasurementRecord> records, OnItemClickListener listener) {
            this.records = records;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MeasurementRecord record = records.get(position);
            holder.bind(record, listener);
        }

        @Override
        public int getItemCount() {
            return records.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView dateText;
            private final TextView heightText;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                dateText = itemView.findViewById(R.id.date_text);
                heightText = itemView.findViewById(R.id.height_text);
            }

            void bind(MeasurementRecord record, OnItemClickListener listener) {
                dateText.setText("测量日期：" + record.date);
                heightText.setText(String.format("身高：%.1f厘米", record.height));
                itemView.setOnClickListener(v -> listener.onItemClick(record));
            }
        }
    }
}