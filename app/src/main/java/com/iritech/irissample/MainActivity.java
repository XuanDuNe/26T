package com.iritech.irissample;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.iritech.android.widget.alertdialog.BestImageDialog;
import com.iritech.android.widget.alertdialog.SettingDialog;
import com.iritech.android.widget.alertdialog.RegisterLicenseDialog;
import com.iritech.iris.CaptureActivity;
import com.iritech.iris.Constants;
import com.iritech.iris.LicenseInfo;
import com.iritech.iris.Utilities;
import com.iritech.mqel704.GemResult;
import com.iritech.mqel704.ImageData;
import com.iritech.mqel704.ImageFormat;
import com.iritech.mqel704.ImageKind;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private static final int PERMISSIONS_REQUEST_CAMERA = 2;
    private static final int PERMISSIONS_READ_PHONE_STATE = 3;
    private static final int PERMISSIONS_ACCESS_NETWORK_STATE = 4;

    String enrollImgPath = (Environment.getExternalStorageDirectory().toString() + File.separator
            + "iritech" + File.separator + "enroll");
    String verifyImgPath = (Environment.getExternalStorageDirectory().toString() + File.separator
            + "iritech" + File.separator + "verify");
    String mUserId;
    private int REQUEST_CODE_IDENTIFY = 1111;
    private int REQUEST_CODE_CAPTURE = 1112;
    private int REQUEST_CODE_ENROLL = 1113;
    private int REQUEST_CODE_VERIFY = 1114;
    private int REQUEST_CODE_UNENROLL = 1115;
    private EditText editUserId;
    PowerManager.WakeLock wl;

    private int mResultCode;

    private String mAction;

    private int mRequestCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        editUserId = findViewById(R.id.edit_user_id);

        CaptureActivity.setUSBActivity(this);

        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
            String version = pInfo.versionName;
            TextView tvVersion = findViewById(R.id.tv_version);
            tvVersion.setText("Version: " + version);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        Button btnEnroll = findViewById(R.id.bt_enroll);
        btnEnroll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAction = Constants.ACTION_ENROLL;
                mRequestCode = REQUEST_CODE_ENROLL;
                startCaptureActivity(Constants.ACTION_ENROLL, REQUEST_CODE_ENROLL);
            }
        });

        Button btnVerify;
        btnVerify = findViewById(R.id.bt_verify);
        btnVerify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAction = Constants.ACTION_VERIFY;
                mRequestCode = REQUEST_CODE_VERIFY;
                startCaptureActivity(Constants.ACTION_VERIFY, REQUEST_CODE_VERIFY);
            }
        });

        Button btnUnenroll = findViewById(R.id.bt_unenroll);
        btnUnenroll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAction = Constants.ACTION_UNENROLL;
                mRequestCode = REQUEST_CODE_UNENROLL;
                startCaptureActivity(Constants.ACTION_UNENROLL, REQUEST_CODE_UNENROLL);
            }
        });

        Button btnIdentify = findViewById(R.id.bt_identify);
        btnIdentify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAction = Constants.ACTION_IDENTIFY;
                mRequestCode = REQUEST_CODE_IDENTIFY;
                startCaptureActivity(Constants.ACTION_IDENTIFY, REQUEST_CODE_IDENTIFY);
            }
        });

        Button btnCapture = findViewById(R.id.bt_capture);
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAction = Constants.ACTION_CAPTURE;
                mRequestCode = REQUEST_CODE_CAPTURE;
                startCaptureActivity(Constants.ACTION_CAPTURE, REQUEST_CODE_CAPTURE);
            }
        });

        //checkStoragePermission();

        // Avoid app auto change screen to sleep
        writeWakeLock();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "UbioXiris:MainActivity");
        wl.acquire();

        //Load and request permission from /sdcard/iritech/irissample.json
        loadConfigs();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        checkPermissions(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mResultCode == GemResult.IDDK_UVC_DEVICE_ACCESS_DENIED) {
            startCaptureActivity(mAction, mRequestCode);
        }
    }

    private void loadConfigs()
    {
        //JSON parser object to parse read file
        JSONParser jsonParser = new JSONParser();

        try (FileReader reader = new FileReader("/sdcard/iritech/irissample.json"))
        {
            //Read JSON file
            Object obj = jsonParser.parse(reader);

            JSONObject jsonObject = new JSONObject(obj.toString());
            JSONArray permissionList = jsonObject.getJSONArray("permissions");
            for (int i = 0; i < permissionList.length(); i++) {
                JSONObject permission = permissionList.getJSONObject(i);
                String permissionName = permission.getString("name");
                int permissionCode = permission.getInt("code");
                if (ContextCompat.checkSelfPermission(this, permissionName) !=
                        PackageManager.PERMISSION_GRANTED) {

                    // Permission is not granted
                    ActivityCompat.requestPermissions(this, new String[]{permissionName},
                            permissionCode);
                }
            }
        } catch (Exception var) {
            var.printStackTrace();
        }
    }

    private void startCaptureActivity(String actionType, int requestCode) {
        if (!checkCameraPermission()) {
            return;
        }
        mUserId = editUserId.getText().toString().trim();
        if (mUserId.isEmpty() && (actionType.equals(Constants.ACTION_VERIFY)
                || actionType.equals(Constants.ACTION_ENROLL))) {
            //Toast.makeText(this, "Please input User Id", Toast.LENGTH_SHORT).show();
            BestImageDialog dialog = new BestImageDialog(this);
            dialog.setTitle("Information!");
            dialog.show();
            dialog.setBestImages(null, null, null);
            dialog.setMessage("  Please input User ID  ");
            return;
        }
        Intent intent = new Intent(getApplicationContext(), CaptureActivity.class);
        intent.setAction(actionType);
        intent.putExtra(Constants.EXTRA_USER_ID, mUserId);

        LicenseInfo licInfo = LicenseInfo.getInstance();
        if (!licInfo.isInitialized()){
            licInfo.initialize(this);
        }
        if (!licInfo.isLicenseExisted()){
            RegisterLicenseDialog regLicenseDlg = RegisterLicenseDialog.newInstance();
            regLicenseDlg.setActivityAfterRegistered(intent, requestCode);
            regLicenseDlg.show(getSupportFragmentManager(), null);
        }
        else{
            startActivityForResult(intent, requestCode);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data != null) {
            int resultCodeExt = data.getIntExtra(Constants.EXTRA_RESULT_CODE, -1);
            mResultCode = resultCodeExt;
        }

        if (mResultCode != GemResult.IDDK_UVC_DEVICE_ACCESS_DENIED){
            if (resultCode == RESULT_OK) {
                processResult(requestCode, data);
            } else {
                Toast.makeText(getApplicationContext(), "Capture activity failed", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void processResult(int requestCode, Intent data) {
        if (requestCode == REQUEST_CODE_CAPTURE) {
            processCaptureResult(data, verifyImgPath, mUserId, "best");
        }
        else if (requestCode == REQUEST_CODE_ENROLL) {
            processCaptureResult(data, enrollImgPath, mUserId, "best");
        }
        else {
            Bitmap leftBm = null;
            Bitmap rightBm = null;
            Bitmap unknownBm = null;
            int resultCode = data.getIntExtra(Constants.EXTRA_RESULT_CODE, -1);
            String msg = data.getStringExtra(Constants.EXTRA_RESULT_MSG);
            msg += (resultCode != GemResult.IDDK_OK ? " (" + resultCode + ")" : "");
            if (requestCode == REQUEST_CODE_VERIFY) {
                if (resultCode == 0) {
                    boolean matchingResult = data.getBooleanExtra(Constants.EXTRA_MATCHING_RESULT, false);
                    if (matchingResult) {
                        msg = "Verify Successfully";
                    } else {
                        msg = "Verify failed! Not matched";
                    }
                }
            }
            else if (requestCode == REQUEST_CODE_IDENTIFY) {
                if (resultCode == 0)
                {
                    int resultCount = data.getIntExtra(Constants.EXTRA_MATCHING_COUNT, 0);
                    String resultItems = data.getStringExtra(Constants.EXTRA_MATCHING_ITEMS);
                    msg = "Matched with " + resultCount + " user(s): " + resultItems;
                }
            }


            BestImageDialog dialog = new BestImageDialog(this);
            dialog.setTitle("Information!");
            dialog.show();
            dialog.setBestImages(leftBm, rightBm, unknownBm);
            dialog.setMessage(msg);
        }
    }

    private void processCaptureResult(Intent data, String folderPath, String prefix, String filePurpose
    ) {
        String imageExt = ".bmp";
        String templateExt = ".tpl";
        Bitmap leftBm = null;
        Bitmap rightBm = null;
        Bitmap unknownBm = null;

        int resultCode = data.getIntExtra(Constants.EXTRA_RESULT_CODE, 0);
        String resultMsg = data.getStringExtra(Constants.EXTRA_RESULT_MSG);

        if (resultCode == 0) {
            // Capture success
            resultMsg = "Successfully! Captured data is saved at: \n" + folderPath;
            byte[] leftTemplateBuffer = data.getByteArrayExtra(Constants.EXTRA_LEFT_TEMPLATE);
            byte[] rightTemplateBuffer = data.getByteArrayExtra(Constants.EXTRA_RIGHT_TEMPLATE);

            Calendar c = Calendar.getInstance();
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);
            int hour = c.get(Calendar.HOUR_OF_DAY);
            int minute = c.get(Calendar.MINUTE);
            int second = c.get(Calendar.SECOND);
            prefix = prefix + String.format(
                    Locale.US, "_%02d%02d%02d_%02d%02d%02d_",
                    year, month, day, hour, minute, second
            );

            saveFile(folderPath, prefix, filePurpose + "L" + templateExt, leftTemplateBuffer);
            saveFile(folderPath, prefix, filePurpose + "R" + templateExt, rightTemplateBuffer);

            // Get best images if needed from IriController
            // Get best image only after capture successfully, if no capture yet error will be returned
            ImageData leftImage = new ImageData();
            ImageData rightImage = new ImageData();
            ImageData unknownImage = new ImageData();

            byte[] bmpLeft = null;
            byte[] bmpRight = null;
            byte[] bmpUnknown = null;

            CaptureActivity.getResultImages(leftImage, rightImage, unknownImage);
            if (leftImage.getData() != null) {
                bmpLeft = com.iritech.iris.Utilities.convertRawImageToBitmap(leftImage.getData(), leftImage.getWidth(), leftImage.getHeight());
                saveFile(folderPath, prefix, filePurpose + "L" + imageExt, bmpLeft);
            }

            if (rightImage.getData() != null) {
                bmpRight = com.iritech.iris.Utilities.convertRawImageToBitmap(rightImage.getData(), rightImage.getWidth(), rightImage.getHeight());
                saveFile(folderPath, prefix, filePurpose + "R" + imageExt, bmpRight);
            }

            if (unknownImage.getData() != null) {
                bmpUnknown = com.iritech.iris.Utilities.convertRawImageToBitmap(unknownImage.getData(), unknownImage.getWidth(), unknownImage.getHeight());
                saveFile(folderPath, prefix, filePurpose + "U" + imageExt, bmpUnknown);
            }

            // save left image kind 7_35, MONO_JPEG2000
            ImageData leftImageJp2 = new ImageData(ImageKind.IDDK_IKIND_K7_3_5, ImageFormat.IDDK_IFORMAT_MONO_JPEG2000, 0, 0, null);
            leftImageJp2.setCompressParams(1,100);
            ImageData rightImageJp2 = new ImageData(ImageKind.IDDK_IKIND_K7_3_5, ImageFormat.IDDK_IFORMAT_MONO_JPEG2000, 0, 0, null);
            rightImageJp2.setCompressParams(1,100);
            ImageData unknownImageJp2 = new ImageData(ImageKind.IDDK_IKIND_K7_3_5, ImageFormat.IDDK_IFORMAT_MONO_JPEG2000, 0, 0, null);
            unknownImageJp2.setCompressParams(1,100);
            CaptureActivity.getResultImages(leftImageJp2, rightImageJp2, unknownImageJp2);
            imageExt = ".jp2";
            String imagesKindString = "MONO_JPEG2000";
            String imageFormatString = "IDDK_IKIND_K7_35";
            if (leftImageJp2.getData() != null) {
                saveFile(folderPath, prefix, filePurpose + "L_" + imagesKindString + imageFormatString + imageExt, leftImageJp2.getData());
            }

            if (rightImageJp2.getData() != null) {
                saveFile(folderPath, prefix, filePurpose + "R_" + imagesKindString + imageFormatString + imageExt, rightImageJp2.getData());
            }

            if (unknownImageJp2.getData() != null) {
                saveFile(folderPath, prefix, filePurpose + "U_" + imagesKindString + imageFormatString + imageExt, unknownImageJp2.getData());
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true;
            if (bmpLeft != null) {
                leftBm = BitmapFactory.decodeByteArray(bmpLeft, 0, bmpLeft.length, options);
            }

            if (bmpRight != null) {
                rightBm = BitmapFactory.decodeByteArray(bmpRight, 0, bmpRight.length, options);
            }

            if (bmpUnknown != null) {
                unknownBm = BitmapFactory.decodeByteArray(bmpUnknown, 0, bmpUnknown.length, options);
            }

        } else {
            resultMsg += " (" + resultCode + ")";
        }

        BestImageDialog dialog = new BestImageDialog(this);
        dialog.setTitle("Information!");
        dialog.show();
        dialog.setBestImages(leftBm, rightBm, unknownBm);
        dialog.setMessage(resultMsg);
    }

    private void saveFile(String filePath, String prefix, String fileName,
                          byte[] byteArray) {
        if (byteArray == null) {
            return;
        }
        String fileFullname = prefix + fileName;
        String filePathName = filePath + File.separator + fileFullname;
        File folder = new File(filePath);
        File file = new File(filePathName);

        try {
            if (!folder.exists()) {
                folder.mkdirs();
            }
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream bos = new FileOutputStream(file);

            bos.write(byteArray);
            bos.flush();
            bos.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Information!")
                        .setMessage("Please allow write to external storage permission.")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
                            }
                        })
                        .show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            }
        } else {
            // Permission has already been granted
            initFolder();
        }
    }

    private boolean checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Information!")
                        .setMessage("Please allow camera permission.")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        new String[]{Manifest.permission.CAMERA},
                                        PERMISSIONS_REQUEST_CAMERA);
                            }
                        })
                        .show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        PERMISSIONS_REQUEST_CAMERA);
            }
            return false;
        }
        return true;
    }

    private void initFolder() {
        File enrollFolder = new File(enrollImgPath);
        if (!enrollFolder.exists()) {
            enrollFolder.mkdirs();
        }

        File verifyFolder = new File(verifyImgPath);
        if (!verifyFolder.exists()) {
            verifyFolder.mkdirs();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
//            case PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE:
//                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    initFolder();
//                } else {
//                    checkStoragePermission();
//                }
//                break;
            case PERMISSIONS_REQUEST_CAMERA:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                } else {
                    checkCameraPermission();
                }
                break;
            case PERMISSIONS_READ_PHONE_STATE:
                checkStorage(this);
                break;
            case PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initFolder();
                    checkNetWork(this);
                } else {
                    checkStoragePermission();
                }
                break;
        }
    }

    public static void writeWakeLock() {

        String path = "/sys/power/wake_lock";
        BufferedWriter bw = null;	// default buffer size = 8192
        try {
            bw = new BufferedWriter(new FileWriter(path));
            bw.write("dont_sleep");
            bw.newLine();
            bw.flush();
        } catch (IOException e) {
            Log.d("writeWakeLock", "IOException : " + e);
        } finally {
            if (bw != null) {
                try {
                    bw.close();
                } catch (IOException e) {
                    Log.d("writeWakeLock", "IOException : " + e);
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            SettingDialog dialog = new SettingDialog(this);
            dialog.show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private static void checkPermissions(final Context context) {
        // Read phone state permission
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale((Activity)context,
                    Manifest.permission.READ_PHONE_STATE)) {
                new AlertDialog.Builder(context)
                        .setTitle("Information!")
                        .setMessage("Read phone state permission needs to be allowed.")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions((Activity)context,
                                        new String[]{Manifest.permission.READ_PHONE_STATE},
                                        PERMISSIONS_READ_PHONE_STATE);
                            }
                        })
                        .show();
            } else {
                ActivityCompat.requestPermissions((Activity)context,
                        new String[]{Manifest.permission.READ_PHONE_STATE},
                        PERMISSIONS_READ_PHONE_STATE);
            }
        }
        else
        {
            checkStorage(context);
        }
    }

    private static void checkStorage(final Context context)
    {
        String TAG = "";
        // Write external storage permission
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale((Activity)context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                new AlertDialog.Builder(context)
                        .setTitle("Information!")
                        .setMessage("Access external storage permission needs to be allowed.")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions((Activity)context,
                                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
                            }
                        })
                        .show();
            } else {
                ActivityCompat.requestPermissions((Activity)context,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            }
        }
        else
        {
            checkNetWork(context);
        }
    }

    private static void checkNetWork(final Context context)
    {
        String TAG="";
        // Access network state permission
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_NETWORK_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale((Activity)context,
                    Manifest.permission.ACCESS_NETWORK_STATE)) {
                new AlertDialog.Builder(context)
                        .setTitle("Information!")
                        .setMessage("Access network state permission needs to be allowed.")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions((Activity)context,
                                        new String[]{Manifest.permission.ACCESS_NETWORK_STATE},
                                        PERMISSIONS_ACCESS_NETWORK_STATE);
                            }
                        })
                        .show();
            } else {
                ActivityCompat.requestPermissions((Activity)context,
                        new String[]{Manifest.permission.ACCESS_NETWORK_STATE},
                        PERMISSIONS_ACCESS_NETWORK_STATE);
            }
        }
    }
}
