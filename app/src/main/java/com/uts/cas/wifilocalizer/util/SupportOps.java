package com.uts.cas.wifilocalizer.util;

import android.net.wifi.ScanResult;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.opencsv.CSVReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by 99168130 on 14/11/2017.
 */

public final class SupportOps {

    private SupportOps(){}

    public static JSONArray createJSON(double[] locEstimates){

        JSONArray jsonArray = new JSONArray();

        for(int i = 0 ; i < locEstimates.length ; i++) {

            JSONObject jsonObject = new JSONObject();

            try {
                jsonObject.put("cellID", i);
                jsonObject.put("probability", locEstimates[i]);

            } catch (JSONException e) {
                e.printStackTrace();
            }
            jsonArray.put(jsonObject);
        }
        Log.e("JSONG", jsonArray.toString());
        return jsonArray;
    }

    public static List<String> loadAPFile(String APFileName){
        List<String> APs = new ArrayList<String>();

        String configurationFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) +
                File.separator + APFileName;

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
                        APs.add(line);
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

        return APs;
    }

    public static ArrayList<ArrayList<String[]>> loadKernelFile(String kFilename, List<String> APs, int cellCount){

        List<String[]> kernels = new ArrayList<String[]>();
        String configurationFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) +
                File.separator + kFilename;

        try {
            FileInputStream finstream = new FileInputStream(configurationFile);
            InputStreamReader csvStreamReader = new InputStreamReader(finstream);
            CSVReader csvReader  = new CSVReader(csvStreamReader);

            try {
                String[] line;
                while ((line = csvReader.readNext()) != null) {
                    kernels.add(line);
                }
            }
            finally{
                finstream.close();
                csvStreamReader.close();
                csvReader.close();
            }
        } catch (Exception e) {
            Log.e("SOMETHING", "Configuration error: " + e.getMessage());
        }

        ArrayList<ArrayList<String[]>> orgKernels = new ArrayList<ArrayList<String[]>>();

        int APCount = APs.size();

        for(int i = 0 ; i < cellCount ; i++){
            ArrayList<String[]> intermList = new ArrayList<String[]>();

            for(int j = 0 ; j < APCount ; j++){
                String[] kernelContent = kernels.get(0);
                kernels.remove(0);

                kernelContent = Arrays.copyOfRange(kernelContent, 2, kernelContent.length);
                intermList.add(kernelContent);
            }
            orgKernels.add(intermList);
        }
        return orgKernels;
    }

    public static List<String[]> loadCSVFile(String cFilename){

        List<String[]> cells = new ArrayList<String[]>();
        String configurationFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) +
                File.separator + cFilename;

        try {
            FileInputStream finstream = new FileInputStream(configurationFile);
            InputStreamReader csvStreamReader = new InputStreamReader(finstream);
            CSVReader csvReader  = new CSVReader(csvStreamReader);

            try {
                String[] line;
                while ((line = csvReader.readNext()) != null) {
                    cells.add(line);
                }
            }
            finally{
                finstream.close();
                csvStreamReader.close();
                csvReader.close();
            }
        } catch (Exception e) {
            Log.e("SOMETHING", "Configuration error: " + e.getMessage());
        }
        return cells;
    }

    public static List<Integer> sortWifiScans(List<ScanResult> wifiScans, List<String> APs){

        List<Integer> sortedList = new ArrayList<Integer>();

        for (String goodAP : APs) {

            String goodAPL = goodAP.toLowerCase();
            boolean foundAP = false;

            for(ScanResult scan : wifiScans) {

                if(goodAPL.trim().toLowerCase().contains(scan.BSSID.toLowerCase())) {
                    foundAP = true;
                    sortedList.add(scan.level);
                    break;
                }
            }
            if(!foundAP) {
                sortedList.add(-120);
            }
        }

        return sortedList;
    }
}
