package com.uts.cas.wifilocalizer;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

public class Reporter extends AsyncTask<String, String, Boolean> {

    @Override
    protected Boolean doInBackground(String... arg0) {

        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httppost = new HttpPost(arg0[0]);

        try {
            StringEntity stringEntity;
            String jsonString = arg0[1];
            stringEntity = new StringEntity(jsonString);

            httppost.setEntity(stringEntity);
            httppost.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");

            HttpResponse response;
            response = httpclient.execute(httppost);
            StatusLine statusLine = response.getStatusLine();

            if (statusLine.getStatusCode() == HttpStatus.SC_OK) {

                String strResponse = EntityUtils.toString(response.getEntity());

            } else {
                response.getEntity().getContent().close();
                throw new IOException(statusLine.getReasonPhrase());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }
}