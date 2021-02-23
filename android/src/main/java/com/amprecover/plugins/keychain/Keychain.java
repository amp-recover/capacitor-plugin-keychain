package com.amprecover.plugins.keychain;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.biometric.BiometricManager;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

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
    private static final String TAG = "Keychain";
    protected static final int REQUEST_CODE_BIOMETRIC = 1; // Unique request code

    public void load() {
        // Called when the plugin is first constructed in the bridge
        Log.v(TAG, "Init Fingerprint");
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

    @PluginMethod
    public void loadBiometricSecret(PluginCall call) {
        PluginError error = canAuthenticate();
        if (error != null) {
            call.reject(error.name());
            return;
        }
        this.runBiometricActivity(call, BiometricActivityType.LOAD_SECRET);
    }

    @PluginMethod
    public void removeBiometricSecret(PluginCall call) {
        try {
            EncryptedData.delete(getActivity());
        } catch (CryptoException e) {
            sendError(call, e.getError());
            return;
        }
        sendSuccess(call, "Secret was removed successfully.");
    }

    private void runBiometricActivity(PluginCall call, BiometricActivityType type) {
        saveCall(call);

        PromptInfo.Builder mPromptInfoBuilder = new PromptInfo.Builder(
                this.getApplicationLabel(getActivity())
        );
        mPromptInfoBuilder.parseArgs(call, type);

        Intent intent = new Intent(getActivity(), BiometricActivity.class);
        intent.putExtras(mPromptInfoBuilder.build().getBundle());

        startActivityForResult(call, intent, REQUEST_CODE_BIOMETRIC);
    }

    // in order to handle the intents result, you have to @Override handleOnActivityResult
    @Override
    protected void handleOnActivityResult(int requestCode, int resultCode, Intent intent) {
//        System.out.println("-> handleOnActivityResult " + resultCode + " " + intent);
        super.handleOnActivityResult(requestCode, resultCode, intent);

        // Get the previously saved call
        PluginCall savedCall = getSavedCall();
        if (savedCall == null) {
            return;
        }
        
        if (requestCode == REQUEST_CODE_BIOMETRIC) {
            handleBiometricActivityResult(savedCall, resultCode, intent);
        }
    }
    
    private void handleBiometricActivityResult(PluginCall call, int resultCode, Intent intent) {
        if (resultCode != Activity.RESULT_OK) {
            sendError(call, intent);
            return;
        }
        
        String methodCalled = call.getMethodName();
//        System.out.println("handleBiometricActivityResult methodCalled: " + methodCalled);
        if (methodCalled.equals("registerBiometricSecret")) {
            JSObject resultJson = new JSObject();
            resultJson.put("message", "success");
            call.resolve(resultJson);
        } else if (methodCalled.equals("loadBiometricSecret")) {
//            System.out.println("processing loadBiometricSecret..." + intent.getExtras());
            if (intent != null && intent.getExtras() != null) {
//                System.out.println("intent has extras!");
                JSObject resultJson = new JSObject();
                resultJson.put("secret", intent.getExtras().getString(PromptInfo.SECRET_EXTRA));
//                System.out.println("Resolving loadBiometricSecret: " + resultJson);
                call.resolve(resultJson);
            }
        } else {
//            System.out.println("uhh.... unknown method name!! " + methodCalled);
        }
        
    }

    private void sendSuccess(PluginCall call, Intent intent) {
        if (intent != null && intent.getExtras() != null) {
            sendSuccess(call, intent.getExtras().getString(PromptInfo.SECRET_EXTRA));
        } else {
            sendSuccess(call, "biometric_success");
        }
    }

    private PluginError canAuthenticate() {
        KeyguardManager keyguardManager = ContextCompat
                .getSystemService(getContext(), KeyguardManager.class);
        if (keyguardManager != null && !keyguardManager.isKeyguardSecure()) {
            return PluginError.BIOMETRIC_SCREEN_GUARD_UNSECURED;
        }

        int authenticationModes = BIOMETRIC_STRONG | DEVICE_CREDENTIAL;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            authenticationModes = BIOMETRIC_STRONG | DEVICE_CREDENTIAL;
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            authenticationModes = BIOMETRIC_WEAK | DEVICE_CREDENTIAL;
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            authenticationModes = BIOMETRIC_WEAK | DEVICE_CREDENTIAL;
        } else {
            authenticationModes = BIOMETRIC_STRONG | DEVICE_CREDENTIAL;
        }

        // NOTE: "Developers that wish to check for the presence of a PIN, pattern,
        // or password on these versions should instead use KeyguardManager.isDeviceSecure()."
        int error = BiometricManager.from(getContext()).canAuthenticate(authenticationModes);
//        System.out.println("canAuthenticate: " + error);

        switch (error) {
//            case BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED:  // unsupported authenticationModes
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:  // try again later
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                return PluginError.BIOMETRIC_HARDWARE_NOT_SUPPORTED;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                return PluginError.BIOMETRIC_NOT_ENROLLED;
            case BiometricManager.BIOMETRIC_SUCCESS:
            default:
                return null;
        }

//        if (!fingerprintManager.isHardwareDetected()) {
//            msg.setText("Your device doesn't support fingerprint authentication");
//        }
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
//            msg.setText("Please enable the fingerprint permission");
//        }
//        if (!fingerprintManager.hasEnrolledFingerprints()) {
//            msg.setText("No fingerprint configured. Please register at least one fingerprint in your device's Settings");
//        }
//
//        if (!keyguardManager.isKeyguardSecure()) {
//            msg.setText("Please enable lockscreen security in your device's Settings");
//        } else {
//            try {
//                generateKey();
//            } catch (FingerprintException e) {
//                e.printStackTrace();
//            }
//
//            if (initCipher()) {
//                cryptoObject = new FingerprintManager.CryptoObject(cipher);
//                helper = new FingerprintHandler(this);
//                helper.startAuth(fingerprintManager, cryptoObject);
//            }
//        }

    }

    private void sendError(PluginCall call, Intent intent) {
        if (intent != null) {
            Bundle extras = intent.getExtras();
            sendError(call, extras.getInt("code"), extras.getString("message"));
        } else {
            sendError(call, PluginError.BIOMETRIC_DISMISSED);
        }
    }

    private void sendError(PluginCall call, PluginError error) {
        sendError(call, error.getValue(), error.getMessage());
    }

    private void sendError(PluginCall call, int code, String message) {
//        call.reject(message, String.valueOf(code));
        call.reject(message, message);
    }

    private void sendSuccess(PluginCall call, String message) {
//        Log.e(TAG, message);
        JSObject resultJson = new JSObject();
        resultJson.put("message", message);

        call.resolve(resultJson);
    }

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
