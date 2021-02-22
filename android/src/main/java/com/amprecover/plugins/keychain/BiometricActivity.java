package com.amprecover.plugins.keychain;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.amprecover.plugins.keychain.keychain.R;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.concurrent.Executor;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

public class BiometricActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 2;
    private PromptInfo mPromptInfo;
//    private CryptographyManager mCryptographyManager;
    private Cryptography mCryptographyManager;
    private static final String SECRET_KEY = "__aio_secret_key";
    private BiometricPrompt mBiometricPrompt;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(null);
//        int layout = getResources()
//                .getIdentifier("biometric_activity", "layout", getPackageName());
//        setContentView(layout);
        setContentView(R.layout.biometric_activity);

        if (savedInstanceState != null) {
            return;
        }

//        mCryptographyManager = new CryptographyManagerImpl();
        mCryptographyManager = new Cryptography(this);
        mPromptInfo = new PromptInfo.Builder(getIntent().getExtras()).build();
        final Handler handler = new Handler(Looper.getMainLooper());
//        Executor executor = handler::post;
        Executor executor = ContextCompat.getMainExecutor(this);
        mBiometricPrompt = new BiometricPrompt(this, executor, mAuthenticationCallback);
        try {
            authenticate();
        } catch (CryptoException e) {
            finishWithError(e);
        } catch (Exception e) {
            finishWithError(PluginError.BIOMETRIC_UNKNOWN_ERROR, e.getMessage());
        }
    }

    private void authenticate() throws CryptoException, IOException, CertificateException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableEntryException, InvalidAlgorithmParameterException, NoSuchPaddingException, KeyStoreException, NoSuchProviderException {
        System.out.println("Called: Authenticate " + mPromptInfo.getType());
        switch (mPromptInfo.getType()) {
          case JUST_AUTHENTICATE:
            justAuthenticate();
            return;
          case REGISTER_SECRET:
            authenticateToEncrypt(mPromptInfo.invalidateOnEnrollment());
            return;
          case LOAD_SECRET:
            authenticateToDecrypt();
            return;
//        case REMOVE_SECRET:
//            authenticateToRemove();
//            return;
        }
        throw new CryptoException(PluginError.BIOMETRIC_ARGS_PARSING_FAILED);
    }

    private void authenticateToEncrypt(boolean invalidateOnEnrollment) throws CryptoException, IOException, CertificateException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableEntryException, InvalidAlgorithmParameterException, NoSuchPaddingException, NoSuchProviderException, KeyStoreException {
        if (mPromptInfo.getSecret() == null) {
            throw new CryptoException(PluginError.BIOMETRIC_ARGS_PARSING_FAILED);
        }

        // if API < 23 then BIOMETRIC_HARDWARE_NOT_SUPPORTED
        //    use credentials... but credentials can't be used with cipher until api 30
        //    so... just save in preferences without user auth?
        //       or use a different library to trigger.... KeyguardManager.isDeviceSecure() ??
        // if 23 <= API <= 27
        //
        // if 28 <= API <= 29
        //
        // if API >= 30


        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
//            mBiometricPrompt.authenticate(createPromptInfo());
////            showAuthenticationScreen();
            skipAuthentication();
//        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P){
//        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q){
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R){
            if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
                Cipher cipher = mCryptographyManager
                        .getInitializedCipherForEncryption(SECRET_KEY, invalidateOnEnrollment, this);
                mBiometricPrompt.authenticate(createPromptInfo(), new BiometricPrompt.CryptoObject(cipher));
            } else {
//                mBiometricPrompt.authenticate(createPromptInfo());
                skipAuthentication();
            }
//        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R){
////            if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
////                Cipher cipher = mCryptographyManager
////                        .getInitializedCipherForEncryption(SECRET_KEY, invalidateOnEnrollment, this);
////                mBiometricPrompt.authenticate(createPromptInfo(), new BiometricPrompt.CryptoObject(cipher));
////            } else {
//////                mBiometricPrompt.authenticate(createPromptInfo());
////                skipAuthentication();
////            }
//            // Auth screen on api29 looks like the biometric screen... and so people have to click through both to get to backup creds :(
//            showAuthenticationScreen();
        } else {
            Cipher cipher = mCryptographyManager
                    .getInitializedCipherForEncryption(SECRET_KEY, invalidateOnEnrollment, this);
            mBiometricPrompt.authenticate(createPromptInfo(), new BiometricPrompt.CryptoObject(cipher));
        }
    }

    private void skipAuthentication() {
        try {
            finishWithSuccess(null);
        } catch (CryptoException e) {
            e.printStackTrace();
            finishWithError(PluginError.BIOMETRIC_UNKNOWN_ERROR);  // ???
//                    finishWithError(PluginError.BIOMETRIC_PIN_OR_PATTERN_DISMISSED);  // ???
        }
    }

    private void justAuthenticate() {
        mBiometricPrompt.authenticate(createPromptInfo());
    }

    private void authenticateToDecrypt() throws CryptoException, IOException, CertificateException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableEntryException, InvalidAlgorithmParameterException, NoSuchPaddingException, KeyStoreException, NoSuchProviderException {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            mBiometricPrompt.authenticate(createPromptInfo());
//            showAuthenticationScreen();
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q){
            if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
                Cipher cipher = mCryptographyManager
                        .getInitializedCipherForDecryption(SECRET_KEY, this);
                System.out.println("authenticateToDecrypt BIOMETRIC_STRONG!  cipher: " + cipher);
                mBiometricPrompt.authenticate(createPromptInfo(), new BiometricPrompt.CryptoObject(cipher));
            } else {
                mBiometricPrompt.authenticate(createPromptInfo());
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R){
//            if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
//                Cipher cipher = mCryptographyManager
//                        .getInitializedCipherForDecryption(SECRET_KEY, this);
//                System.out.println("authenticateToDecrypt BIOMETRIC_STRONG!  cipher: " + cipher);
//                mBiometricPrompt.authenticate(createPromptInfo(), new BiometricPrompt.CryptoObject(cipher));
//            } else {
//                mBiometricPrompt.authenticate(createPromptInfo());
//            }
            showAuthenticationScreen();
        } else {
            Cipher cipher = mCryptographyManager
                    .getInitializedCipherForDecryption(SECRET_KEY, this);
            mBiometricPrompt.authenticate(createPromptInfo(), new BiometricPrompt.CryptoObject(cipher));
        }
    }

//    private void authenticateToRemove() throws CryptoException {
//        mBiometricPrompt.authenticate(createPromptInfo());
//    }

    private BiometricPrompt.PromptInfo createPromptInfo() {


        BiometricPrompt.PromptInfo.Builder promptInfoBuilder = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(mPromptInfo.getTitle())
                .setSubtitle(mPromptInfo.getSubtitle())
                .setConfirmationRequired(mPromptInfo.getConfirmationRequired())
                .setDescription(mPromptInfo.getDescription());
//                .setAllowedAuthenticators(authenticationModes);
//                .setAllowedAuthenticators(BIOMETRIC_STRONG | DEVICE_CREDENTIAL | BIOMETRIC_WEAK);  // Crypto-based authentication is not supported for Class 2 (Weak) biometrics.
//                .setAllowedAuthenticators(BIOMETRIC_STRONG | DEVICE_CREDENTIAL); // Authenticator combination is unsupported on API 29: BIOMETRIC_STRONG | DEVICE_CREDENTIAL
//                .setAllowedAuthenticators(BIOMETRIC_STRONG); // Cancel text must be set and non-empty
//                .setAllowedAuthenticators(DEVICE_CREDENTIAL); // Authenticator combination is unsupported on API 21: DEVICE_CREDENTIAL
//                .setAllowedAuthenticators(BIOMETRIC_WEAK); // Crypto-based authentication is not supported for Class 2 (Weak) biometrics.  -> if remove crypto -> "This device does not have a fingerprint sensor"
//                .setAllowedAuthenticators(BIOMETRIC_WEAK | DEVICE_CREDENTIAL);


        // Negative text must not be set if device credential authentication is allowed.  (API 21)
        // Crypto-based authentication is not supported for Class 2 (Weak) biometrics. (API 21)
        // Crypto-based authentication is not supported for device credential prior to API 30. (API 21)
        // This device does not have a fingerprint sensor (API 21)

        // TODO: respect mPromptInfo.isDeviceCredentialAllowed()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            promptInfoBuilder.setAllowedAuthenticators(BIOMETRIC_STRONG | DEVICE_CREDENTIAL); // "Authenticator combination is unsupported on API 21: DEVICE_CREDENTIAL"
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
                    && mPromptInfo.isDeviceCredentialAllowed()) {
                System.out.println("canAuthenticate(BIOMETRIC_STRONG)!");
                promptInfoBuilder.setAllowedAuthenticators(BIOMETRIC_STRONG);
                promptInfoBuilder.setNegativeButtonText(mPromptInfo.getCancelButtonTitle());
            } else {
                System.out.println("canAuthenticate(BIOMETRIC_STRONG) == false :(");
                // Crypto-based authentication is not supported for device credential prior to API 30.
                promptInfoBuilder.setAllowedAuthenticators(DEVICE_CREDENTIAL | BIOMETRIC_WEAK);
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // oh... but why start with fingerprint prompt if the swipe prompt allows biometrics, too??
            //    well... can the other way accept a crypto object?
            //  and DEVICE_CREDENTIAL | BIOMETRIC_WEAK should also work for 28/29

//            promptInfoBuilder.setAllowedAuthenticators(DEVICE_CREDENTIAL);
//            promptInfoBuilder.setAllowedAuthenticators(DEVICE_CREDENTIAL | BIOMETRIC_WEAK); // oh right... can't use crypto with weak

            if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
                promptInfoBuilder.setAllowedAuthenticators(BIOMETRIC_STRONG);
                promptInfoBuilder.setNegativeButtonText(mPromptInfo.getCancelButtonTitle());
            } else {
                promptInfoBuilder.setAllowedAuthenticators(DEVICE_CREDENTIAL | BIOMETRIC_WEAK); // oh right... can't use crypto with weak
            }
        } else {
            if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
                promptInfoBuilder.setAllowedAuthenticators(BIOMETRIC_STRONG | DEVICE_CREDENTIAL);
            } else {
                promptInfoBuilder.setAllowedAuthenticators(DEVICE_CREDENTIAL);
            }
        }
//
//        promptInfoBuilder.setNegativeButtonText(mPromptInfo.getCancelButtonTitle());
//        System.out.println("mPromptInfo.getCancelButtonTitle(): " + mPromptInfo.getCancelButtonTitle());  // Required with BIOMETRIC_STRONG

//        // for API 29... it seems it must only allow BIOMETRIC_STRONG and include negativeButtonText
//        //    otherwise we can't use crypto
//        if (mPromptInfo.isDeviceCredentialAllowed()
//                && mPromptInfo.getType() == BiometricActivityType.JUST_AUTHENTICATE
//                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) { // TODO: remove after fix https://issuetracker.google.com/issues/142740104
//            promptInfoBuilder.setDeviceCredentialAllowed(true);
//        } else {
//            promptInfoBuilder.setNegativeButtonText(mPromptInfo.getCancelButtonTitle());
//        }
        return promptInfoBuilder.build();
    }

    private BiometricPrompt.AuthenticationCallback mAuthenticationCallback =
            new BiometricPrompt.AuthenticationCallback() {

                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    System.out.println("onAuthenticationError: "+ errorCode +" : "+ errString);
                    onError(errorCode, errString);
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    System.out.println("onAuthenticationSucceeded");
                    try {
                        finishWithSuccess(result.getCryptoObject());
                    } catch (CryptoException e) {
                        finishWithError(e);
                    }
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    System.out.println("onAuthenticationFailed");
                    onFailure();
                }
            };


    // TODO: remove after fix https://issuetracker.google.com/issues/142740104
    private void showAuthenticationScreen() {
        KeyguardManager keyguardManager = ContextCompat
                .getSystemService(this, KeyguardManager.class);
        if (keyguardManager == null
                || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        if (keyguardManager.isKeyguardSecure()) {
            Intent intent = keyguardManager
                    .createConfirmDeviceCredentialIntent(mPromptInfo.getTitle(), mPromptInfo.getDescription());
            this.startActivityForResult(intent, REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS);
        } else {
            // Show a message that the user hasn't set up a lock screen.
            finishWithError(PluginError.BIOMETRIC_SCREEN_GUARD_UNSECURED);
        }
    }

    // TODO: remove after fix https://issuetracker.google.com/issues/142740104
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS) {
            if (resultCode == Activity.RESULT_OK) {
                try {
                    finishWithSuccess(null);
                } catch (CryptoException e) {
                    e.printStackTrace();
                    finishWithError(PluginError.BIOMETRIC_UNKNOWN_ERROR);  // ???
//                    finishWithError(PluginError.BIOMETRIC_PIN_OR_PATTERN_DISMISSED);  // ???
                }
            } else {
                finishWithError(PluginError.BIOMETRIC_PIN_OR_PATTERN_DISMISSED);
            }
        }
    }

    private void onFailure() {
//        if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
//                && mPromptInfo.isDeviceCredentialAllowed()
//                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
////                && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
//                && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
//            showAuthenticationScreen();
//        } else {
//            finishWithError(PluginError.BIOMETRIC_AUTHENTICATION_FAILED);
//        }
    }

    private void onError(int errorCode, @NonNull CharSequence errString) {

        switch (errorCode)
        {
            case BiometricPrompt.ERROR_USER_CANCELED:
            case BiometricPrompt.ERROR_CANCELED:

//                if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
//                        && mPromptInfo.isDeviceCredentialAllowed()
//                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
//                        && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
////                        && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                      // on api24 bad fingerprint returns ERROR_CANCELED AND onFailure, so finishing here breaks the AuthenticationScreen
//                    return;
//                }
                finishWithError(PluginError.BIOMETRIC_DISMISSED);
                return;
            case BiometricPrompt.ERROR_NEGATIVE_BUTTON:
                // TODO: remove after fix https://issuetracker.google.com/issues/142740104
//                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P && mPromptInfo.isDeviceCredentialAllowed()) {
//                if (mPromptInfo.isDeviceCredentialAllowed()) {
                if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
                        && mPromptInfo.isDeviceCredentialAllowed()
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
//                        && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                        && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    showAuthenticationScreen();
                    return;
                }
                finishWithError(PluginError.BIOMETRIC_DISMISSED);
                break;
            case BiometricPrompt.ERROR_LOCKOUT:
                finishWithError(PluginError.BIOMETRIC_LOCKED_OUT);
//                finishWithError(PluginError.BIOMETRIC_LOCKED_OUT.getValue(), errString.toString());
                break;
            case BiometricPrompt.ERROR_LOCKOUT_PERMANENT:
                finishWithError(PluginError.BIOMETRIC_LOCKED_OUT_PERMANENT);
//                finishWithError(PluginError.BIOMETRIC_LOCKED_OUT_PERMANENT.getValue(), errString.toString());
                break;
            case BiometricPrompt.ERROR_NO_BIOMETRICS:
                finishWithError(PluginError.BIOMETRIC_NOT_ENROLLED);
                break;
            default:
                finishWithError(errorCode, errString.toString());
        }
    }

    private void finishWithSuccess() {
        setResult(RESULT_OK);
        finish();
    }

    private void finishWithSuccess(BiometricPrompt.CryptoObject cryptoObject) throws CryptoException {
        Intent intent = null;
        switch (mPromptInfo.getType()) {
          case REGISTER_SECRET:
            encrypt(cryptoObject);
            break;
          case LOAD_SECRET:
            intent = getDecryptedIntent(cryptoObject);
            break;
//          case REMOVE_SECRET:
//            removeKey();
//            break;
        }
        if (intent == null) {
            setResult(RESULT_OK);
        } else {
            setResult(RESULT_OK, intent);
        }
        finish();
    }

    private void encrypt(BiometricPrompt.CryptoObject cryptoObject) throws CryptoException { //, IOException, CertificateException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableEntryException, InvalidAlgorithmParameterException, NoSuchPaddingException, NoSuchProviderException, BadPaddingException, KeyStoreException, IllegalBlockSizeException {
        String text = mPromptInfo.getSecret();
        Cipher cipher = null;
        if (cryptoObject != null && cryptoObject.getCipher() != null) {
            cipher = cryptoObject.getCipher();
        } else {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && strongEnabled....) {
            cipher = mCryptographyManager
                    .getInitializedCipherForEncryption(SECRET_KEY, false, this);
        }
        EncryptedData encryptedData = mCryptographyManager.encryptData(text, cipher);
        encryptedData.save(this);
    }

    private Intent getDecryptedIntent(BiometricPrompt.CryptoObject cryptoObject) throws CryptoException { //, IOException, CertificateException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableEntryException, InvalidAlgorithmParameterException, NoSuchPaddingException, NoSuchProviderException, BadPaddingException, KeyStoreException, IllegalBlockSizeException {
        byte[] ciphertext = EncryptedData.loadCiphertext(this);

        Cipher cipher = null;
        if (cryptoObject != null && cryptoObject.getCipher() != null) {
            cipher = cryptoObject.getCipher();
            System.out.println("getDecryptedIntent cryptoObject.getCipher() = " + cipher);
        } else {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && strongEnabled....) {
//            byte[] initializationVector = EncryptedData.loadInitializationVector(this);
            cipher = mCryptographyManager
                    .getInitializedCipherForDecryption(SECRET_KEY, this);
        }

        String secret = mCryptographyManager.decryptData(ciphertext, cipher);
        if (secret != null) {
            Intent intent = new Intent();
            intent.putExtra(PromptInfo.SECRET_EXTRA, secret);
            return intent;
        }
        return null;
    }

//    private void removeKey() throws CryptoException {
////        mCryptographyManager.encryptData(SECRET_KEY);
//        // TODO: finish removeKey functionality
//        mCryptographyManager.removeKey(SECRET_KEY);
//    }

    private void finishWithError(CryptoException e) {
        e.printStackTrace();
        finishWithError(e.getError().getValue(), e.getMessage());
    }

    private void finishWithError(PluginError error) {
        finishWithError(error.getValue(), error.getMessage());
    }

    private void finishWithError(PluginError error, String message) {
        finishWithError(error.getValue(), message);
    }

    private void finishWithError(int code, String message) {
        System.out.println("finishWithError ERROR " + code + " " + message);
        Thread.dumpStack();

        Intent data = new Intent();
        data.putExtra("code", code);
        data.putExtra("message", message);
        setResult(RESULT_CANCELED, data);
        finish();
    }
}
