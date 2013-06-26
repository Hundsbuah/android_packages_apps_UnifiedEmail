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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.android.emailcommon.internet.MimeUtility;
import com.android.emailcommon.mail.MessagingException;
import com.android.emailcommon.mail.Part;
import com.android.mail.browse.MessageAttachmentBar;
import com.android.mail.providers.UIProvider.AttachmentColumns;
import com.android.mail.providers.UIProvider.AttachmentDestination;
import com.android.mail.providers.UIProvider.AttachmentState;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.MimeType;
import com.android.mail.utils.Utils;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;

public class Attachment implements Parcelable {
    public static final String LOG_TAG = LogTag.getLogTag();
    /**
     * Workaround for b/8070022 so that appending a null partId to the end of a
     * uri wouldn't remove the trailing backslash
     */
    public static final String EMPTY_PART_ID = "empty";

    /**
     * Part id of the attachment.
     */
    public String partId;

    /**
     * Attachment file name. See {@link AttachmentColumns#NAME}.
     */
    private String name;

    /**
     * Attachment size in bytes. See {@link AttachmentColumns#SIZE}.
     */
    public int size;

    /**
     * The provider-generated URI for this Attachment. Must be globally unique.
     * For local attachments generated by the Compose UI prior to send/save,
     * this field will be null.
     *
     * @see AttachmentColumns#URI
     */
    public Uri uri;

    /**
     * MIME type of the file.
     *
     * @see AttachmentColumns#CONTENT_TYPE
     */
    private String contentType;
    private String inferredContentType;

    /**
     * @see AttachmentColumns#STATE
     */
    public int state;

    /**
     * @see AttachmentColumns#DESTINATION
     */
    public int destination;

    /**
     * @see AttachmentColumns#DOWNLOADED_SIZE
     */
    public int downloadedSize;

    /**
     * Shareable, openable uri for this attachment
     * <p>
     * content:// Gmail.getAttachmentDefaultUri() if origin is SERVER_ATTACHMENT
     * <p>
     * content:// uri pointing to the content to be uploaded if origin is
     * LOCAL_FILE
     * <p>
     * file:// uri pointing to an EXTERNAL apk file. The package manager only
     * handles file:// uris not content:// uris. We do the same workaround in
     * {@link MessageAttachmentBar#onClick(android.view.View)} and
     * UiProvider#getUiAttachmentsCursorForUIAttachments().
     *
     * @see AttachmentColumns#CONTENT_URI
     */
    public Uri contentUri;

    /**
     * Might be null.
     *
     * @see AttachmentColumns#THUMBNAIL_URI
     */
    public Uri thumbnailUri;

    /**
     * Might be null.
     *
     * @see AttachmentColumns#PREVIEW_INTENT_URI
     */
    public Uri previewIntentUri;

    /**
     * Might be null. JSON string.
     *
     * @see AttachmentColumns#PROVIDER_DATA
     */
    public String providerData;

    private transient Uri mIdentifierUri;

    /**
     * True if this attachment can be downloaded again.
     */
    private boolean supportsDownloadAgain;

    public Attachment() {
    }

    public Attachment(Parcel in) {
        name = in.readString();
        size = in.readInt();
        uri = in.readParcelable(null);
        contentType = in.readString();
        state = in.readInt();
        destination = in.readInt();
        downloadedSize = in.readInt();
        contentUri = in.readParcelable(null);
        thumbnailUri = in.readParcelable(null);
        previewIntentUri = in.readParcelable(null);
        providerData = in.readString();
        supportsDownloadAgain = in.readInt() == 1;
    }

    public Attachment(Cursor cursor) {
        if (cursor == null) {
            return;
        }

        name = cursor.getString(cursor.getColumnIndex(AttachmentColumns.NAME));
        size = cursor.getInt(cursor.getColumnIndex(AttachmentColumns.SIZE));
        uri = Uri.parse(cursor.getString(cursor.getColumnIndex(AttachmentColumns.URI)));
        contentType = cursor.getString(cursor.getColumnIndex(AttachmentColumns.CONTENT_TYPE));
        state = cursor.getInt(cursor.getColumnIndex(AttachmentColumns.STATE));
        destination = cursor.getInt(cursor.getColumnIndex(AttachmentColumns.DESTINATION));
        downloadedSize = cursor.getInt(cursor.getColumnIndex(AttachmentColumns.DOWNLOADED_SIZE));
        contentUri = parseOptionalUri(
                cursor.getString(cursor.getColumnIndex(AttachmentColumns.CONTENT_URI)));
        thumbnailUri = parseOptionalUri(
                cursor.getString(cursor.getColumnIndex(AttachmentColumns.THUMBNAIL_URI)));
        previewIntentUri = parseOptionalUri(
                cursor.getString(cursor.getColumnIndex(AttachmentColumns.PREVIEW_INTENT_URI)));
        providerData = cursor.getString(cursor.getColumnIndex(AttachmentColumns.PROVIDER_DATA));
        supportsDownloadAgain = cursor.getInt(
                cursor.getColumnIndex(AttachmentColumns.SUPPORTS_DOWNLOAD_AGAIN)) == 1;
    }

    public Attachment(JSONObject srcJson) {
        name = srcJson.optString(AttachmentColumns.NAME, null);
        size = srcJson.optInt(AttachmentColumns.SIZE);
        uri = parseOptionalUri(srcJson, AttachmentColumns.URI);
        contentType = srcJson.optString(AttachmentColumns.CONTENT_TYPE, null);
        state = srcJson.optInt(AttachmentColumns.STATE);
        destination = srcJson.optInt(AttachmentColumns.DESTINATION);
        downloadedSize = srcJson.optInt(AttachmentColumns.DOWNLOADED_SIZE);
        contentUri = parseOptionalUri(srcJson, AttachmentColumns.CONTENT_URI);
        thumbnailUri = parseOptionalUri(srcJson, AttachmentColumns.THUMBNAIL_URI);
        previewIntentUri = parseOptionalUri(srcJson, AttachmentColumns.PREVIEW_INTENT_URI);
        providerData = srcJson.optString(AttachmentColumns.PROVIDER_DATA);
        supportsDownloadAgain = srcJson.optBoolean(AttachmentColumns.SUPPORTS_DOWNLOAD_AGAIN, true);
    }

    /**
     * Constructor for use when creating attachments in eml files.
     */
    public Attachment(Context context, Part part, Uri emlFileUri, String messageId, String partId) {
        try {
            // Transfer fields from mime format to provider format
            final String contentTypeHeader = MimeUtility.unfoldAndDecode(part.getContentType());
            name = MimeUtility.getHeaderParameter(contentTypeHeader, "name");
            if (name == null) {
                final String contentDisposition =
                        MimeUtility.unfoldAndDecode(part.getDisposition());
                name = MimeUtility.getHeaderParameter(contentDisposition, "filename");
            }

            contentType = MimeType.inferMimeType(name, part.getMimeType());
            uri = EmlAttachmentProvider.getAttachmentUri(emlFileUri, messageId, partId);
            contentUri = uri;
            thumbnailUri = uri;
            previewIntentUri = null;
            state = AttachmentState.SAVED;
            providerData = null;
            supportsDownloadAgain = false;
            destination = AttachmentDestination.CACHE;

            // insert attachment into content provider so that we can open the file
            final ContentResolver resolver = context.getContentResolver();
            resolver.insert(uri, toContentValues());

            // save the file in the cache
            try {
                final InputStream in = part.getBody().getInputStream();
                final OutputStream out = resolver.openOutputStream(uri, "rwt");
                size = IOUtils.copy(in, out);
                downloadedSize = size;
                in.close();
                out.close();
            } catch (FileNotFoundException e) {
                LogUtils.e(LOG_TAG, e, "Error in writing attachment to cache");
            } catch (IOException e) {
                LogUtils.e(LOG_TAG, e, "Error in writing attachment to cache");
            }
            // perform a second insert to put the updated size and downloaded size values in
            resolver.insert(uri, toContentValues());
        } catch (MessagingException e) {
            LogUtils.e(LOG_TAG, e, "Error parsing eml attachment");
        }
    }

    /**
     * Create an attachment from a {@link ContentValues} object.
     * The keys should be {@link AttachmentColumns}.
     */
    public Attachment(ContentValues values) {
        name = values.getAsString(AttachmentColumns.NAME);
        size = values.getAsInteger(AttachmentColumns.SIZE);
        uri = parseOptionalUri(values.getAsString(AttachmentColumns.URI));
        contentType = values.getAsString(AttachmentColumns.CONTENT_TYPE);
        state = values.getAsInteger(AttachmentColumns.STATE);
        destination = values.getAsInteger(AttachmentColumns.DESTINATION);
        downloadedSize = values.getAsInteger(AttachmentColumns.DOWNLOADED_SIZE);
        contentUri = parseOptionalUri(values.getAsString(AttachmentColumns.CONTENT_URI));
        thumbnailUri = parseOptionalUri(values.getAsString(AttachmentColumns.THUMBNAIL_URI));
        previewIntentUri =
                parseOptionalUri(values.getAsString(AttachmentColumns.PREVIEW_INTENT_URI));
        providerData = values.getAsString(AttachmentColumns.PROVIDER_DATA);
        supportsDownloadAgain = values.getAsBoolean(AttachmentColumns.SUPPORTS_DOWNLOAD_AGAIN);
    }

    /**
     * Returns the various attachment fields in a {@link ContentValues} object.
     * The keys for each field should be {@link AttachmentColumns}.
     */
    public ContentValues toContentValues() {
        final ContentValues values = new ContentValues(12);

        values.put(AttachmentColumns.NAME, name);
        values.put(AttachmentColumns.SIZE, size);
        values.put(AttachmentColumns.URI, uri.toString());
        values.put(AttachmentColumns.CONTENT_TYPE, contentType);
        values.put(AttachmentColumns.STATE, state);
        values.put(AttachmentColumns.DESTINATION, destination);
        values.put(AttachmentColumns.DOWNLOADED_SIZE, downloadedSize);
        values.put(AttachmentColumns.CONTENT_URI, contentUri.toString());
        values.put(AttachmentColumns.THUMBNAIL_URI, thumbnailUri.toString());
        values.put(AttachmentColumns.PREVIEW_INTENT_URI,
                previewIntentUri == null ? null : previewIntentUri.toString());
        values.put(AttachmentColumns.PROVIDER_DATA, providerData);
        values.put(AttachmentColumns.SUPPORTS_DOWNLOAD_AGAIN, supportsDownloadAgain);

        return values;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeInt(size);
        dest.writeParcelable(uri, flags);
        dest.writeString(contentType);
        dest.writeInt(state);
        dest.writeInt(destination);
        dest.writeInt(downloadedSize);
        dest.writeParcelable(contentUri, flags);
        dest.writeParcelable(thumbnailUri, flags);
        dest.writeParcelable(previewIntentUri, flags);
        dest.writeString(providerData);
        dest.writeInt(supportsDownloadAgain ? 1 : 0);
    }

    public JSONObject toJSON() throws JSONException {
        final JSONObject result = new JSONObject();

        result.put(AttachmentColumns.NAME, name);
        result.put(AttachmentColumns.SIZE, size);
        result.put(AttachmentColumns.URI, stringify(uri));
        result.put(AttachmentColumns.CONTENT_TYPE, contentType);
        result.put(AttachmentColumns.STATE, state);
        result.put(AttachmentColumns.DESTINATION, destination);
        result.put(AttachmentColumns.DOWNLOADED_SIZE, downloadedSize);
        result.put(AttachmentColumns.CONTENT_URI, stringify(contentUri));
        result.put(AttachmentColumns.THUMBNAIL_URI, stringify(thumbnailUri));
        result.put(AttachmentColumns.PREVIEW_INTENT_URI, stringify(previewIntentUri));
        result.put(AttachmentColumns.PROVIDER_DATA, providerData);
        result.put(AttachmentColumns.SUPPORTS_DOWNLOAD_AGAIN, supportsDownloadAgain);

        return result;
    }

    @Override
    public String toString() {
        try {
            final JSONObject jsonObject = toJSON();
            // Add some additional fields that are helpful when debugging issues
            jsonObject.put("partId", partId);
            try {
                // pretty print the provider data
                jsonObject.put(AttachmentColumns.PROVIDER_DATA, new JSONObject(providerData));
            } catch (JSONException e) {
            }
            return jsonObject.toString(4);
        } catch (JSONException e) {
            LogUtils.e(LOG_TAG, e, "JSONException in toString");
            return super.toString();
        }
    }

    private static String stringify(Object object) {
        return object != null ? object.toString() : null;
    }

    protected static Uri parseOptionalUri(String uriString) {
        return uriString == null ? null : Uri.parse(uriString);
    }

    protected static Uri parseOptionalUri(JSONObject srcJson, String key) {
        final String uriStr = srcJson.optString(key, null);
        return uriStr == null ? null : Uri.parse(uriStr);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public boolean isPresentLocally() {
        return state == AttachmentState.SAVED;
    }

    public boolean canSave() {
        return !isSavedToExternal() && !isInstallable() && !MimeType.isBlocked(getContentType());
    }

    public boolean canShare() {
        return isPresentLocally() && contentUri != null;
    }

    public boolean isDownloading() {
        return state == AttachmentState.DOWNLOADING || state == AttachmentState.PAUSED;
    }

    public boolean isSavedToExternal() {
        return state == AttachmentState.SAVED && destination == AttachmentDestination.EXTERNAL;
    }

    public boolean isInstallable() {
        return MimeType.isInstallable(getContentType());
    }

    public boolean shouldShowProgress() {
        return (state == AttachmentState.DOWNLOADING || state == AttachmentState.PAUSED)
                && size > 0 && downloadedSize > 0 && downloadedSize <= size;
    }

    public boolean isDownloadFailed() {
        return state == AttachmentState.FAILED;
    }

    public boolean isDownloadFinishedOrFailed() {
        return state == AttachmentState.FAILED || state == AttachmentState.SAVED;
    }

    public boolean supportsDownloadAgain() {
        return supportsDownloadAgain;
    }

    public boolean canPreview() {
        return previewIntentUri != null;
    }

    /**
     * Returns a stable identifier URI for this attachment. TODO: make the uri
     * field stable, and put provider-specific opaque bits and bobs elsewhere
     */
    public Uri getIdentifierUri() {
        if (Utils.isEmpty(mIdentifierUri)) {
            mIdentifierUri = Utils.isEmpty(uri) ?
                    (Utils.isEmpty(contentUri) ? Uri.EMPTY : contentUri)
                    : uri.buildUpon().clearQuery().build();
        }
        return mIdentifierUri;
    }

    public String getContentType() {
        if (TextUtils.isEmpty(inferredContentType)) {
            inferredContentType = MimeType.inferMimeType(name, contentType);
        }
        return inferredContentType;
    }

    public void setContentType(String contentType) {
        if (!TextUtils.equals(this.contentType, contentType)) {
            this.inferredContentType = null;
            this.contentType = contentType;
        }
    }

    public String getName() {
        return name;
    }

    public boolean setName(String name) {
        if (!TextUtils.equals(this.name, name)) {
            this.inferredContentType = null;
            this.name = name;
            return true;
        }
        return false;
    }

    /**
     * Sets the attachment state. Side effect: sets downloadedSize
     */
    public void setState(int state) {
        this.state = state;
        if (state == AttachmentState.FAILED || state == AttachmentState.NOT_SAVED) {
            this.downloadedSize = 0;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (o == null || o.getClass() != this.getClass()) {
            return false;
        }

        Attachment other = (Attachment) o;
        return TextUtils.equals(other.name, this.name) && other.size == this.size
                && Objects.equal(other.uri, this.uri)
                && TextUtils.equals(other.contentType, this.contentType)
                && other.state == this.state && other.destination == this.destination
                && other.downloadedSize == this.downloadedSize
                && Objects.equal(other.contentUri, this.contentUri)
                && Objects.equal(other.thumbnailUri, this.thumbnailUri)
                && Objects.equal(other.previewIntentUri, this.previewIntentUri)
                && TextUtils.equals(other.providerData, this.providerData);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, size, uri, contentType, state, destination, downloadedSize,
                contentUri, thumbnailUri, previewIntentUri, providerData);
    }

    public static String toJSONArray(Collection<? extends Attachment> attachments) {
        if (attachments == null) {
            return null;
        }
        final JSONArray result = new JSONArray();
        try {
            for (Attachment attachment : attachments) {
                result.put(attachment.toJSON());
            }
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
        return result.toString();
    }

    public static List<Attachment> fromJSONArray(String jsonArrayStr) {
        final List<Attachment> results = Lists.newArrayList();
        if (jsonArrayStr != null) {
            try {
                final JSONArray arr = new JSONArray(jsonArrayStr);

                for (int i = 0; i < arr.length(); i++) {
                    results.add(new Attachment(arr.getJSONObject(i)));
                }

            } catch (JSONException e) {
                throw new IllegalArgumentException(e);
            }
        }
        return results;
    }

    private static final String SERVER_ATTACHMENT = "SERVER_ATTACHMENT";
    private static final String LOCAL_FILE = "LOCAL_FILE";

    public String toJoinedString() {
        return TextUtils.join("|", Lists.newArrayList(
                partId == null ? "" : partId,
                name == null ? "" : name.replaceAll("[|\n]", ""),
                getContentType(),
                String.valueOf(size),
                getContentType(),
                contentUri != null ? SERVER_ATTACHMENT : LOCAL_FILE,
                contentUri));
    }

    public static final Creator<Attachment> CREATOR = new Creator<Attachment>() {
            @Override
        public Attachment createFromParcel(Parcel source) {
            return new Attachment(source);
        }

            @Override
        public Attachment[] newArray(int size) {
            return new Attachment[size];
        }
    };
}
