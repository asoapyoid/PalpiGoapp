package com.pokemate.companion;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Zero-dependency ContentProvider that serves the downloaded OTA update APK
 * ("pokemate-companion-update.apk") from internal cacheDir to the native Android
 * Package Installer with FLAG_GRANT_READ_URI_PERMISSION.
 */
public class ApkUpdateFileProvider extends ContentProvider {

    public static final String AUTHORITY = "com.pokemate.companion.apkprovider";
    public static final String APK_FILE_NAME = "pokemate-companion-update.apk";

    public static File getDownloadedApkFile(android.content.Context context) {
        return new File(context.getCacheDir(), APK_FILE_NAME);
    }

    public static Uri getContentUriForApk(android.content.Context context) {
        return Uri.parse("content://" + AUTHORITY + "/" + APK_FILE_NAME);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (getContext() == null) {
            throw new FileNotFoundException("Context is null");
        }
        File apkFile = getDownloadedApkFile(getContext());
        if (!apkFile.exists() || apkFile.length() < 1024) {
            throw new FileNotFoundException("Downloaded APK file not found: " + apkFile.getAbsolutePath());
        }
        return ParcelFileDescriptor.open(apkFile, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {
        File apkFile = getContext() != null ? getDownloadedApkFile(getContext()) : null;
        long size = (apkFile != null && apkFile.exists()) ? apkFile.length() : 0L;

        String[] cols = projection != null && projection.length > 0
                ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};

        MatrixCursor cursor = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) {
                row[i] = "pokemate-companion.apk";
            } else if (OpenableColumns.SIZE.equals(cols[i])) {
                row[i] = size;
            } else {
                row[i] = null;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
