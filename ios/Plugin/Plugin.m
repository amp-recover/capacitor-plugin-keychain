#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

// Define the plugin using the CAP_PLUGIN Macro, and
// each method the plugin supports using the CAP_PLUGIN_METHOD macro.
CAP_PLUGIN(Keychain, "Keychain",
           CAP_PLUGIN_METHOD(echo, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(isAvailable, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(authenticate, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(registerBiometricSecret, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(loadBiometricSecret, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(removeBiometricSecret, CAPPluginReturnPromise);
)
