package com.uts.cas.wifilocalizer.util;

import android.util.Log;

import java.util.List;

/**
 * Created by 99168130 on 14/11/2017.
 */

public final class MatrixOps {

    private MatrixOps(){}

    public static double getAvg(List<Double> inList){

        double sum = 0.0;
        for(double alttd : inList){
            sum = sum + alttd;
        }
        double avg = sum / inList.size();
        return avg;
    }

    public static int getLargestIdx(double[] inArr){

        int largestIdx = 0;
        double largestVal = inArr[0];

        for(int i = 0 ; i < inArr.length ; i++ ){
            if(inArr[i] > largestVal){
                largestVal = inArr[i];
                largestIdx = i;
            }
        }
        return largestIdx;
    }

    public static double[] naturalLog(double[] inArray){

        double[] outArray = new double[inArray.length];
        boolean zeroCapt = false;

        for(int i = 0 ; i < inArray.length ; i++) {
            if (inArray[i] == 0.0) {
                zeroCapt = true;
                break;
            }
        }
        if(zeroCapt) {
            for (int i = 0; i < inArray.length; i++) {
                inArray[i] = inArray[i] + 0.000000001;
            }
        }
        inArray = normalizeArray(inArray);

        for(int i = 0 ; i < inArray.length ; i++) {
            double logVal = Math.log(inArray[i]);
            outArray[i] = logVal;
        }
        return outArray;
    }

    public static double[] normalizeArray(double[] inArray){

        double[] outArray = new double[inArray.length];
        double sumArr = 0;

        for(int i = 0 ; i < inArray.length ; i++){
            sumArr = sumArr + inArray[i];
        }
        for(int i = 0 ; i < inArray.length ; i++){
            outArray[i] = inArray[i] / sumArr;
        }
        return outArray;
    }

    public static double[] inversesNaturalLog(double[] inArray){
        double[] outArray = new double[inArray.length];

        boolean nanCaptured = false;
        for(int i = 0 ; i < inArray.length ; i++){
            outArray[i] = Math.exp(inArray[i]);

        }
        return outArray;
    }

    public static double[] sumArrays(double[] inArrOne, double[] inArrTwo){

        double[] outArray = new double[inArrOne.length];

        if(inArrOne.length == inArrTwo.length){
            for(int i = 0 ; i < inArrOne.length ; i++){
                outArray[i] = inArrOne[i] + inArrTwo[i];
            }
        }
        else{
            // error
            Log.e("ARROP", "Arrays lengths mismatch");
        }
        return outArray;
    }

    public static double[] addDouble(double[] inArray, double val){
        double[] outArray = new double[inArray.length];

        for(int i = 0 ; i < inArray.length ; i++){
            outArray[i] = inArray[i] + val;
        }
        return outArray;
    }

}
