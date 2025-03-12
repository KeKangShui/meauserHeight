package com.example.myapplicationbb.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.example.myapplicationbb.HistoryFragment.MeasurementRecord;

public class MeasurementDatabase extends SQLiteOpenHelper {
    private static final String TAG = "MeasurementDatabase";
    private static final String DATABASE_NAME = "height_measurements.db";
    private static final int DATABASE_VERSION = 1;

    // 表名
    private static final String TABLE_MEASUREMENTS = "measurements";

    // 列名
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_DATE = "date";
    private static final String COLUMN_HEIGHT = "height";

    // 单例实例
    private static MeasurementDatabase instance;

    // 获取数据库实例（单例模式）
    public static synchronized MeasurementDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new MeasurementDatabase(context.getApplicationContext());
        }
        return instance;
    }

    private MeasurementDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 创建测量记录表
        String CREATE_MEASUREMENTS_TABLE = "CREATE TABLE " + TABLE_MEASUREMENTS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_DATE + " TEXT NOT NULL,"
                + COLUMN_HEIGHT + " REAL NOT NULL"
                + ")";
        db.execSQL(CREATE_MEASUREMENTS_TABLE);
        Log.d(TAG, "数据库表创建成功");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 升级数据库时删除旧表并创建新表
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEASUREMENTS);
        onCreate(db);
    }

    /**
     * 保存新的测量记录
     * @param height 测量的身高值
     * @return 是否保存成功
     */
    public boolean saveMeasurement(float height) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();

            // 获取当前日期
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String currentDate = dateFormat.format(new Date());

            values.put(COLUMN_DATE, currentDate);
            values.put(COLUMN_HEIGHT, height);

            // 插入新记录
            long result = db.insert(TABLE_MEASUREMENTS, null, values);
            return result != -1;
        } catch (Exception e) {
            Log.e(TAG, "保存测量记录失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取所有测量记录
     * @return 测量记录列表
     */
    public List<MeasurementRecord> getAllMeasurements() {
        List<MeasurementRecord> records = new ArrayList<>();

        try {
            String selectQuery = "SELECT * FROM " + TABLE_MEASUREMENTS + " ORDER BY " + COLUMN_DATE + " ASC";
            SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = db.rawQuery(selectQuery, null);

            if (cursor.moveToFirst()) {
                do {
                    String date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE));
                    float height = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_HEIGHT));

                    records.add(new MeasurementRecord(date, height));
                } while (cursor.moveToNext());
            }

            cursor.close();
        } catch (Exception e) {
            Log.e(TAG, "获取测量记录失败: " + e.getMessage());
        }

        return records;
    }

    /**
     * 删除所有测量记录
     * @return 是否删除成功
     */
    public boolean deleteAllMeasurements() {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.delete(TABLE_MEASUREMENTS, null, null);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "删除所有测量记录失败: " + e.getMessage());
            return false;
        }
    }
}