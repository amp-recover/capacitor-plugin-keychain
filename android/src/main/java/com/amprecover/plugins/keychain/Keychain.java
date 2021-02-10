package com.amprecover.plugins.keychain;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
//import android.hardware.biometrics.BiometricManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

@NativePlugin(
    requestCodes={Keychain.REQUEST_CODE_BIOMETRIC}, // register request code(s) for intent results

    permissions={
        Manifest.permission.USE_BIOMETRIC,
        Manifest.permission.USE_FINGERPRINT
    }
)
public class Keychain extends Plugin {
//    protected static final int REQUEST_KEYCHAIN_VALUE = 4252226; // Unique request code

    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value");

        JSObject ret = new JSObject();
        ret.put("value", value);
        call.success(ret);
    }



    private static final String TAG = "Keychain";
    protected static final int REQUEST_CODE_BIOMETRIC = 1;

//    private CallbackContext mCallbackContext = null;
    private PromptInfo.Builder mPromptInfoBuilder;

    public void load() {
        Log.v(TAG, "Init Fingerprint");
        mPromptInfoBuilder = new PromptInfo.Builder(
                this.getApplicationLabel(getActivity())
        );

//        // https://developer.android.com/training/sign-in/biometric-auth
//        // Allows user to authenticate using either a Class 3 biometric or
//        // their lock screen credential (PIN, pattern, or password).
//        promptInfo = new BiometricPrompt.PromptInfo.Builder()
//                .setTitle("Biometric login for my app")
//                .setSubtitle("Log in using your biometric credential")
//                // Can't call setNegativeButtonText() and
//                // setAllowedAuthenticators(...|DEVICE_CREDENTIAL) at the same time.
//                // .setNegativeButtonText("Use account password")
//                .setAllowedAuthenticators(BIOMETRIC_STRONG | DEVICE_CREDENTIAL)
//                .build();
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        PluginError error = canAuthenticate();
        JSObject ret = new JSObject();
        if (error != null) {
            call.reject(error.name());
            return;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
            ret.put("biometryType", "biometric");
        } else {
            ret.put("biometryType", "finger");
        }
        call.resolve(ret);
    }

    @PluginMethod
    public void registerBiometricSecret(PluginCall call) {
        // should at least contain the secret
        if (call.getString("secret") == null) {
            call.reject(PluginError.BIOMETRIC_ARGS_PARSING_FAILED.name());
            return;
        }
        this.runBiometricActivity(call, BiometricActivityType.REGISTER_SECRET);
    }
//
//    @PluginMethod
//    public void loadBiometricSecret(PluginCall call) {
//        this.runBiometricActivity(call, BiometricActivityType.LOAD_SECRET);
////        saveCall(call);
////
////        Intent intent = new Intent(Intent.ACTION_PICK);
////        intent.setType("image/*");
////
////        startActivityForResult(call, intent, REQUEST_IMAGE_PICK);
//    }
//
//    @PluginMethod
//    private void removeBiometricSecret(PluginCall call) {
//        this.runBiometricActivity(call, BiometricActivityType.REMOVE_SECRET);
//    }
//
//    @PluginMethod
//    public void authenticate(PluginCall call) {
//        this.runBiometricActivity(call, BiometricActivityType.JUST_AUTHENTICATE);
//    }
//
    private void runBiometricActivity(PluginCall call, BiometricActivityType type) {
        PluginError error = canAuthenticate();
        if (error != null) {
            call.reject(error.name());
            return;
        }
        saveCall(call);

//        getActivity().runOnUiThread(() -> {
            mPromptInfoBuilder.parseArgs(call, type);
//            Intent intent = new Intent(getActivity().getApplicationContext(), BiometricActivity.class);
//            Intent intent = new Intent(getContext(), BiometricActivity.class);
            Intent intent = new Intent(getActivity(), BiometricActivity.class);
//            Intent intent = new Intent(getActivity(), BiometricActivity.class);
            intent.putExtras(mPromptInfoBuilder.build().getBundle());
            startActivityForResult(call, intent, REQUEST_CODE_BIOMETRIC);
//        });
//        JSObject ret = new JSObject();
//        ret.put("biometryType", "biometric");
//        call.resolve(ret);

//        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
//        pluginResult.setKeepCallback(true);
//        this.mCallbackContext.sendPluginResult(pluginResult);
    }

    // in order to handle the intents result, you have to @Override handleOnActivityResult
    @Override
    protected void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        super.handleOnActivityResult(requestCode, resultCode, data);

        // Get the previously saved call
        PluginCall savedCall = getSavedCall();

        if (savedCall == null) {
            return;
        }
        if (requestCode == REQUEST_CODE_BIOMETRIC) {
            if (resultCode != Activity.RESULT_OK) {
                sendError(savedCall, data);
                return;
            }
        }
    }
//
////    @Override
////    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
////        super.onActivityResult(requestCode, resultCode, intent);
////        if (requestCode != REQUEST_CODE_BIOMETRIC) {
////            return;
////        }
////        if (resultCode != Activity.RESULT_OK) {
////            sendError(intent);
////            return;
////        }
////        sendSuccess(intent);
////    }
//
//    private void sendSuccess(Intent intent) {
//        if (intent != null && intent.getExtras() != null) {
//            sendSuccess(intent.getExtras().getString(PromptInfo.SECRET_EXTRA));
//        } else {
//            sendSuccess("biometric_success");
//        }
//    }
//
    private void sendError(PluginCall call, Intent intent) {
        if (intent != null) {
            Bundle extras = intent.getExtras();
            sendError(call, extras.getInt("code"), extras.getString("message"));
        } else {
            sendError(call, PluginError.BIOMETRIC_DISMISSED);
        }
    }

    private PluginError canAuthenticate() {

        // NOTE: "Developers that wish to check for the presence of a PIN, pattern,
        // or password on these versions should instead use KeyguardManager.isDeviceSecure()."
        int error = BiometricManager.from(getContext()).canAuthenticate(
                BIOMETRIC_STRONG | DEVICE_CREDENTIAL | BIOMETRIC_WEAK
        );
        switch (error) {
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                return PluginError.BIOMETRIC_HARDWARE_NOT_SUPPORTED;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                return PluginError.BIOMETRIC_NOT_ENROLLED;
            case BiometricManager.BIOMETRIC_SUCCESS:
            default:
                return null;
        }
    }

    private void sendError(PluginCall call, int code, String message) {
        JSObject resultJson = new JSObject();
        resultJson.put("code", code);
        resultJson.put("message", message);
        call.reject(message, String.valueOf(code));


//        try {
//            resultJson.put("code", code);
//            resultJson.put("message", message);
//
//            PluginResult result = new PluginResult(PluginResult.Status.ERROR, resultJson);
//            result.setKeepCallback(true);
//            cordova.getActivity().runOnUiThread(() ->
//                    Fingerprint.this.mCallbackContext.sendPluginResult(result));
//        } catch (JSONException e) {
//            Log.e(TAG, e.getMessage(), e);
//        }
    }

    private void sendError(PluginCall call, PluginError error) {
        sendError(call, error.getValue(), error.getMessage());
    }
//
//    private void sendSuccess(String message) {
//        Log.e(TAG, message);
//        cordova.getActivity().runOnUiThread(() ->
//                this.mCallbackContext.success(message));
//    }
//
    private String getApplicationLabel(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo app = packageManager
                    .getApplicationInfo(context.getPackageName(), 0);
            return packageManager.getApplicationLabel(app).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
