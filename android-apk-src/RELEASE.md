# Android release and signing-key rotation

Private signing keys must stay outside the repository and be stored in a
dedicated secret manager. The checked-in release key was exposed. This
compatibility build still needs that key for Android 8.x (API 26–27); it uses
the rotated key and lineage from API 28 onward. To fully retire the exposed key,
stop supporting API 26–27 and require those users to move to Android 9+.

1. Create a new Android signing keystore in secure storage and retain a backup.
   Do not commit either key, certificate-chain inputs, or passwords.
2. Using the exposed old key only for this migration, create a lineage with
   Android SDK `apksigner rotate`. Supply its private key and certificate via
   old keystore/password and the new keystore/password via protected paths and
   environment variables:

   ```sh
   "$ANDROID_HOME/build-tools/35.0.0/apksigner" rotate \
     --out "$PALPIGO_SIGNING_LINEAGE" \
     --old-signer --ks "$PALPIGO_LEGACY_KEYSTORE" \
       --ks-key-alias "$PALPIGO_LEGACY_KEY_ALIAS" \
       --ks-pass env:PALPIGO_LEGACY_KEYSTORE_PASSWORD \
       --key-pass env:PALPIGO_LEGACY_KEY_PASSWORD \
     --new-signer --ks "$PALPIGO_KEYSTORE" \
       --ks-key-alias "$PALPIGO_KEY_ALIAS" \
       --ks-pass env:PALPIGO_KEYSTORE_PASSWORD \
       --key-pass env:PALPIGO_KEY_PASSWORD
   ```

   Store the resulting lineage as a protected release input, not in this
   repository.
3. Increase the app's version code and name in `AndroidManifest.xml`,
   `MainActivity.java`, and the OTA manifest before building.
4. Set `ANDROID_HOME`, `PALPIGO_LEGACY_KEYSTORE`, `PALPIGO_LEGACY_KEY_ALIAS`,
   `PALPIGO_LEGACY_KEYSTORE_PASSWORD`, `PALPIGO_LEGACY_KEY_PASSWORD`,
   `PALPIGO_KEYSTORE`, `PALPIGO_KEY_ALIAS`,
   `PALPIGO_KEYSTORE_PASSWORD`, `PALPIGO_KEY_PASSWORD`, and
   `PALPIGO_SIGNING_LINEAGE`, then run
   `scripts/build_android_apk.sh /secure/output/PalpiGO.apk`.
5. Verify the APK updates existing installs on Android 8.x and Android 9+
  before changing production download endpoints. Keep the lineage and new
  keystore backed up for all future updates.

The production APKs and download mirrors must not be replaced with a debug
signature or an APK signed without the rotation lineage: Android 9+ existing
installations would reject those updates. The exposed legacy key remains
necessary for API 26–27 signatures and cannot be considered revoked while that
support remains. Removing a key from the current checkout does not erase
Git history, clones, or APKs already distributed from external mirrors; those
need separate cleanup and credential/key revocation.
