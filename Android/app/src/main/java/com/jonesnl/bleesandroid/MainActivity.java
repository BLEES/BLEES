package com.jonesnl.bleesandroid;

import com.jonesnl.bleesandroid.BLEESScanRecord;

import android.app.ExpandableListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.http.AndroidHttpClient;
import android.os.Handler;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ProgressBar;
import android.widget.SimpleExpandableListAdapter;
import android.widget.Toast;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



public class MainActivity extends ExpandableListActivity implements BluetoothAdapter.LeScanCallback {
    private static final String TAG = "MainActivity";

    private static final String NAME = "NAME";
    private static final String VALUE = "VALUE";
    private static final int REQUEST_ENABLE_BT = 55;

    private SimpleExpandableListAdapter mAdapter;

    private BluetoothAdapter mBluetoothAdapter = null;

    private ArrayList<HashMap<String, String>> groupData;
    private ArrayList<ArrayList<HashMap<String, String>>> childData;

    private ExpandableListView mListView;

    private ProgressBar mProgress;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mProgress = (ProgressBar) findViewById(R.id.progress);
        mProgress.setVisibility(ProgressBar.INVISIBLE);

        final BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bm.getAdapter();

        //refreshBLEESList();

        // Still need to get data from Bluetooth and put it into lists

        groupData = new ArrayList<HashMap<String, String>>();
        childData = new ArrayList<ArrayList<HashMap<String, String>>>();
        // Set up our adapter
        mAdapter = new SimpleExpandableListAdapter(
                this,
                groupData,
                android.R.layout.simple_expandable_list_item_1,
                new String[] { NAME, VALUE },
                new int[] { android.R.id.text1, android.R.id.text2 },
                childData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] { NAME, VALUE },
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        setListAdapter(mAdapter);

        mListView = getExpandableListView();
    }

//    @Override
//    public void onRestoreInstanceState(Bundle savedBundle) {
//        super.onRestoreInstanceState(savedBundle);
//
//        groupData = (ArrayList<HashMap<String, String>>) savedBundle.getSerializable("GroupData");
//        childData = (ArrayList<ArrayList<HashMap<String, String>>>) savedBundle.getSerializable("ChildData");
//    }

    private Runnable mStopRunnable = new Runnable() {
        @Override
        public void run() {
            stopScan();
        }
    };
    private Runnable mStartRunnable = new Runnable() {
        @Override
        public void run() {
            startScan();
        }
    };

    private void startScan() {
        Log.v(TAG, "StartLeScan");
        groupData.clear();
        childData.clear();
        mAdapter.notifyDataSetChanged();
        mListView.invalidateViews();
        mProgress.setVisibility(ProgressBar.VISIBLE);
        mBluetoothAdapter.startLeScan(this);
        mHandler.postDelayed(mStopRunnable, 2500);
    }
    private void stopScan() {
        Log.v(TAG, "StopLeScan");
        mAdapter.notifyDataSetChanged();
        mListView.invalidateViews();
        mProgress.setVisibility(ProgressBar.INVISIBLE);
        mBluetoothAdapter.stopLeScan(this);
    }

    private Handler mHandler = new Handler();


    @Override
    public synchronized void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        BLEESScanRecord record;
        Log.v(TAG, "OnLeScan()");
        record = new BLEESScanRecord(scanRecord);

        if (!record.valid) return;

        DecimalFormat twoDecimal = new DecimalFormat("##");
        DecimalFormat fourDecimal = new DecimalFormat("####");

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        Boolean push = sharedPref.getBoolean(SettingsActivity.KEY_PUSH_DATA, false);
        String f_or_c = sharedPref.getString(SettingsActivity.KEY_TEMP_TYPE, "F");

        // Update existing record if it exists
        for (int i = 0; i < groupData.size(); ++i) {
            HashMap<String, String> map = groupData.get(i);
            if (map.get(NAME).equals(record.devName)) {
                map.put(NAME, record.devName);
                ArrayList<HashMap<String, String>> children = childData.get(i);
                for (HashMap<String, String> childMap : children) {
                    if (childMap.get(NAME).equals("Temp: ")) {
                        if (f_or_c.equals("F")) {
                            float tempF = record.temp * (9.0f/5.0f) + 32;
                            childMap.put(VALUE, twoDecimal.format(tempF) + "\u2109");
                        } else {
                            childMap.put(VALUE, twoDecimal.format(record.temp) + "\u2103");
                        }
                        // childMap.put(VALUE, String.format("%.1f", record.temp));
                    } else if (childMap.get(NAME).equals("Humidity: ")) {
                        childMap.put(VALUE, twoDecimal.format(record.humidity) + "%");
                        // childMap.put(VALUE, String.format("%.0f", record.humidity));
                    } else if (childMap.get(NAME).equals("Light: ")) {
                        childMap.put(VALUE, twoDecimal.format(record.light) + " lx");
                        //childMap.put(VALUE, String.format("%.0f", record.light));
                    } else if (childMap.get(NAME).equals("Pressure: ")) {
                        childMap.put(VALUE, fourDecimal.format(record.pressure) + " mbars");
                        //childMap.put(VALUE, String.format("%.1f", record.pressure));
                    } else {
                        Log.e(TAG, "Error parsing childMap");
                        return;
                    }
                }
                return;
            }
        }


        if (push) {
            new BLEESServerConn().execute(record);
        }

        // Create new record otherwise
        HashMap<String, String> curGroupMap = new HashMap<String, String>();
        groupData.add(curGroupMap);
        curGroupMap.put(NAME, record.devName);
        curGroupMap.put(VALUE, record.devName);

        ArrayList<HashMap<String, String>> children = new ArrayList<HashMap<String, String>>();

        HashMap<String, String> tempChild = new HashMap<String, String>();
        children.add(tempChild);
        tempChild.put(NAME, "Temp: ");
        if (f_or_c.equals("F")) {
            float tempF = record.temp * (9.0f/5.0f) + 32;
            tempChild.put(VALUE, twoDecimal.format(tempF) + "\u2109");
        } else {
            tempChild.put(VALUE, twoDecimal.format(record.temp) + "\u2103");
        }

        HashMap<String, String> humidityChild = new HashMap<String, String>();
        children.add(humidityChild);
        humidityChild.put(NAME, "Humidity: ");
        humidityChild.put(VALUE, twoDecimal.format(record.humidity) + "%");

        HashMap<String, String> lightChild = new HashMap<String, String>();
        children.add(lightChild);
        lightChild.put(NAME, "Light: ");
        lightChild.put(VALUE, twoDecimal.format(record.light) + " lx");

        HashMap<String, String> pressureChild = new HashMap<String, String>();
        children.add(pressureChild);
        pressureChild.put(NAME, "Pressure: ");
        pressureChild.put(VALUE, fourDecimal.format(record.pressure) + " mbars");

        childData.add(children);
        setProgressBarVisibility(true);
    }

    public void refreshBLEESList() {
        Log.v(TAG, "RefreshBLEESList()");

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        } else {
            final BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bm.getAdapter();
        }

        startScan();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
            return true;
        } else if (id == R.id.action_refresh) {
            refreshBLEESList();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == REQUEST_ENABLE_BT) {
            final BluetoothManager bm = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            mBluetoothAdapter = bm.getAdapter();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

//    @Override
//    public void onSaveInstanceState(Bundle savedInstanceState) {
//        savedInstanceState.putSerializable("GroupData", groupData);
//        savedInstanceState.putSerializable("ChildData", childData);
//
//        super.onSaveInstanceState(savedInstanceState);
//    }
}