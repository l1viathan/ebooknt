package org.ebookdroid.ui.library;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;

import org.sufficientlysecure.viewer.R;

public class RequestPermissionsActivity extends AppCompatActivity {

    private static final int REQUEST_LEGACY_STORAGE = 10;

    private boolean waitingForManagePermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            waitingForManagePermission = savedInstanceState.getBoolean("waitingForManagePermission", false);
        }
        if (!waitingForManagePermission) {
            checkAndRequestPermissions();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("waitingForManagePermission", waitingForManagePermission);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForManagePermission) {
            waitingForManagePermission = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                onSuccess();
            } else {
                onFailure();
            }
        }
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: need All Files Access for non-media files (PDF, epub, etc.)
            if (Environment.isExternalStorageManager()) {
                onSuccess();
            } else {
                showManageStorageRationale();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                onSuccess();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        REQUEST_LEGACY_STORAGE);
            }
        }
    }

    private void showManageStorageRationale() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.permission_manage_storage_rationale)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        openManageStorageSettings();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        onFailure();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void openManageStorageSettings() {
        waitingForManagePermission = true;
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LEGACY_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onSuccess();
            } else {
                onFailure();
            }
        }
    }

    private void onSuccess() {
        startActivity(new Intent(this, RecentActivity.class));
        finish();
    }

    private void onFailure() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.error_write_external_storage_permission)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                })
                .show();
    }
}
