package pervasive.jku.at.wifisensor;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.IOException;

import org.fusesource.hawtbuf.AsciiBuffer;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtbuf.DataByteArrayInputStream;
import org.fusesource.hawtbuf.DataByteArrayOutputStream;

import pervasive.jku.at.wifisensor.comm.CommunicationListener;
import pervasive.jku.at.wifisensor.comm.CommService;
import pervasive.jku.at.wifisensor.wifi.WifiScanEvent;
import pervasive.jku.at.wifisensor.wifi.WifiScanListener;
import pervasive.jku.at.wifisensor.wifi.WifiService;

public class WiFiActivity extends ActionBarActivity implements WifiScanListener, CommunicationListener {

    private static final String TAG_REG="reg";
    private static final String TAG_IOT="iot";
    private static final String TAG_SEN="sen";
    private static final String TAG_OTH="oth";

    private static final String WIFI_SENSOR_NAME="WiFi RSSi Sensor";
    private static final String COMM_SENSOR_NAME="Comm Sensor";

    private Sensor mSensor;
    private boolean wifiBounded;
    private WifiService wifiService;
    private CommService commService;
    private boolean commBounded;
    private ServiceConnection commServiceConnection;
    private ServiceConnection wifiServiceConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wifi_sensor);

        ToggleButton toggleButton=(ToggleButton)findViewById(R.id.toggle_wifi_scan);
        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                                                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                                        if (isChecked) {
                                                            Intent mIntent = new Intent(WiFiActivity.this, WifiService.class);
                                                            Log.d(TAG_OTH, "starting WifiService");
                                                            startService(mIntent);
                                                        } else {
                                                            Intent mIntent = new Intent(WiFiActivity.this, WifiService.class);
                                                            Log.d(TAG_OTH, "stopping WifiService");
                                                            unregisterWifi();
                                                        }
                                                    }
                                                }
        );
        Log.d(TAG_OTH, "on create");
        if(!wifiBounded) {
            bindWifiService();
        }
        if(!commBounded) {
            bindCommunicationService();
        }
    }

    private void bindCommunicationService(){
        Log.d(TAG_OTH, "binding commService");
        Intent mIntent = new Intent(this, CommService.class);
        commServiceConnection = new ServiceConnection() {

            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG_OTH, "service " + name.toShortString() + " is disconnected");
                commBounded = false;
                commService = null;
            }

            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG_OTH, "service " + name.toShortString() + " is connected");
                commBounded = true;
                CommService.LocalBinder mLocalBinder = (CommService.LocalBinder) service;
                commService = mLocalBinder.getServerInstance();
                registerComm();
            }
        };
        bindService(mIntent, commServiceConnection, BIND_AUTO_CREATE);
        startService(mIntent);

    }

    private void bindWifiService(){
        Log.d(TAG_OTH, "binding wifiService");

        Intent mIntent = new Intent(this, WifiService.class);
        wifiServiceConnection = new ServiceConnection() {

            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG_OTH, "service " + name.toShortString() + " is disconnected");
                wifiBounded = false;
                wifiService = null;
            }

            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG_OTH, "service " + name.toShortString() + " is connected");
                wifiBounded = true;
                WifiService.LocalBinder mLocalBinder = (WifiService.LocalBinder) service;
                wifiService = mLocalBinder.getServerInstance();
                registerWifi();
                ((ToggleButton)findViewById(R.id.toggle_wifi_scan)).setChecked(wifiService.isScanning());
            }
        };
        bindService(mIntent, wifiServiceConnection, BIND_AUTO_CREATE);
    }

        @Override
    public void onWifiChanged(WifiScanEvent event) {
        Log.d(TAG_SEN, "sensor event received from " + WIFI_SENSOR_NAME + " " + event.getMAC());
        TextView tc=(TextView)findViewById(R.id.sensorContent);
        StringBuffer sb=new StringBuffer();
        for(ScanResult data:event.getResult()) {
           sb.append(data.BSSID + "/" + data.level + ", ");
        }
        tc.setText(sb.toString());
    }

    @Override
    protected void onDestroy() {
        unbindService(commServiceConnection);
        unbindService(wifiServiceConnection);
        Log.d(TAG_OTH, "on destroy");
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG_OTH, "on resume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG_OTH, "on pause");
    }

    @Override
    public void messageReceived(String topic, Buffer content) {
        Log.d(TAG_IOT, "message with size " + content.length() + " received from topic: " + topic);
        DataByteArrayInputStream bais = new DataByteArrayInputStream(content);
        int macSize = bais.readInt();
        AsciiBuffer buffer=bais.readBuffer(macSize).ascii();
        final String mac=buffer.toString();
        if(!((WifiManager)getSystemService(WIFI_SERVICE)).getConnectionInfo().getMacAddress().equals(mac)) {
            final StringBuffer sb = new StringBuffer();
            int size = bais.readInt();
            for (int i = 0; i < size; i++) {
                long lMac = bais.readLong();
                int rssi = bais.readInt();
                String sMac = Long.toHexString(lMac);
                sb.append(sMac);
                sb.insert(sb.length() - 10, ':');
                sb.insert(sb.length() - 8, ':');
                sb.insert(sb.length() - 6, ':');
                sb.insert(sb.length() - 4, ':');
                sb.insert(sb.length() - 2, ':');
                sb.append("/");
                sb.append(rssi);
                sb.append(", ");
            }
            Log.d(TAG_IOT, "message received from " + mac + " in topic: " + topic + " with payload " + sb.toString());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView tc = (TextView) findViewById(R.id.otherSensorContent);
                    tc.setText(sb.toString());
                    TextView tn = (TextView) findViewById(R.id.otherSensorName);
                    tn.setText(getResources().getString(R.string.sensing_of, mac));
                }
            });
        } else {
            Log.d(TAG_IOT, "message received from myself (" + mac + ") in topic: " + topic + " ignored.");
        }
    }

    private WifiScanListener wifiToCommRelay = new WifiScanListener() {
        @Override
        public void onWifiChanged(WifiScanEvent event) {
            Log.d(TAG_IOT, "wifiCommRelay called");
            try {
                DataByteArrayOutputStream baos = new DataByteArrayOutputStream();
                AsciiBuffer header = new AsciiBuffer(event.getMAC());
                baos.writeInt(header.length);
                baos.write(header);
                //tuple size
                baos.writeInt(event.getResult().size());
                for(ScanResult data:event.getResult()) {
                    baos.writeLong(Long.parseLong(data.BSSID.replaceAll(":", ""), 16));
                    baos.writeInt(data.level);
                }
                if(commService!=null) {
                    commService.sendMessage("sensor", baos.toBuffer());
                } else {
                    Log.e(TAG_IOT, "comm not available");
                }
            } catch (IOException e) {
                Log.e(TAG_IOT, "error preparing message", e);
            }
        }
    };

    private void registerWifi() {
        Log.d(TAG_REG, "registering " + WIFI_SENSOR_NAME);
        wifiService.registerListener(this);
        wifiService.registerListener(wifiToCommRelay);
    }

    private void registerComm(){
        Log.d(TAG_REG, "registering " + COMM_SENSOR_NAME);
        commService.registerListener(this);
        commService.addTopic("sensor");
    }

    private void unregisterComm(){
        Log.d(TAG_REG, "unregistering " + COMM_SENSOR_NAME);
        commService.unregisterListener(this);
        commService.removeTopic("sensor");
    }

    private void unregisterWifi(){
        if(wifiService !=null) {
            Log.d(TAG_REG, "unregistering " + WIFI_SENSOR_NAME);
            wifiService.unregisterListener(this);
            wifiService.unregisterListener(wifiToCommRelay);
            wifiService.stopScanning();
        }
    }
}
