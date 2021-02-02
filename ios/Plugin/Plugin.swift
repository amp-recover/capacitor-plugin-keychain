import Foundation
import Capacitor
import LocalAuthentication

//enum PluginError:Int {
//    case BIOMETRIC_UNKNOWN_ERROR = -100
//    case BIOMETRIC_UNAVAILABLE = -101
//    case BIOMETRIC_AUTHENTICATION_FAILED = -102
//    case BIOMETRIC_PERMISSION_NOT_GRANTED = -105
//    case BIOMETRIC_NOT_ENROLLED = -106
//    case BIOMETRIC_DISMISSED = -108
//    case BIOMETRIC_SCREEN_GUARD_UNSECURED = -110
//    case BIOMETRIC_LOCKED_OUT = -111
//    case BIOMETRIC_SECRET_NOT_FOUND = -113
//}


enum PluginError:String {
    case BIOMETRIC_UNKNOWN_ERROR = "BIOMETRIC_UNKNOWN_ERROR"
    case BIOMETRIC_UNAVAILABLE = "BIOMETRIC_UNAVAILABLE"
    case BIOMETRIC_AUTHENTICATION_FAILED = "BIOMETRIC_AUTHENTICATION_FAILED"
    case BIOMETRIC_PERMISSION_NOT_GRANTED = "BIOMETRIC_PERMISSION_NOT_GRANTED"
    case BIOMETRIC_NOT_ENROLLED = "BIOMETRIC_NOT_ENROLLED"
    case BIOMETRIC_DISMISSED = "BIOMETRIC_DISMISSED"
    case BIOMETRIC_SCREEN_GUARD_UNSECURED = "BIOMETRIC_SCREEN_GUARD_UNSECURED"
    case BIOMETRIC_LOCKED_OUT = "BIOMETRIC_LOCKED_OUT"
    case BIOMETRIC_SECRET_NOT_FOUND = "BIOMETRIC_SECRET_NOT_FOUND"
}

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(Keychain)
public class Keychain: CAPPlugin {
    
    struct ErrorCodes {
        var code: String
    }

    @objc func isAvailable(_ call: CAPPluginCall){
        let authenticationContext = LAContext();
        var biometryType = "finger";
        var error:NSError?;
        let allowBackup = call.getBool("allowBackup") ?? true
        let policy:LAPolicy = allowBackup ? .deviceOwnerAuthentication : .deviceOwnerAuthenticationWithBiometrics;
        let available = authenticationContext.canEvaluatePolicy(policy, error: &error);

        if (available == true) {
            if #available(iOS 11.0, *) {
                switch(authenticationContext.biometryType) {
                case .none:
                    biometryType = "none";
                case .touchID:
                    biometryType = "finger";
                case .faceID:
                    biometryType = "face"
                @unknown default:
                    biometryType = "other"
                }
            }
            call.resolve(["biometryType": biometryType])
        } else {
            var code: String;
            switch(error!._code) {
                case Int(kLAErrorBiometryNotAvailable):
                    code = PluginError.BIOMETRIC_UNAVAILABLE.rawValue;
                    break;
                case Int(kLAErrorBiometryNotEnrolled):
                    code = PluginError.BIOMETRIC_NOT_ENROLLED.rawValue;
                    break;

                default:
                    code = PluginError.BIOMETRIC_UNKNOWN_ERROR.rawValue;
                    break;
            }
            call.reject(error!.localizedDescription, code, error)
        }
    }

    func justAuthenticate(_ call: CAPPluginCall) {
        let authenticationContext = LAContext();
        var reason = "Authentication";
        var policy:LAPolicy = .deviceOwnerAuthentication;

        if let disableBackup = call.getBool("disableBackup") {
            if disableBackup {
                authenticationContext.localizedFallbackTitle = "";
                policy = .deviceOwnerAuthenticationWithBiometrics;
            } else {
                if let fallbackButtonTitle = call.getString("fallbackButtonTitle") {
                    authenticationContext.localizedFallbackTitle = fallbackButtonTitle;
                }else{
                    authenticationContext.localizedFallbackTitle = "Use Pin";
                }
            }
        }

        // Localized reason
        if let description = call.getString("description") {
            reason = description;
        }

        authenticationContext.evaluatePolicy(
            policy,
            localizedReason: reason,
            reply: { [unowned call] (success, error) -> Void in
                if( success ) {
                    call.resolve(["message": "Success"])
                } else {
                    if (error != nil) {

                        var errorCodes = [Int: ErrorCodes]()

                        errorCodes[1] = ErrorCodes(code: PluginError.BIOMETRIC_AUTHENTICATION_FAILED.rawValue)
                        errorCodes[2] = ErrorCodes(code: PluginError.BIOMETRIC_DISMISSED.rawValue)
                        errorCodes[5] = ErrorCodes(code: PluginError.BIOMETRIC_SCREEN_GUARD_UNSECURED.rawValue)
                        errorCodes[6] = ErrorCodes(code: PluginError.BIOMETRIC_UNAVAILABLE.rawValue)
                        errorCodes[7] = ErrorCodes(code: PluginError.BIOMETRIC_NOT_ENROLLED.rawValue)
                        errorCodes[8] = ErrorCodes(code: PluginError.BIOMETRIC_LOCKED_OUT.rawValue)

                        var code = PluginError.BIOMETRIC_UNKNOWN_ERROR.rawValue
                        let errorCode = abs(error!._code)
                        if let e = errorCodes[errorCode] {
                            code = e.code
                        }
                        let description = error?.localizedDescription ?? ""
                        
                        call.reject(description, code, error)
                    } else {
                        let code = PluginError.BIOMETRIC_UNKNOWN_ERROR.rawValue
                        let description = "Something went wrong"
                        
                        call.reject(description, String(code), error)
                        
                    }
                }
            }
        );
    }

    func saveSecret(_ secretKeyName: String, _ secretStr: String, call: CAPPluginCall) {
        do {
            let secret = Secret()
            try? secret.delete(secretKeyName)
            let invalidateOnEnrollment = call.getBool("invalidateOnEnrollment") ?? false
            try secret.save(secretKeyName, secretStr, invalidateOnEnrollment: invalidateOnEnrollment)
            call.resolve(["message": "Success"])
        } catch {
            let code = PluginError.BIOMETRIC_UNKNOWN_ERROR.rawValue
            call.reject(error.localizedDescription, code, error)
        }
        return
    }


    func loadSecret(_ secretKeyName: String, _ call: CAPPluginCall) {
        let prompt = call.getString("description") ?? "Authentication"
        
        do {
            let result = try Secret().load(secretKeyName, prompt)
            call.resolve(["secret": result])
        } catch {
            var code = PluginError.BIOMETRIC_UNKNOWN_ERROR.rawValue
//            var message = error.localizedDescription
            if let err = error as? KeychainError {
                code = err.pluginError.rawValue
//                message = err.localizedDescription
            }
            call.reject(error.localizedDescription, String(code), error)
        }
    }

    func removeSecret(_ secretKeyName: String, _ call: CAPPluginCall) {
        do {
            let secret = Secret()
            try secret.delete(secretKeyName)
            call.resolve(["message": "Success"])
        } catch {
            let code = PluginError.BIOMETRIC_SECRET_NOT_FOUND.rawValue
            call.reject(error.localizedDescription, String(code), error)
        }
        return
    }

    @objc func authenticate(_ call: CAPPluginCall){
        justAuthenticate(call)
    }

    @objc func registerBiometricSecret(_ call: CAPPluginCall){
        let secretKeyName = call.getString("secretKeyName")
        let secret = call.getString("secret")
        if secretKeyName != nil && secret != nil {
            self.saveSecret(secretKeyName!, secret!, call: call)
            return
        }
    }

    @objc func loadBiometricSecret(_ call: CAPPluginCall){
        let secretKeyName = call.getString("secretKeyName")
        if secretKeyName != nil {
            self.loadSecret(secretKeyName!, call)
        }
    }

    @objc func removeBiometricSecret(_ call: CAPPluginCall){
        let secretKeyName = call.getString("secretKeyName")
        if secretKeyName != nil {
            self.removeSecret(secretKeyName!, call)
        }
    }

//    override func pluginInitialize() {
//        super.pluginInitialize()
//    }
}

/// Keychain errors we might encounter.
struct KeychainError: Error {
    var status: OSStatus

    var localizedDescription: String {
        if #available(iOS 11.3, *) {
            if let result = SecCopyErrorMessageString(status, nil) as String? {
                return result
            }
        }
        switch status {
            case errSecItemNotFound:
                return "Secret not found"
            case errSecUserCanceled:
                return "Biometric dissmissed"
            case errSecAuthFailed:
                return "Authentication failed"
            default:
                return "Unknown error \(status)"
        }
    }

    var pluginError: PluginError {
        switch status {
        case errSecItemNotFound:
            return PluginError.BIOMETRIC_SECRET_NOT_FOUND
        case errSecUserCanceled:
            return PluginError.BIOMETRIC_DISMISSED
        case errSecAuthFailed:
                return PluginError.BIOMETRIC_AUTHENTICATION_FAILED
        default:
            return PluginError.BIOMETRIC_UNKNOWN_ERROR
        }
    }
}

class Secret {

//    private static let keyName: String = "__aio_keyy"

    private func getBioSecAccessControl(invalidateOnEnrollment: Bool) -> SecAccessControl {
        var access: SecAccessControl?
        var error: Unmanaged<CFError>?

        if #available(iOS 11.3, *) {
            access = SecAccessControlCreateWithFlags(nil,
                kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                invalidateOnEnrollment ? .biometryCurrentSet : .userPresence,
                &error)
        } else {
            access = SecAccessControlCreateWithFlags(nil,
                kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                invalidateOnEnrollment ? .touchIDCurrentSet : .userPresence,
                &error)
        }
        precondition(access != nil, "SecAccessControlCreateWithFlags failed")
        return access!
    }

    func save(_ secretKeyName: String, _ secret: String, invalidateOnEnrollment: Bool) throws {
        let password = secret.data(using: String.Encoding.utf8)!

        // Allow a device unlock in the last 10 seconds to be used to get at keychain items.
        // let context = LAContext()
        // context.touchIDAuthenticationAllowableReuseDuration = 10

        // Build the query for use in the add operation.
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrAccount as String: secretKeyName,
                                    kSecAttrAccessControl as String: getBioSecAccessControl(invalidateOnEnrollment: invalidateOnEnrollment),
                                    kSecValueData as String: password]

        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else { throw KeychainError(status: status) }
    }

    func load(_ secretKeyName: String, _ prompt: String) throws -> String {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrAccount as String: secretKeyName,
                                    kSecMatchLimit as String: kSecMatchLimitOne,
                                    kSecReturnData as String : true,
                                    kSecAttrAccessControl as String: getBioSecAccessControl(invalidateOnEnrollment: true),
                                    kSecUseOperationPrompt as String: prompt]

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess else { throw KeychainError(status: status) }

        guard let passwordData = item as? Data,
            let password = String(data: passwordData, encoding: String.Encoding.utf8)
            // let account = existingItem[kSecAttrAccount as String] as? String
            else {
                throw KeychainError(status: errSecInternalError)
        }

        return password
    }

    func delete(_ secretKeyName: String) throws {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrAccount as String: secretKeyName]

        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess else { throw KeychainError(status: status) }
    }
}
