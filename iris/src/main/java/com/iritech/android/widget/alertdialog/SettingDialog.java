package com.iritech.android.widget.alertdialog;

import android.app.Dialog;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.app.AlarmManager;
import android.os.Bundle;

import android.content.Intent;

import com.iritech.iris.LicenseInfo;
import com.iritech.iris.Settings;
import com.iritech.iris.R;
import com.iritech.android.TextValidator;

import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import android.content.pm.PackageManager;
import android.content.DialogInterface;

public class SettingDialog extends Dialog {
    private boolean mNeedRestart;
    private EditText mCameraPreviewScale;

    private CheckBox mFlipImages;
    private EditText mCustomerID;
    private EditText mLicenseID;

    public SettingDialog(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setting_dialog);

        Button ButtonCancel = findViewById(R.id.bt_setting_cancel);
        ButtonCancel.setOnClickListener(new View.OnClickListener(){
                                            @Override
                                            public void onClick(View v) {
                                                //Close dialog
                                                dismiss();
                                            }
                                        }
        );

        Button ButtonOK = findViewById(R.id.bt_setting_save);
        ButtonOK.setOnClickListener(new View.OnClickListener(){
                                            @Override
                                            public void onClick(View v) {
                                                if(ValidateSettings()) {
                                                    SaveSettings();

                                                    if (mNeedRestart){
                                                        notifyAndRestartApplication();
                                                    }

                                                    //Close dialog
                                                    dismiss();
                                                }
                                            }
                                        }
        );

        // Camera preview scale (landscape)
        mCameraPreviewScale = findViewById(R.id.edt_cam_preview_scale);
        if (mCameraPreviewScale != null){
            mCameraPreviewScale.setText(String.valueOf(Settings.getCameraPreviewScale(getContext())));

            mCameraPreviewScale.addTextChangedListener(new TextValidator(mCameraPreviewScale) {
                @Override public void validate(TextView textView, String text) {
                    if (text.isEmpty()) {
                        Toast.makeText(getContext(), "Please enter scale value", Toast.LENGTH_SHORT).show();
                        ButtonOK.setEnabled(false);
                    }
                    else {
                        int scaleVal = Integer.parseInt(text);
                        if (scaleVal <= 0 || scaleVal > 100){
                            Toast.makeText(getContext(), "Scale value must be between [1; 100]", Toast.LENGTH_SHORT).show();
                            ButtonOK.setEnabled(false);
                        }
                        else{
                            ButtonOK.setEnabled(true);
                        }
                    }
                }
            });
        }

        mFlipImages = findViewById(R.id.flip_images_id);
        if (mFlipImages != null) {
            mFlipImages.setChecked(Settings.getCameraFlipImages(getContext()));
            mFlipImages.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    ButtonOK.setEnabled(true);
                }
            });
        }

        // License information
        mCustomerID = findViewById(R.id.edit_setting_customer_id);
        mLicenseID = findViewById(R.id.edit_setting_license_id);
        if(Settings.getLicenseEntered(getContext())){
            mCustomerID.setText(Settings.getCustomerID(getContext()));
            mLicenseID.setText(Settings.getLicenseID(getContext()));
        }
    }

    private void notifyAndRestartApplication(){
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Setting changed");
        builder.setMessage("The application need to restart for changes to take effect");
        builder.setPositiveButton("Restart now", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                doRestart(getContext());
            }
        });

        builder.setCancelable(false);
        builder.show();
    }

    private void doRestart(Context c) {
        try {
            if (c != null) {
                // fetch the packagemanager so we can get the default launch activity
                PackageManager pm = c.getPackageManager();
                if (pm != null) {
                    // create the intent with the default start activity for your application
                    Intent mStartActivity = pm.getLaunchIntentForPackage(c.getPackageName());
                    if (mStartActivity != null) {
                        mStartActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        // create a pending intent so the application is restarted after System.exit(0) was called.
                        // use an AlarmManager to call this intent in 100ms
                        PendingIntent mPendingIntent = PendingIntent.getActivity(c, 0, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);

                        AlarmManager mgr = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
                        mgr.set(AlarmManager.RTC, 0, mPendingIntent);

                        //kill the application
                        System.exit(0);
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private boolean ValidateSettings(){
        if (!ValidateLicense()){
            return false;
        }
        return true;
    }

    private boolean ValidateLicense(){
        String customerID = mCustomerID.getText().toString();
        String licenseID = mLicenseID.getText().toString();

        if (customerID.isEmpty() || licenseID.isEmpty()){
            Toast.makeText(getContext(), "Please input both CustomerID and LicenseID", Toast.LENGTH_SHORT).show();
            return false;
        }

        String curCustomerID = Settings.getCustomerID(getContext());
        String curLicenseID = Settings.getLicenseID(getContext());

        // If current license is empty, accept new the one
        if (curCustomerID.isEmpty() || curLicenseID.isEmpty()){
            return true;
        }

        // Check restart if license changed
        if (!customerID.equals(curCustomerID) || !licenseID.equals(curLicenseID)){
            mNeedRestart = true;
            Settings.setRequestNewLicense(getContext(), true);
        }

        return true;
    }

    private void SaveSettings(){
        // Save Camera preview scale
        Settings.setCameraPreviewScale(getContext(), Integer.parseInt(mCameraPreviewScale.getText().toString()));

        Settings.setCameraFlipImages(getContext(), mFlipImages.isChecked());

        // Save license information
        String customerID = mCustomerID.getText().toString();
        String licenseID = mLicenseID.getText().toString();
        Settings.setCustomerID(getContext(), customerID);
        Settings.setLicenseID(getContext(),  licenseID);
        Settings.setLicenseEntered(getContext(), true);
        // Initialize license information
        LicenseInfo.getInstance().initialize(getContext());
    }
}
