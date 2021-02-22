package com.amprecover.plugins.keychain;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

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

import javax.crypto.Cipher;
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
//        final Handler handler = new Handler(Looper.getMainLooper());
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

    private void authenticate() throws CryptoException {
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

    private void authenticateToEncrypt(boolean invalidateOnEnrollment) throws CryptoException {
        if (mPromptInfo.getSecret() == null) {
            throw new CryptoException(PluginError.BIOMETRIC_ARGS_PARSING_FAILED);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            skipAuthentication();
//        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P){
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q){
            if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
                Cipher cipher = mCryptographyManager
                        .getInitializedCipherForEncryption(SECRET_KEY, invalidateOnEnrollment, this);
                mBiometricPrompt.authenticate(createPromptInfo(), new BiometricPrompt.CryptoObject(cipher));
            } else {
                skipAuthentication();
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R){
////            if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
////                Cipher cipher = mCryptographyManager
////                        .getInitializedCipherForEncryption(SECRET_KEY, invalidateOnEnrollment, this);
////                mBiometricPrompt.authenticate(createPromptInfo(), new BiometricPrompt.CryptoObject(cipher));
////            } else {
//////                mBiometricPrompt.authenticate(createPromptInfo());
////                skipAuthentication();
////            }
            // NOTE: There's no need for a CryptoObject since we're using showAlternateAuthenticationScreen() upon decryption, anyway.
            skipAuthentication();
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
            finishWithError(PluginError.BIOMETRIC_UNKNOWN_ERROR);
        }
    }

    private void justAuthenticate() {
        mBiometricPrompt.authenticate(createPromptInfo());
    }

    private void authenticateToDecrypt() throws CryptoException {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            mBiometricPrompt.authenticate(createPromptInfo());
//            showAlternateAuthenticationScreen();
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
            // BiometricManager's Backup Auth screen on api29 looks like the biometric screen
            //      so... people have to click through both to get to backup creds :(
            //      Instead, just use our alternate auth screen, which actually looks good in api29 anyway
            //      but... can't use crypto object.  Accepting this limitation for the sake of usability.
            // TODO: if issue ever gets fixed, use BiometricPrompt.authenticate with CryptoObject instead
            showAlternateAuthenticationScreen();
//            if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
//                Cipher cipher = mCryptographyManager
//                        .getInitializedCipherForDecryption(SECRET_KEY, this);
//                System.out.println("authenticateToDecrypt BIOMETRIC_STRONG!  cipher: " + cipher);
//                mBiometricPrompt.authenticate(createPromptInfo(), new BiometricPrompt.CryptoObject(cipher));
//            } else {
//                mBiometricPrompt.authenticate(createPromptInfo());
//            }
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

        // TODO: respect mPromptInfo.isDeviceCredentialAllowed()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // NOTE: must use "BIOMETRIC_STRONG | DEVICE_CREDENTIAL" due to following error message,
            //       even though fingerprint api isn't implemented yet.
            // Error: "Authenticator combination is unsupported on API 21: DEVICE_CREDENTIAL"
            promptInfoBuilder.setAllowedAuthenticators(BIOMETRIC_STRONG | DEVICE_CREDENTIAL);
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
                promptInfoBuilder.setAllowedAuthenticators(BIOMETRIC_STRONG);
                promptInfoBuilder.setNegativeButtonText(mPromptInfo.getCancelButtonTitle());
            } else {
                // Crypto-based authentication is not supported for device credential prior to API 30.
                promptInfoBuilder.setAllowedAuthenticators(DEVICE_CREDENTIAL | BIOMETRIC_WEAK); // oh right... can't use crypto with weak
            }
        } else {
            // NOTE: can't use CryptoObject with BIOMETRIC_WEAK
            promptInfoBuilder.setAllowedAuthenticators(BIOMETRIC_STRONG | DEVICE_CREDENTIAL);
        }

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
    private void showAlternateAuthenticationScreen() {
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
                    finishWithError(PluginError.BIOMETRIC_UNKNOWN_ERROR);
                }
            } else {
                finishWithError(PluginError.BIOMETRIC_PIN_OR_PATTERN_DISMISSED);
            }
        }
    }

    private void onFailure() {
        // Don't actually do anything here, because onAuthenticationError will get called, too.
    }

    private void onError(int errorCode, @NonNull CharSequence errString) {

        switch (errorCode)
        {
            case BiometricPrompt.ERROR_USER_CANCELED:
            case BiometricPrompt.ERROR_CANCELED:
                finishWithError(PluginError.BIOMETRIC_DISMISSED);
                return;
            case BiometricPrompt.ERROR_NEGATIVE_BUTTON:
                // Note: BiometricPrompt at api30 doesn't send ERROR_NEGATIVE_BUTTON if DEVICE_CREDENTIAL is allowed
                // TODO: remove after fix https://issuetracker.google.com/issues/142740104
                if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
                        && mPromptInfo.isDeviceCredentialAllowed()
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    showAlternateAuthenticationScreen();
                    return;
                }
                finishWithError(PluginError.BIOMETRIC_DISMISSED);
                break;
            case BiometricPrompt.ERROR_LOCKOUT:
                finishWithError(PluginError.BIOMETRIC_LOCKED_OUT);
                break;
            case BiometricPrompt.ERROR_LOCKOUT_PERMANENT:
                finishWithError(PluginError.BIOMETRIC_LOCKED_OUT_PERMANENT);
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

    private void encrypt(BiometricPrompt.CryptoObject cryptoObject) throws CryptoException {
        String text = mPromptInfo.getSecret();
        Cipher cipher = null;
        if (cryptoObject != null && cryptoObject.getCipher() != null) {
            cipher = cryptoObject.getCipher();
        } else {
            cipher = mCryptographyManager
                    .getInitializedCipherForEncryption(SECRET_KEY, false, this);
        }
        EncryptedData encryptedData = mCryptographyManager.encryptData(text, cipher);
        encryptedData.save(this);
    }

    private Intent getDecryptedIntent(BiometricPrompt.CryptoObject cryptoObject) throws CryptoException {
        byte[] ciphertext = EncryptedData.loadCiphertext(this);

        Cipher cipher = null;
        if (cryptoObject != null && cryptoObject.getCipher() != null) {
            cipher = cryptoObject.getCipher();
        } else {
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
