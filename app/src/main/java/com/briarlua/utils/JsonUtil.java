package com.briarlua.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class JsonUtil {

    private JsonUtil() {
    }

    public static Map<String, Object> parseObject(String jsonString) {
        try {
            JSONObject json = new JSONObject(jsonString);
            return jsonToMap(json);
        } catch (JSONException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    public static List<Map<String, Object>> parseArray(String jsonString) {
        try {
            JSONArray json = new JSONArray(jsonString);
            return jsonToList(json);
        } catch (JSONException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private static Map<String, Object> jsonToMap(JSONObject json) {
        Map<String, Object> map = new HashMap<>();
        if (json == null) return map;
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.opt(key);
            map.put(key, gsonToJson(value));
        }
        return map;
    }

    private static List<Map<String, Object>> jsonToList(JSONArray json) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (json == null) return list;
        for (int i = 0; i < json.length(); i++) {
            Object value = json.opt(i);
            if (value instanceof JSONObject) {
                list.add(jsonToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                Map<String, Object> item = new HashMap<>();
                item.put("array", jsonToList((JSONArray) value));
                list.add(item);
            } else {
                Map<String, Object> item = new HashMap<>();
                item.put("", gsonToJson(value));
                list.add(item);
            }
        }
        return list;
    }

    private static Object gsonToJson(Object value) {
        if (value instanceof JSONObject) {
            return jsonToMap((JSONObject) value);
        } else if (value instanceof JSONArray) {
            return jsonToList((JSONArray) value);
        }
        return value;
    }
}