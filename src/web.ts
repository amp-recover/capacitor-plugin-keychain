import { WebPlugin } from '@capacitor/core';
import { KeychainPlugin } from './definitions';

export class KeychainWeb extends WebPlugin implements KeychainPlugin {
  constructor() {
    super({
      name: 'Keychain',
      platforms: ['web'],
    });
  }

  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}

const Keychain = new KeychainWeb();

export { Keychain };

import { registerWebPlugin } from '@capacitor/core';
registerWebPlugin(Keychain);
