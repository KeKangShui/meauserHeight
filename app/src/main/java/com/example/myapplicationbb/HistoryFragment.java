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

import com.example.myapplicationbb.data.MeasurementDatabase;
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

        // 初始化图表
        heightChart = view.findViewById(R.id.height_chart);

        // 加载历史记录
        loadMeasurementHistory();

        // 设置图表
        setupChart();

        return view;
    }

    private void setupChart() {
        if (heightChart == null || getContext() == null) return;

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

        // 增加顶部间距，解决空间拥挤问题
        heightChart.setMinOffset(30f);
        heightChart.setExtraOffsets(15f, 40f, 15f, 15f);

        // 启用水平滚动
        heightChart.setDragXEnabled(true);
        heightChart.setVisibleXRangeMaximum(5f); // 一次最多显示5个数据点
        heightChart.setHighlightPerDragEnabled(true);
        heightChart.setHighlightPerTapEnabled(true);

        try {
            heightChart.setMarker(new CustomMarkerView(requireContext(), R.layout.marker_view));
        } catch (Exception e) {
            // 处理MarkerView创建失败的情况
            Toast.makeText(requireContext(), "图表标记初始化失败", Toast.LENGTH_SHORT).show();
        }

        // 配置X轴
        XAxis xAxis = heightChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.DKGRAY);
        xAxis.setTextSize(10f);
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

        // 计算Y轴范围 - 默认值设置为合理的身高范围
        float minHeight = Float.MAX_VALUE;
        float maxHeight = Float.MIN_VALUE;

        if (measurementRecords.isEmpty()) {
            // 如果没有数据，设置一个默认的合理范围
            minHeight = 170f; // 默认最小值
            maxHeight = 180f; // 默认最大值
        } else {
            // 计算实际数据的最小最大值
            for (MeasurementRecord record : measurementRecords) {
                minHeight = Math.min(minHeight, record.height);
                maxHeight = Math.max(maxHeight, record.height);
            }

            // 确保最小和最大值有足够的差距以显示变化
            if (maxHeight - minHeight < 2f) {
                float avgHeight = (maxHeight + minHeight) / 2;
                minHeight = avgHeight - 1f;
                maxHeight = avgHeight + 1f;
            }
        }

        // 设置Y轴范围，添加合理的上下边距
        float yAxisRange = maxHeight - minHeight;
        float yAxisMargin = Math.max(yAxisRange * 0.15f, 0.5f); // 至少0.5cm的边距或15%的数据范围

        // 设置Y轴的最小值和最大值
        leftAxis.setAxisMinimum(minHeight - yAxisMargin);
        leftAxis.setAxisMaximum(maxHeight + yAxisMargin);

        // 根据数据范围设置合适的Y轴间隔
        float granularity = calculateYAxisGranularity(yAxisRange);
        leftAxis.setGranularity(granularity);

        leftAxis.setTextColor(Color.DKGRAY);
        leftAxis.setTextSize(10f);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format("%.1f厘米", value);
            }
        });
        heightChart.getAxisRight().setEnabled(false);

        // 配置图例
        Legend legend = heightChart.getLegend();
        legend.setTextSize(10f);
        legend.setTextColor(Color.DKGRAY);
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setWordWrapEnabled(true);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
    }

    // 根据数据范围计算合适的Y轴间隔
    private float calculateYAxisGranularity(float range) {
        if (range <= 1f) return 0.2f;
        if (range <= 2f) return 0.5f;
        if (range <= 5f) return 1.0f;
        if (range <= 10f) return 2.0f;
        return 5.0f;
    }

    private void updateChart() {
        if (heightChart == null || measurementRecords == null || measurementRecords.isEmpty()) {
            // 如果没有数据或图表未初始化，则不进行更新
            if (heightChart != null) {
                heightChart.clear();
                heightChart.invalidate();
            }
            return;
        }

        try {
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < measurementRecords.size(); i++) {
                MeasurementRecord record = measurementRecords.get(i);
                if (record != null) {
                    Entry entry = new Entry(i, record.height);
                    entry.setData(record);
                    entries.add(entry);
                }
            }

            if (entries.isEmpty()) {
                heightChart.clear();
                heightChart.invalidate();
                return;
            }

            LineDataSet dataSet = new LineDataSet(entries, "身高变化趋势");
            dataSet.setColor(getResources().getColor(R.color.primary));
            dataSet.setCircleColor(getResources().getColor(R.color.primary));
            dataSet.setLineWidth(2.5f); // 增加线宽
            dataSet.setCircleRadius(5f); // 增加圆点大小
            dataSet.setCircleHoleRadius(2.5f); // 设置圆点内部空洞大小
            dataSet.setDrawCircleHole(true); // 绘制圆点内部空洞
            dataSet.setDrawValues(true);
            dataSet.setValueTextSize(10f); // 增加数值文字大小
            dataSet.setValueTextColor(Color.DKGRAY);

            // 优化曲线显示
            if (entries.size() > 2) {
                dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
                dataSet.setCubicIntensity(0.15f); // 调整曲线平滑度，降低一点使曲线更自然
            } else {
                dataSet.setMode(LineDataSet.Mode.LINEAR);
            }

            dataSet.setDrawFilled(true);
            dataSet.setFillColor(getResources().getColor(R.color.primary_dark));
            dataSet.setFillAlpha(50); // 增加填充透明度
            dataSet.setHighlightEnabled(true);
            dataSet.setDrawHorizontalHighlightIndicator(false);
            dataSet.setHighLightColor(getResources().getColor(R.color.accent));
            dataSet.setHighlightLineWidth(1.5f); // 设置高亮线宽度

            LineData lineData = new LineData(dataSet);
            lineData.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return String.format("%.1f", value);
                }
            });

            heightChart.setData(lineData);
            heightChart.invalidate();
        } catch (Exception e) {
            // 处理图表数据更新过程中的异常
            Toast.makeText(requireContext(), "图表数据更新失败", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void loadMeasurementHistory() {
        try {
            // 从数据库加载历史记录
            if (measurementRecords == null) {
                measurementRecords = new ArrayList<>();
            } else {
                measurementRecords.clear();
            }

            // 确保Context可用
            if (getContext() == null) {
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                return;
            }

            List<MeasurementRecord> records = MeasurementDatabase
                    .getInstance(requireContext())
                    .getAllMeasurements();

            // 将加载的记录添加到列表中
            if (records != null) {
                measurementRecords.addAll(records);
            }

            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }

            updateChart();
        } catch (Exception e) {
            // 处理加载历史记录过程中的异常
            if (getContext() != null) {
                Toast.makeText(requireContext(), "加载历史记录失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            e.printStackTrace();
        } finally {
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }
    }

    // 数据模型类
    public static class MeasurementRecord {
        public String date;
        public float height;

        public MeasurementRecord(String date, float height) {
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 释放图表资源，避免内存泄漏
        if (heightChart != null) {
            heightChart.clear();
            heightChart.invalidate();
            heightChart = null;
        }
        // 清空数据引用
        if (measurementRecords != null) {
            measurementRecords.clear();
        }
    }
}