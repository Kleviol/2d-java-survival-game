package com.citysurvival.core.supabase;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;

public class CloudSaveService {
    private final SupabaseClient client;
    private final Gson gson = new Gson();

    public CloudSaveService(SupabaseClient client) {
        this.client = client;
    }

    public void uploadSave(String playerId, int slot, String saveJson) throws IOException {
        JsonObject row = new JsonObject();
        row.addProperty("player_id", playerId);
        row.addProperty("slot", slot);
        row.add("save_json", gson.fromJson(saveJson, com.google.gson.JsonElement.class));

        client.upsert("game_save", gson.toJson(row));
    }

    public String downloadSaveJson(String playerId, int slot) throws IOException {
        String q = "?player_id=eq." + playerId + "&slot=eq." + slot + "&select=save_json&limit=1";
        String resp = client.select("game_save", q);

        JsonArray arr = gson.fromJson(resp, JsonArray.class);
        if (arr == null || arr.size() == 0) return null;

        JsonObject row = arr.get(0).getAsJsonObject();
        return row.get("save_json").toString();
    }
}
