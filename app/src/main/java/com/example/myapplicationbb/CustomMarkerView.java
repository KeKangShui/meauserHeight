package com.example.myapplicationbb;

import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.charts.LineChart;

public class CustomMarkerView extends MarkerView {
    private TextView dateTextView;
    private TextView heightTextView;

    public CustomMarkerView(Context context, int layoutResource) {
        super(context, layoutResource);
        try {
            dateTextView = findViewById(R.id.marker_date);
            heightTextView = findViewById(R.id.marker_height);
        } catch (Exception e) {
            // 处理视图初始化异常
            e.printStackTrace();
        }
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        // 添加空值检查
        if (dateTextView == null || heightTextView == null) {
            super.refreshContent(e, highlight);
            return;
        }

        if (e == null) {
            dateTextView.setText("");
            heightTextView.setText("");
            super.refreshContent(e, highlight);
            return;
        }

        try {
            Object data = e.getData();
            // 使用instanceof检查并安全地转换数据类型
            if (data != null && data instanceof HistoryFragment.MeasurementRecord) {
                HistoryFragment.MeasurementRecord record = (HistoryFragment.MeasurementRecord) data;
                if (record.date != null) {
                    dateTextView.setText("日期: " + record.date);
                    heightTextView.setText(String.format("身高: %.1f厘米", e.getY()));
                } else {
                    dateTextView.setText("日期: 未知");
                    heightTextView.setText(String.format("身高: %.1f厘米", e.getY()));
                }
            } else {
                // 如果数据不是预期类型，显示默认值
                dateTextView.setText("日期: --");
                heightTextView.setText(String.format("身高: %.1f厘米", e.getY()));
            }
        } catch (Exception ex) {
            // 捕获并处理所有可能的异常
            ex.printStackTrace();
            dateTextView.setText("日期: --");
            heightTextView.setText("身高: --");
        }

        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2), -getHeight());
    }
}