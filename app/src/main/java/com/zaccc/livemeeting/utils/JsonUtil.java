package com.zaccc.livemeeting.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import org.webrtc.IceCandidate;


public class JsonUtil {

    public static final String CMD_TYPE = "cmdType";

    public static String login(String account, String password) {
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("cmdType", 101);

        JSONObject bodyObject = new JSONObject();
        bodyObject.put("account", account);
        bodyObject.put("password", password);

        jsonObject.put("body", bodyObject);

        return jsonObject.toString();
    }

    public static String signaling(String type, String desc, int uid) {
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("cmdType", 501);

        JSONObject bodyObject = new JSONObject();
        bodyObject.put("type", type);
        bodyObject.put("receivedUid", uid);
        bodyObject.put("desc", desc);

        jsonObject.put("body", bodyObject);

        return jsonObject.toString();
    }

    public static String candidate(IceCandidate candidate, int uid) {
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("cmdType", 501);

        JSONObject bodyObject = new JSONObject();
        bodyObject.put("type", "candidate");
        bodyObject.put("receivedUid", uid);
        bodyObject.put("label", candidate.sdpMLineIndex);
        bodyObject.put("id", candidate.sdpMid);
        bodyObject.put("candidate", candidate.sdp);

        jsonObject.put("body", bodyObject);

        return jsonObject.toString();
    }

    public static JSONObject parseString(String message) {
        return JSON.parseObject(message);
    }
}
