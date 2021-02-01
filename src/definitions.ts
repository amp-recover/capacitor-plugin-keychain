declare module '@capacitor/core' {
  interface PluginRegistry {
    Keychain: KeychainPlugin;
  }
}

export interface KeychainPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
