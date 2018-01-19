package com.uts.cas.wifilocalizer;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.uts.cas.wifilocalizer.util.SupportOps;
import com.uts.cas.wifilocalizer.util.MatrixOps;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Localizer extends Activity {

    WifiManager wifi;
    WifiScanReceiver wifiReciever;
    Thread wifiThread;
    Thread stringUpdater;
    boolean proceed = false;
    boolean scanning = false;
    final Object lock = new Object();
    int prevCell = -1;
    int localCounter = 0;
    int minRSSI = -90;
    int maxRSSI = -30;
    double entropyThresh = 5.4;
    List<ScanResult> wifiScanList;
    int wifiCount = 0;
    ArrayList<ArrayList<String[]>> kernelDists;
    List<String[]> celldefs;
    List<String[]> transitions;
    double floorTransThresh = 2.0;
    boolean firstScan = true;
    List<String> APs;
    double[] globalPriorProbs = null;
    double[] floorHist = null;
    List<Double> altWindow;
    int altWindowSize = 5;
    int cellCount = 0;
    float height = 0.0f;
    boolean decayHist = false;
    double decayScale = 0.9;
    double noDecayScale = 0.6;
    SensorManager mSensorManager = null;
    public String SERVICE_PATH = "http://172.19.131.103:8080/datacol/rssi/datacollector/setData";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_localizer);

        celldefs = SupportOps.loadCSVFile("cells.csv");
        cellCount = celldefs.size();

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE),
                SensorManager.SENSOR_DELAY_NORMAL);

        APs = SupportOps.loadAPFile("selectedAPs.txt");
        kernelDists = SupportOps.loadKernelFile("kernel_model.csv", APs, cellCount);
        transitions = SupportOps.loadCSVFile("transitions.csv");
        loadPropertyFile("altimeter_properties.txt");

        globalPriorProbs = new double[cellCount];
        floorHist = new double[]{1.0,1.0};

        altWindow = new ArrayList<Double>();

        wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifiReciever = new WifiScanReceiver();
    }

    SensorEventListener mSensorListener = new SensorEventListener() {

        float pressureValue = 0.0f;

        @Override
        public void onSensorChanged(SensorEvent event) {
            if(Sensor.TYPE_PRESSURE == event.sensor.getType() ){
                pressureValue = event.values[0];
                height = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                        pressureValue);

                double heightD = (double) height;

                if(altWindow.size() < altWindowSize) {
                    altWindow.add(heightD);
                }
                else {
                    altWindow.remove(0);
                    altWindow.add(heightD);

                    double avgAlt = MatrixOps.getAvg(altWindow);

                    if(decayHist){

                        int maxIdx = MatrixOps.getLargestIdx(floorHist);

                        if (heightD - avgAlt > floorTransThresh) {
                            // goes up
                            // here it assumes that we are dealing with only two floors
                            // the synchronization is done, because now the wifi listener also can
                            // can update the floorHist ayyar with synchronize blocks

                            if (maxIdx == 0) {
                                synchronized (floorHist) {
                                    floorHist[0] = floorHist[0] * (1 - decayScale);
                                    floorHist[1] = floorHist[1] * decayScale;
                                }

                            }
                        } else if (avgAlt - heightD > floorTransThresh) {
                            // goes down
                            if (maxIdx == 1) {
                                synchronized (floorHist) {
                                    floorHist[0] = floorHist[0] * decayScale;
                                    floorHist[1] = floorHist[1] * (1-decayScale);
                                }
                            }
                        } else {
                            // same floor - decay equally
                            synchronized (floorHist) {
                                floorHist[0] = floorHist[0] * noDecayScale;
                                floorHist[1] = floorHist[1] * noDecayScale;
                            }
                        }
                    }
                }
            }
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
            // when accuracy is changed this method will be called
        }
    };

    protected void onResume() {
        registerReceiver(wifiReciever, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        super.onResume();
    }

    protected void onPause() {
        unregisterReceiver(wifiReciever);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_localizer, menu);
        return true;
    }

    private class WifiScanReceiver extends BroadcastReceiver {
        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        public void onReceive(Context c, Intent intent) {

            synchronized(lock) {
                wifiScanList = wifi.getScanResults();
            }
            if(proceed){
                wifi.startScan();
            }

            else{
                scanning = false;
                localCounter = 0;
                Toast.makeText(getApplicationContext(), "Scanning is stopped", Toast.LENGTH_SHORT).show();
                Button myButton = (Button) findViewById(R.id.button);
                myButton.setText("Start");
            }
        }
    }

    public void onStartStop(View v){

        EditText IPText = (EditText) findViewById(R.id.editText1);
        SERVICE_PATH = "http://" + IPText.getText().toString() + ":8080/locmediator/loc/localizer/setLocResults";
        //SERVICE_PATH = "http://demo2137091.mockable.io/gettemp"; // this only temporary

        if(!scanning){

            proceed = true;

            wifi.startScan();

            stringUpdater = new Thread(new Runnable() {
                @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
                public void run() {
                    while (true) {
                        if(null != wifiScanList){
                            localCounter = localCounter + 1;

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    TextView count = (TextView) findViewById(R.id.textView);
                                    count.setText("Current Scan number : " + localCounter);
                                }
                            });

                            double[] locationEsimates = getLocationEstimates();

                            toast(String.valueOf(MatrixOps.getLargestIdx(locationEsimates) + 1), 10);

                            JSONArray jsonArray = SupportOps.createJSON(locationEsimates);
                            new Reporter().execute(new String[]{SERVICE_PATH, jsonArray.toString()});

                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException e) {
                                return;
                            }
                        }
                    }
                }
            });
            stringUpdater.start();

            TextView count = (TextView) findViewById(R.id.textView);
            count.setText("Current Scan number : " + localCounter);

            scanning = true;
            Button myButton = (Button) findViewById(R.id.button);
            myButton.setText("Stop");
        }
        else{
            stringUpdater.interrupt();
            proceed = false;
        }
    }

    @Override
    public void onDestroy() {
        if(stringUpdater != null){
            stringUpdater.interrupt();
        }
        if(wifiThread != null){
            wifiThread.interrupt();
        }
        super.onDestroy();
    }

    private void toast(final String text, final int duration) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), text, duration).show();
            }
        });
    }

    private void loadPropertyFile(String propFileName){

        String configurationFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) +
                File.separator + propFileName;

        File file = new File(configurationFile);

        FileInputStream fis;
        BufferedReader reader;

        try {
            if (file.exists()) {
                fis = new FileInputStream(file);
                reader = new BufferedReader(new InputStreamReader(fis));
                try {
                    String line = reader.readLine();
                    while (line != null) {

                        String[] prop = line.split("=");
                        String propField =  prop[0].trim();

                        try {
                            if (propField.equals("decay") ) {
                                decayScale = Double.parseDouble(prop[1].trim());

                            } else if (propField.equals("No_decay")) {
                                noDecayScale = Double.parseDouble(prop[1].trim());

                            } else if (propField.equals("Alt_window")) {
                                altWindowSize = Integer.parseInt(prop[1].trim());

                            } else if (propField.equals("Floor_transition_threshold")) {
                                floorTransThresh = Double.parseDouble(prop[1].trim());

                            } else if (propField.equals("Entropy_threshold")){
                                entropyThresh = Double.parseDouble(prop[1].trim());
                            }
                        }
                        catch(NumberFormatException e){
                            // defaults
                            decayScale = 0.9;
                            noDecayScale = 0.6;
                            altWindowSize = 5;
                            floorTransThresh = 2.0;
                            entropyThresh = 5.4;
                        }

                        line = reader.readLine();
                    }
                }
                finally{
                    fis.close();
                    reader.close();
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private double[] getLocationEstimates(){

        List<Integer> wifiScans = null;

        double[] priorProbs = globalPriorProbs;

        //double[] entPrior = new double[cellCount];
        //double entUnifVal = 1.0/cellCount;
        //Arrays.fill(entPrior, entUnifVal);

        if(firstScan) {
            double unifVal = 1.0/cellCount;
            Arrays.fill(priorProbs, unifVal);
            firstScan = false;
        }
        else{
            priorProbs = MatrixOps.addDouble(priorProbs, 0.1);
            priorProbs = MatrixOps.normalizeArray(priorProbs);
        }

        priorProbs = MatrixOps.naturalLog(priorProbs);

        //entPrior = MatrixOps.naturalLog(entPrior);

        double[] bestCellCoords = null;

        if(wifiScanList != null) {

            synchronized (lock) {
                wifiScans = SupportOps.sortWifiScans(wifiScanList, APs);
            }

            int goodAPID = -1;

            for(int i = 0 ; i < wifiScans.size() ; i++){

                goodAPID++;

                if((wifiScans.get(i).intValue() == -120) || (wifiScans.get(i).intValue() < minRSSI)
                        || (wifiScans.get(i).intValue() > maxRSSI)){

                    continue;
                }
                else{

                    int observation = wifiScans.get(i).intValue();

                    double[] likelihoods = getLikelihood(goodAPID, observation);
                    double[] logLikelihoods = MatrixOps.naturalLog(likelihoods);
                    double[] posteriorProbs = MatrixOps.sumArrays(priorProbs, logLikelihoods);
                    //double[] entPost = MatrixOps.sumArrays(entPrior, logLikelihoods);
                    priorProbs = posteriorProbs;
                    //entPrior = entPost;
                }
            }

            priorProbs = MatrixOps.inversesNaturalLog(priorProbs);
            priorProbs = MatrixOps.normalizeArray(priorProbs);
            //entPrior = MatrixOps.inversesNaturalLog(entPrior);
            //entPrior = MatrixOps.normalizeArray(entPrior);

            wifiCount++;

            if(wifiCount == 5){
                decayHist = true;
                wifiCount = -100;
            }

            priorProbs = altMotionLogic(priorProbs);
            priorProbs = directMotionModel(priorProbs);
            priorProbs = MatrixOps.normalizeArray(priorProbs);
            //priorProbs = getInformativeDist(entPrior, priorProbs);
            globalPriorProbs = priorProbs;

            prevCell = MatrixOps.getLargestIdx(priorProbs);
            //bestCellCoords = getCoords(priorProbs);
        }
        else{
            // write something
        }

        return priorProbs;
    }

    private double[] getLikelihood(int ap, int signal){

        double[] likelihoods = new double[cellCount];
        int indexpb = signal + (-1 * minRSSI);

        for(int i = 0 ; i < cellCount ; i++){
            likelihoods[i] = Double.parseDouble(kernelDists.get(i).get(ap)[indexpb]);
        }
        return likelihoods;
    }

    private double[] altMotionLogic(double[] inProbs){

        double[] outProbs = new double[inProbs.length];
        int maxIdx = MatrixOps.getLargestIdx(inProbs);
        int currFloor =  Integer.parseInt(celldefs.get(maxIdx)[3]);
        int currFloorIdx = currFloor - 1;

        floorHist[currFloorIdx] = floorHist[currFloorIdx] + 0.5;
        floorHist = MatrixOps.normalizeArray(floorHist);

        for(int i = 0 ; i < inProbs.length ; i++){
            outProbs[i] = inProbs[i] * floorHist[Integer.parseInt(celldefs.get(i)[3]) - 1];
        }
        return outProbs;
    }

    private double[] directMotionModel(double[] inProbs){

        if (prevCell == -1){
            return inProbs;
        }

        double[] outProbs = new double[inProbs.length];

        for(int i = 0 ; i < inProbs.length ; i++){
            outProbs[i] = inProbs[i] * Double.parseDouble(transitions.get(prevCell)[i]);
        }
        return outProbs;
    }

    /*
    private double[] getInformativeDist(double[] interPrior, double[] motionPrior){

        double entropy = 0;
        for(int i = 0 ; i < motionPrior.length ; i++){

            if(motionPrior[i] > 0.0){
                double logVal = Math.log(motionPrior[i]) / Math.log(2);
                entropy = entropy + ( ( -1 * motionPrior[i] ) * logVal );
            }
        }
        if(entropy < entropyThresh){
            return interPrior;
        }

        return motionPrior;
    }
    */

    /*
    private double[] getCoords(double[] probs){

        double[] coords = new double[3];

        int bestCell = MatrixOps.getLargestIdx(probs);

        coords[0] = Double.parseDouble(celldefs.get(bestCell)[1]);
        coords[1] = Double.parseDouble(celldefs.get(bestCell)[2]);
        coords[2] = Double.parseDouble(celldefs.get(bestCell)[3]);

        return coords;
    }
    */

}
