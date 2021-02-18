package com.amprecover.plugins.keychain;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Calendar;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.security.auth.x500.X500Principal;

class CryptographyManagerImpl implements CryptographyManager {

    private static final int KEY_SIZE = 256;
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String ENCRYPTION_PADDING = "NoPadding"; // KeyProperties.ENCRYPTION_PADDING_NONE
    private static final String ENCRYPTION_ALGORITHM = "AES"; // KeyProperties.KEY_ALGORITHM_AES
    private static final String KEY_ALGORITHM_AES = "AES"; // KeyProperties.KEY_ALGORITHM_AES
    private static final String ENCRYPTION_BLOCK_MODE = "GCM"; // KeyProperties.BLOCK_MODE_GCM

    private Cipher getCipher() throws NoSuchPaddingException, NoSuchAlgorithmException {
        String transformation = ENCRYPTION_ALGORITHM + "/" + ENCRYPTION_BLOCK_MODE + "/" + ENCRYPTION_PADDING;
        return Cipher.getInstance(transformation);
    }

    private SecretKey getOrCreateSecretKey(String keyName, boolean invalidateOnEnrollment, Context context) throws CryptoException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getOrCreateSecretKeyNew(keyName, invalidateOnEnrollment);
        } else {
            return getOrCreateSecretKeyOld(keyName, context);
        }
    }

    private SecretKey getOrCreateSecretKeyOld(String keyName, Context context) throws CryptoException {
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        end.add(Calendar.YEAR, 1);
        try {

            // Why aren't we using Keystore in this old method, too??
            // https://medium.com/@ericfu/securely-storing-secrets-in-an-android-application-501f030ae5a3

            System.out.println("getOrCreateSecretKeyOld keyName: " + keyName);
            KeyPairGeneratorSpec keySpec = new KeyPairGeneratorSpec.Builder(context)
                    .setAlias(keyName)
                    .setSubject(new X500Principal("CN=FINGERPRINT_AIO ," +
                            " O=FINGERPRINT_AIO" +
                            " C=World"))
                    .setSerialNumber(BigInteger.ONE)
                    .setStartDate(start.getTime())
                    .setEndDate(end.getTime())
                    .build();
            KeyGenerator kg = KeyGenerator.getInstance(KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            kg.init(keySpec);
            return kg.generateKey();

//            KeyPairGenerator kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
//            kpg.initialize(keySpec);
//            return kpg.generateKeyPair();
//            saveEncryptedKey(context);
        } catch (Exception e) {
            System.out.println("getOrCreateSecretKeyOld ERROR " + e);
            Thread.dumpStack();
            throw new CryptoException(e.getMessage(), e);
        }
    }

//    @SuppressLint("ApplySharedPref")
//    private void saveEncryptedKey(Context context) throws CertificateException, NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, KeyStoreException, NoSuchProviderException, UnrecoverableEntryException, IOException {
//        SharedPreferences pref = context.getSharedPreferences(SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE);
//        String encryptedKeyBase64encoded = pref.getString(ENCRYPTED_KEY_NAME, null);
//        if (encryptedKeyBase64encoded == null) {
//            byte[] key = new byte[16];
//            SecureRandom secureRandom = new SecureRandom();
//            secureRandom.nextBytes(key);
//            byte[] encryptedKey = rsaEncryptKey(key);
//            encryptedKeyBase64encoded = Base64.encodeToString(encryptedKey, Base64.DEFAULT);
//            SharedPreferences.Editor edit = pref.edit();
//            edit.putString(ENCRYPTED_KEY_NAME, encryptedKeyBase64encoded);
//            boolean successfullyWroteKey = edit.commit();
//            if (successfullyWroteKey) {
//                Log.d(LOG_TAG, "Saved keys successfully");
//            } else {
//                Log.e(LOG_TAG, "Saved keys unsuccessfully");
//                throw new IOException("Could not save keys");
//            }
//        }
//
//    }
//
//    private byte[] rsaEncryptKey(byte[] secret) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, NoSuchProviderException, NoSuchPaddingException, UnrecoverableEntryException, InvalidKeyException {
//
//        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE_NAME);
//        keyStore.load(null);
//
//        KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
//        Cipher inputCipher = Cipher.getInstance(RSA_MODE, CIPHER_PROVIDER_NAME_ENCRYPTION_DECRYPTION_RSA);
//        inputCipher.init(Cipher.ENCRYPT_MODE, privateKeyEntry.getCertificate().getPublicKey());
//
//        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//        CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, inputCipher);
//        cipherOutputStream.write(secret);
//        cipherOutputStream.close();
//
//        byte[] encryptedKeyAsByteArray = outputStream.toByteArray();
//        return encryptedKeyAsByteArray;
//    }
//
//    private  byte[] rsaDecryptKey(byte[] encrypted) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, UnrecoverableEntryException, NoSuchProviderException, NoSuchPaddingException, InvalidKeyException {
//
//        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE_NAME);
//        keyStore.load(null);
//
//        KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry)keyStore.getEntry(KEY_ALIAS, null);
//        Cipher output = Cipher.getInstance(RSA_MODE, CIPHER_PROVIDER_NAME_ENCRYPTION_DECRYPTION_RSA);
//        output.init(Cipher.DECRYPT_MODE, privateKeyEntry.getPrivateKey());
//        CipherInputStream cipherInputStream = new CipherInputStream(
//                new ByteArrayInputStream(encrypted), output);
//        ArrayList<Byte> values = new ArrayList<>();
//        int nextByte;
//        while ((nextByte = cipherInputStream.read()) != -1) {
//            values.add((byte)nextByte);
//        }
//
//        byte[] decryptedKeyAsBytes = new byte[values.size()];
//        for(int i = 0; i < decryptedKeyAsBytes.length; i++) {
//            decryptedKeyAsBytes[i] = values.get(i);
//        }
//        return decryptedKeyAsBytes;
//    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private SecretKey getOrCreateSecretKeyNew(String keyName, boolean invalidateOnEnrollment) throws CryptoException {
        try {
            System.out.println("getOrCreateSecretKeyNew keyName: " + keyName);
            // If Secretkey was previously created for that keyName, then grab and return it.
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null); // Keystore must be loaded before it can be accessed


            SecretKey key = (SecretKey) keyStore.getKey(keyName, null);
            if (key != null) {
                return key;
            }

            // if you reach here, then a new SecretKey must be generated for that keyName
            KeyGenParameterSpec.Builder keyGenParamsBuilder = new KeyGenParameterSpec.Builder(keyName,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE)
                    .setUserAuthenticationRequired(true);
//                    .setUserAuthenticationValidityDurationSeconds(500);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                keyGenParamsBuilder.setInvalidatedByBiometricEnrollment(invalidateOnEnrollment);
            }

            KeyGenerator keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE);
            keyGenerator.init(keyGenParamsBuilder.build());

            return keyGenerator.generateKey();
        } catch (Exception e) {
            System.out.println("getOrCreateSecretKeyNew ERROR " + e);
            Thread.dumpStack();
            throw new CryptoException(e.getMessage(), e);
        }
    }

    @Override
    public Cipher getInitializedCipherForEncryption(String keyName, boolean invalidateOnEnrollment, Context context) throws CryptoException {
        try {
            Cipher cipher = getCipher();
            SecretKey secretKey = getOrCreateSecretKey(keyName, invalidateOnEnrollment, context);
            System.out.println("getInitializedCipherForEncryption secretKey: " + secretKey);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            System.out.println("getInitializedCipherForEncryption cipher initialized!");
            return cipher;
//        } catch (Exception e) {
        } catch (NoSuchPaddingException e) {
            System.out.println("NoSuchPaddingException " + e);
            throw new CryptoException(e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            System.out.println("NoSuchAlgorithmException " + e);
            throw new CryptoException(e.getMessage(), e);
        } catch (InvalidKeyException e) {
            System.out.println("Exception: " + e);
            try {
                handleException(e, keyName);
            } catch (KeyInvalidatedException kie) {
                System.out.println("getInitializedCipherForEncryption got KeyInvalidatedException");
                return getInitializedCipherForEncryption(keyName, invalidateOnEnrollment, context);
            }
            throw new CryptoException(e.getMessage(), e);
        }
    }

    private void handleException(Exception e, String keyName) throws CryptoException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && e instanceof KeyPermanentlyInvalidatedException) {
            System.out.println("removing key due to exception!");
            removeKey(keyName);
            throw new KeyInvalidatedException();
        }
    }

    @Override
    public Cipher getInitializedCipherForDecryption(String keyName, byte[] initializationVector, Context context) throws CryptoException {
        try {
            Cipher cipher = getCipher();
            SecretKey secretKey = getOrCreateSecretKey(keyName, true, context);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, initializationVector));
            return cipher;
        } catch (Exception e) {
            System.out.println("getInitializedCipherForDecryption ERROR " + e);
            Thread.dumpStack();

            handleException(e, keyName);
            throw new CryptoException(e.getMessage(), e);
        }
    }

    @Override
    public void removeKey(String keyName) throws CryptoException {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null); // Keystore must be loaded before it can be accessed
            keyStore.deleteEntry(keyName);
        } catch (Exception e) {
            throw new CryptoException(e.getMessage(), e);
        }
    }

    @Override
    public EncryptedData encryptData(String plaintext, Cipher cipher) throws CryptoException {
        try {
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedData(ciphertext, cipher.getIV());
        } catch (BadPaddingException|IllegalBlockSizeException e) {
            System.out.println("encryptData ERROR " + e);
            e.printStackTrace();
//            Thread.dumpStack();
            throw new CryptoException(e.getMessage(), e);
        }
    }

    @Override
    public String decryptData(byte[] ciphertext, Cipher cipher) throws CryptoException {
        try {
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (BadPaddingException|IllegalBlockSizeException e) {
            System.out.println("decryptData ERROR " + e);
            Thread.dumpStack();
            throw new CryptoException(e.getMessage(), e);
        }
    }
}
