package com.amprecover.plugins.keychain;

class KeyInvalidatedException extends CryptoException {
    KeyInvalidatedException() {
        super(PluginError.BIOMETRIC_NO_SECRET_FOUND);
    }
}
