package com.zaccc.livemeeting.ui;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.alibaba.fastjson.JSONObject;
import com.zaccc.livemeeting.R;
import com.zaccc.livemeeting.config.Config;
import com.zaccc.livemeeting.listener.MessageListener;
import com.zaccc.livemeeting.signaling.SignalingClient;
import com.zaccc.livemeeting.utils.JsonUtil;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class LoginActivity extends AppCompatActivity implements View.OnClickListener, MessageListener {
    private final static int RC_CALL = 111;

    private String account, password = "1qaz2wsx";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        SignalingClient.get(this).connect();
    }

    @Override
    public void onMessage(String message) {
        if (message != null) {
            JSONObject object = JsonUtil.parseString(message);
            if (object.containsKey(JsonUtil.CMD_TYPE) && object.getInteger(JsonUtil.CMD_TYPE) == 101) {
                JSONObject body = object.getJSONObject("body");
                if (body.getInteger("status") == 1) {
                    Config.SEND_UID ^= body.getInteger("uid");
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    intent.putExtra("MY_UID", body.getInteger("uid"));

                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        SignalingClient.get(this).close();

        super.onDestroy();
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.login_info_1:
                account = "lanrich2003";
                break;
            case R.id.login_info_2:
                account = "lanrich2004";
                break;
            case R.id.login_info_3:
                account = "lanrich2005";
                break;
            default:
                account = null;
                break;
        }

        login();
    }

    private void login() {
        if (account == null) return;

        checkPermissionAndStart();
    }

    @AfterPermissionGranted(RC_CALL)
    private void checkPermissionAndStart() {
        String[] perms = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
        if (EasyPermissions.hasPermissions(this, perms)) {
            startLogin();
        } else {
            EasyPermissions.requestPermissions(this, "Need some permissions", RC_CALL, perms);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    private void startLogin() {
        SignalingClient.get(this).send(JsonUtil.login(account, password));
    }
}