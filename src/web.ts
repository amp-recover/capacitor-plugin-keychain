import { WebPlugin } from '@capacitor/core';
import { KeychainPlugin } from './definitions';

export class KeychainWeb extends WebPlugin implements KeychainPlugin {
  constructor() {
    super({
      name: 'Keychain',
      platforms: ['web'],
    });
  }
  isAvailable(options: { allowBackup: boolean; }): Promise<{ biometryType: string; code: string; message: string; }> {
    console.log('ECHO?', options);
    // return options;
    throw new Error('Method not implemented.');
  }
  registerBiometricSecret(options: { secret: string; invalidateOnEnrollment: boolean; }): Promise<{ message: string; code: string; }> {
    console.log('ECHO?', options);
    throw new Error('Method not implemented.');
  }
  loadBiometricSecret(options: { description: string; }): Promise<{ secret: string, message: string; code: string; }> {
    console.log('ECHO?', options);
    throw new Error('Method not implemented.');
  }
  removeBiometricSecret(): Promise<{ message: string; code: string; }> {
    throw new Error('Method not implemented.');
  }

  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO?', options);
    return options;
  }
}

const Keychain = new KeychainWeb();

export { Keychain };

import { registerWebPlugin } from '@capacitor/core';
registerWebPlugin(Keychain);
