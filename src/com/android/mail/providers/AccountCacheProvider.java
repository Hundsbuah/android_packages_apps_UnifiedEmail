/**
 * Copyright (c) 2011, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mail.providers;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;

import java.util.Map;


/**
 * The Account Cache provider allows email providers to register "accounts" and the UI has a single
 * place to query for the list of accounts.
 *
 * During development this will allow new account types to be added, and allow them to be shown in
 * the application.  For example, the mock accounts can be enabled/disabled.
 * In the future, once other processes can add new accounts, this could allow other "mail"
 * applications have their content appear within the application
 */
public abstract class AccountCacheProvider extends ContentProvider {

    private final static Map<Uri, CachedAccount> ACCOUNT_CACHE = Maps.newHashMap();

    private ContentResolver mResolver;
    private static String sAuthority;
    private static AccountCacheProvider sInstance;

    /**
     * Allows the implmenting provider to specify the authority that should be used.
     */
    protected abstract String getAuthority();

    public static Uri getAccountsUri() {
        return Uri.parse("content://" + sAuthority + "/");
    }

    @Override
    public boolean onCreate() {
        sInstance = this;
        sAuthority = getAuthority();
        mResolver = getContext().getContentResolver();
        return true;
    }

    @Override
    public void shutdown() {
        sInstance = null;
    }

    @Override
    public Cursor query(Uri url, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // This content provider currently only supports one query (to return the list of accounts).
        // No reason to check the uri.  Currently only checking the projections

        // Validates and returns the projection that should be used.
        final String[] resultProjection = UIProviderValidator.validateAccountProjection(projection);
        final MatrixCursor cursor = new MatrixCursor(resultProjection);

        for (CachedAccount account : ACCOUNT_CACHE.values()) {
            final MatrixCursor.RowBuilder builder = cursor.newRow();

            for (String column : resultProjection) {
                if (TextUtils.equals(column, BaseColumns._ID)) {
                    builder.add(Integer.valueOf((int)account.mId));
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.NAME)) {
                    builder.add(account.mName);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.PROVIDER_VERSION)) {
                    // TODO fix this
                    builder.add(Integer.valueOf(0));
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.URI)) {
                    builder.add(account.mUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.CAPABILITIES)) {
                    builder.add(Integer.valueOf((int)account.mCapabilities));
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.FOLDER_LIST_URI)) {
                    builder.add(account.mFolderListUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.SEARCH_URI)) {
                    builder.add(account.mSearchUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.ACCOUNT_FROM_ADDRESSES_URI)) {
                    builder.add(account.mAccountFromAddressesUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.SAVE_DRAFT_URI)) {
                    builder.add(account.mSaveDraftUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.SEND_MAIL_URI)) {
                    builder.add(account.mSendMailUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.EXPUNGE_MESSAGE_URI)) {
                    builder.add(account.mExpungeMessageUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.UNDO_URI)) {
                    builder.add(account.mUndoUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SETTINGS_INTENT_URI)) {
                    builder.add(account.mSettingsIntentUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SETTINGS_QUERY_URI)) {
                    builder.add(account.mSettingsQueryUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.HELP_INTENT_URI)) {
                    builder.add(account.mHelpIntentUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.SYNC_STATUS)) {
                    builder.add(Integer.valueOf((int)account.mSyncStatus));
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.COMPOSE_URI)) {
                    builder.add(account.mComposeIntentUri);
                } else {
                    throw new IllegalStateException("Column not found: " + column);
                }
            }

        }

        cursor.setNotificationUri(mResolver, getAccountsUri());

        return cursor;
    }

    @Override
    public Uri insert(Uri url, ContentValues values) {
        return url;
    }

    @Override
    public int update(Uri url, ContentValues values, String selection,
            String[] selectionArgs) {
        return 0;
    }

    @Override
    public int delete(Uri url, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }


    public static void addAccount(CachedAccount account) {
        synchronized (ACCOUNT_CACHE) {
            if (account != null) {
                ACCOUNT_CACHE.put(account.mUri, account);
            }
        }
        // Explicitly calling this out of the synchronized block in case any of the observers get
        // called synchronously.
        broadcastAccountChange();
    }

    public static void removeAccount(String accountUri) {
        synchronized (ACCOUNT_CACHE) {
            final CachedAccount account = ACCOUNT_CACHE.get(accountUri);

            if (account != null) {
                ACCOUNT_CACHE.remove(account);
            }
        }

        // Explicitly calling this out of the synchronized block in case any of the observers get
        // called synchronously.
        broadcastAccountChange();
    }

    private static void broadcastAccountChange() {
        final AccountCacheProvider provider = sInstance;

        if (provider != null) {
            provider.mResolver.notifyChange(getAccountsUri(), null);
        }
    }

    public static class CachedAccount {
        private final long mId;
        private final String mName;
        private final Uri mUri;
        private final long mCapabilities;
        private final Uri mFolderListUri;
        private final Uri mSearchUri;
        private final Uri mAccountFromAddressesUri;
        private final Uri mSaveDraftUri;
        private final Uri mSendMailUri;
        private final Uri mExpungeMessageUri;
        private final Uri mUndoUri;
        private final Uri mSettingsIntentUri;
        private final Uri mSettingsQueryUri;
        private final Uri mHelpIntentUri;
        private final int mSyncStatus;
        private final Uri mComposeIntentUri;

        public CachedAccount(long id, String name, Uri uri, long capabilities,
                Uri folderListUri, Uri searchUri, Uri fromAddressesUri,
                Uri saveDraftUri, Uri sendMailUri, Uri expungeMessageUri, Uri undoUri,
                Uri settingsIntentUri, Uri settingsQueryUri, Uri helpIntentUri, int syncStatus,
                Uri composeIntentUri) {
            mId = id;
            mName = name;
            mUri = uri;
            mCapabilities = capabilities;
            mFolderListUri = folderListUri;
            mSearchUri = searchUri;
            mAccountFromAddressesUri = fromAddressesUri;
            mSaveDraftUri = saveDraftUri;
            mSendMailUri = sendMailUri;
            mExpungeMessageUri = expungeMessageUri;
            mUndoUri = undoUri;
            mSettingsIntentUri = settingsIntentUri;
            mSettingsQueryUri = settingsQueryUri;
            mHelpIntentUri = helpIntentUri;
            mSyncStatus = syncStatus;
            mComposeIntentUri = composeIntentUri;
        }

        public CachedAccount(Account acct) {
            mId = 0;
            mName = acct.name;
            mUri = acct.uri;
            mCapabilities = acct.capabilities;
            mFolderListUri = acct.folderListUri;
            mSearchUri = acct.searchUri;
            mAccountFromAddressesUri = acct.accountFromAddressesUri;
            mSaveDraftUri = acct.saveDraftUri;
            mSendMailUri = acct.sendMessageUri;
            mExpungeMessageUri = acct.expungeMessageUri;
            mUndoUri = acct.undoUri;
            mSettingsIntentUri = acct.settingIntentUri;
            mSettingsQueryUri = acct.settingQueryUri;
            mHelpIntentUri = acct.helpIntentUri;
            mSyncStatus = acct.syncStatus;
            mComposeIntentUri = acct.composeIntentUri;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }

            if ((o == null) || (o.getClass() != this.getClass())) {
                return false;
            }

            CachedAccount other = (CachedAccount) o;
            return mId == other.mId && TextUtils.equals(mName, other.mName) &&
                    mUri.equals(other.mUri) && mCapabilities == other.mCapabilities &&
                    mFolderListUri.equals(other.mFolderListUri) &&
                    mSearchUri.equals(other.mSearchUri) &&
                    mAccountFromAddressesUri.equals(other.mAccountFromAddressesUri) &&
                    mSaveDraftUri.equals(other.mSaveDraftUri) &&
                    mSendMailUri.equals(other.mSendMailUri) &&
                    mExpungeMessageUri.equals(other.mExpungeMessageUri) &&
                    mUndoUri.equals(other.mUndoUri) &&
                    mSettingsIntentUri.equals(other.mSettingsIntentUri) &&
                    mSettingsQueryUri.equals(other.mSettingsQueryUri) &&
                    mHelpIntentUri.equals(other.mHelpIntentUri) &&
                    (mSyncStatus == other.mSyncStatus) &&
                    mComposeIntentUri.equals(other.mComposeIntentUri);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mId, mName, mUri, mCapabilities, mFolderListUri, mSearchUri,
                    mAccountFromAddressesUri, mSaveDraftUri, mSendMailUri, mExpungeMessageUri,
                    mUndoUri, mSettingsIntentUri, mSettingsQueryUri, mHelpIntentUri, mSyncStatus,
                    mComposeIntentUri);
        }
    }
}