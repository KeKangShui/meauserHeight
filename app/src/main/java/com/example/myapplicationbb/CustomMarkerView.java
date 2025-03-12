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
        dateTextView = findViewById(R.id.marker_date);
        heightTextView = findViewById(R.id.marker_height);
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        if (e == null) return;

        int index = (int) e.getX();
        if (getChartView() instanceof LineChart) {
            LineChart chart = (LineChart) getChartView();
            if (chart.getData() != null && chart.getData().getDataSetCount() > 0) {
                LineDataSet dataSet = (LineDataSet) chart.getData().getDataSetByIndex(0);
                if (dataSet != null && index >= 0 && index < dataSet.getEntryCount()) {
                    Object data = e.getData();
                    if (data instanceof HistoryFragment.MeasurementRecord) {
                        HistoryFragment.MeasurementRecord record = (HistoryFragment.MeasurementRecord) data;
                        dateTextView.setText("日期: " + record.date);
                        heightTextView.setText(String.format("身高: %.1f厘米", e.getY()));
                    }
                }
            }
        }
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2), -getHeight());
    }
}