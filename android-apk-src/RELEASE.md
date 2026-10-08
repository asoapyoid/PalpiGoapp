# Android release and signing-key rotation

Private signing keys must stay outside the repository and be stored in a
dedicated secret manager. The checked-in release key was exposed; do not use it
to sign future APKs except to create the one-time rotation proof.

1. Create a new Android signing keystore in secure storage and retain a backup.
   Do not commit the keystore, passwords, or exported private keys.
2. Using the exposed old key only for this migration, create a lineage with
   Android SDK `apksigner rotate`. Supply the old private key and certificate
   through protected file paths, and the new keystore/password through protected
   paths/environment variables. Store the resulting lineage as a protected
   release input (`PALPIGO_SIGNING_LINEAGE`), not in this repository.
3. Increase the app's version code and name in `AndroidManifest.xml`,
   `MainActivity.java`, and the OTA manifest before building.
4. Set `ANDROID_HOME`, `PALPIGO_KEYSTORE`, `PALPIGO_KEY_ALIAS`,
   `PALPIGO_KEYSTORE_PASSWORD`, `PALPIGO_KEY_PASSWORD`, and
   `PALPIGO_SIGNING_LINEAGE`, then run
   `scripts/build_android_apk.sh /secure/output/PalpiGO.apk`.
5. Verify the APK installs as an update on Android 9+ and Android 8.x before
   changing the production download endpoints. The build uses the old signing
   certificate below API 28 and the rotated certificate from API 28 onward.
   Keep the lineage and new keystore backed up for all future updates.

The production APKs and download mirrors must not be replaced with a debug
signature or an APK signed only with the new key: existing installations would
reject those updates. Removing a key from the current checkout does not erase
Git history, clones, or APKs already distributed from external mirrors; those
need separate cleanup and credential/key revocation.
