# Android release and signing-key rotation

Private signing keys must stay outside the repository and be stored in a
dedicated secret manager. The checked-in release key was exposed. Preserving
updates on Android 8.x (API 26–27) still requires that legacy key for those
devices; Android 9+ uses the rotated key and lineage. The legacy key remains a
security risk for API 26–27 and cannot be considered revoked while supported.
To fully retire it, stop supporting API 26–27 and require those users to move
to Android 9+.

1. Create a new Android signing keystore in secure storage and retain a backup.
   Do not commit either key, certificate-chain inputs, or passwords.
2. Create a lineage with Android SDK `apksigner rotate`. Supply the old and new
   keystores and passwords via protected paths and environment variables:

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
  keystore backed up for all future updates. Revoke exposed OCR API credentials
  with their provider as well.

The production APKs and download mirrors must not be replaced with a debug
signature or an APK signed without the rotation lineage: Android 9+ existing
installations would reject those updates. Removing keys from the current
checkout does not erase Git history, clones, or APKs already distributed from
external mirrors; those require coordinated history cleanup, mirror replacement,
and credential revocation.
