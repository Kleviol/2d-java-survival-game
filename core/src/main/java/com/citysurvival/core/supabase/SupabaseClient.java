package com.citysurvival.core.supabase;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class SupabaseClient {
    private final OkHttpClient http = new OkHttpClient();
    private final String url;
    private final String apiKey;

    public SupabaseClient(String url, String apiKey) {
        this.url = url;
        this.apiKey = apiKey;
    }

    public String upsert(String table, String jsonBody) throws IOException {
        String endpoint = url + "/rest/v1/" + table;

        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response resp = http.newCall(request).execute()) {
            ResponseBody body = resp.body();
            String bodyString = body != null ? body.string() : "";
            if (!resp.isSuccessful()) {
                throw new IOException("Supabase error " + resp.code() + ": " + bodyString);
            }
            return bodyString;
        }
    }

    public String select(String table, String queryString) throws IOException {
        String endpoint = url + "/rest/v1/" + table + (queryString == null ? "" : queryString);

        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        try (Response resp = http.newCall(request).execute()) {
            ResponseBody body = resp.body();
            String bodyString = body != null ? body.string() : "";
            if (!resp.isSuccessful()) {
                throw new IOException("Supabase error " + resp.code() + ": " + bodyString);
            }
            return bodyString;
        }
    }
}
