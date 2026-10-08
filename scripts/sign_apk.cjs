const fs = require('fs');
const path = require('path');
const { ApkSigner, SigningKey } = require('apk_sign_ts');

async function main() {
  const unsignedApkPath = process.argv[2] || '/tmp/palpigo-unsigned.apk';
  const signedApkPath = process.argv[3] || 'public/PalpiGO-v1.2.2026.apk';
  
  // Use the REAL, ORIGINAL release key & certificate from the project keystore
  const keyPath = path.resolve(__dirname, '../android-apk-src/release-key.pem');
  const certPath = path.resolve(__dirname, '../android-apk-src/release-cert.pem');

  if (!fs.existsSync(keyPath) || !fs.existsSync(certPath)) {
    console.error('Original release key/cert not found at', keyPath);
    process.exit(1);
  }

  const privateKey = fs.readFileSync(keyPath, 'utf8');
  const certificate = fs.readFileSync(certPath, 'utf8');
  const apkBytes = new Uint8Array(fs.readFileSync(unsignedApkPath));

  console.log(`Signing APK ${unsignedApkPath} with original release key (size: ${apkBytes.length} bytes)...`);
  const signer = new ApkSigner({
    signingKey: SigningKey.fromPEM(privateKey, certificate)
  });

  const { signedApk } = await signer.sign(apkBytes);
  fs.writeFileSync(signedApkPath, Buffer.from(signedApk));
  console.log(`Successfully signed APK with original release key written to ${signedApkPath} (size: ${signedApk.length} bytes)`);
}

main().catch((err) => {
  console.error('Signing failed:', err);
  process.exit(1);
});
