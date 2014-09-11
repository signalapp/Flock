/*
 * *
 *  Copyright (C) 2014 Open Whisper Systems
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 * /
 */

package org.anhonesteffort.flock.sync.addressbook;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.util.Log;

import com.google.common.base.Optional;
import ezvcard.VCard;
import ezvcard.property.Photo;

import org.anhonesteffort.flock.webdav.carddav.CardDavConstants;
import org.anhonesteffort.flock.sync.AbstractLocalComponentCollection;
import org.anhonesteffort.flock.webdav.ComponentETagPair;
import org.anhonesteffort.flock.webdav.InvalidComponentException;

import java.util.LinkedList;
import java.util.List;

/**
 * Programmer: rhodey
 */
public class LocalContactCollection extends AbstractLocalComponentCollection<VCard> {

  private static final String TAG = "org.anhonesteffort.flock.sync.addressbook.LocalContactCollection";

  private static final String PREFERENCES_NAME            = "org.anhonesteffort.flock.sync.addressbook.LocalContactCollection";
  private static final String KEY_PREFIX_COLLECTION_C_TAG = "LocalContactCollection.KEY_PREFIX_COLLECTION_C_TAG";
  private static final String KEY_COLLECTION_DISPLAY_NAME = "LocalContactCollection.KEY_COLLECTION_DISPLAY_NAME";

  private Context context;

  public LocalContactCollection(Context               context,
                                ContentProviderClient client,
                                Account               account,
                                String                remotePath)
  {
    super(client, account, remotePath, 1L); // hack :D limit one collection
    this.context = context;
  }

  private String getKeyForCTag() {
    return KEY_PREFIX_COLLECTION_C_TAG.concat(getPath());
  }

  public static Uri getSyncAdapterUri(Uri base, Account account) {
    return base.buildUpon()
        .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
        .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER,    "true")
        .build();
  }

  @Override
  protected Uri getSyncAdapterUri(Uri base) {
    return getSyncAdapterUri(base, account);
  }

  @Override
  protected Uri handleAddAccountQueryParams(Uri uri) {
    return uri.buildUpon()
        .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
        .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
        .build();
  }

  @Override
  protected Uri getUriForComponents() {
    return getSyncAdapterUri(ContactsContract.RawContacts.CONTENT_URI);
  }

  private Uri getUriForData() {
    return getSyncAdapterUri(ContactsContract.Data.CONTENT_URI);
  }

  @Override
  protected String getColumnNameCollectionLocalId() {
    return "1"; // hack :D
  }

  @Override
  protected String getColumnNameComponentLocalId() {
    return ContactsContract.RawContacts._ID;
  }

  protected String getColumnNameComponentDataLocalId() {
    return ContactsContract.Data.RAW_CONTACT_ID;
  }

  @Override
  protected String getColumnNameComponentUid() {
    return ContactsContract.RawContacts.SOURCE_ID;
  }

  @Override
  protected String getColumnNameComponentETag() {
    return ContactsContract.RawContacts.SYNC1;
  }

  @Override
  protected String getColumnNameDirty() {
    return ContactsContract.RawContacts.DIRTY;
  }

  @Override
  protected String getColumnNameDeleted() {
    return ContactsContract.RawContacts.DELETED;
  }

  @Override
  protected String getColumnNameQueuedForMigration() {
    return ContactsContract.RawContacts.SYNC2;
  }

  @Override
  public Optional<String> getDisplayName() {
    SharedPreferences preferences = context.getSharedPreferences(PREFERENCES_NAME,
        Context.MODE_MULTI_PROCESS);

    return Optional.fromNullable(preferences.getString(KEY_COLLECTION_DISPLAY_NAME, null));
  }

  @Override
  public void setDisplayName(String displayName) {
    SharedPreferences preferences = context.getSharedPreferences(PREFERENCES_NAME,
        Context.MODE_MULTI_PROCESS);

    preferences.edit().putString(KEY_COLLECTION_DISPLAY_NAME, displayName).commit();
  }

  @Override
  public Optional<String> getCTag() {
    SharedPreferences preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_MULTI_PROCESS);
    return Optional.fromNullable(preferences.getString(getKeyForCTag(), null));
  }

  @Override
  public void setCTag(String cTag) {
    SharedPreferences preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_MULTI_PROCESS);
    preferences.edit().putString(getKeyForCTag(), cTag).commit();
  }

  private void addStructuredNames(Long rawContactId, VCard vCard)
    throws RemoteException
  {
    String   SELECTION      = getColumnNameComponentDataLocalId() + "=? " +
                              "AND " + ContactsContract.Data.MIMETYPE + "=?";
    String[] SELECTION_ARGS = new String[]{
        rawContactId.toString(),
        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
        };

    Cursor cursor = client.query(getUriForData(),
                                 ContactFactory.getProjectionForStructuredName(),
                                 SELECTION,
                                 SELECTION_ARGS,
                                 null);

    while (cursor.moveToNext()) {
      ContentValues structuredNameValues = ContactFactory.getValuesForStructuredName(cursor);
      ContactFactory.addStructuredName(vCard, structuredNameValues);
    }
    cursor.close();
  }

  private void addPhoneNumbers(Long rawContactId, VCard vCard)
      throws InvalidComponentException, RemoteException
  {
    String   SELECTION      = getColumnNameComponentDataLocalId() + "=? " +
                              "AND " + ContactsContract.Data.MIMETYPE + "=?";
    String[] SELECTION_ARGS = new String[]{
        rawContactId.toString(),
        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
        };

    Cursor cursor = client.query(getUriForData(),
                                 ContactFactory.getProjectionForPhoneNumber(),
                                 SELECTION,
                                 SELECTION_ARGS,
                                 null);

    while (cursor.moveToNext()) {
      ContentValues phoneNumberValues = ContactFactory.getValuesForPhoneNumber(cursor);
      ContactFactory.addPhoneNumber(getPath(), vCard, phoneNumberValues);
    }
    cursor.close();
  }

  private void addEmailAddresses(Long rawContactId, VCard vCard)
      throws InvalidComponentException, RemoteException
  {
    String   SELECTION      = getColumnNameComponentDataLocalId() + "=? " +
                              "AND " + ContactsContract.Data.MIMETYPE + "=?";
    String[] SELECTION_ARGS = new String[]{
        rawContactId.toString(),
        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
    };

    Cursor cursor = client.query(getUriForData(),
                                 ContactFactory.getProjectionForEmailAddress(),
                                 SELECTION,
                                 SELECTION_ARGS,
                                 null);

    while (cursor.moveToNext()) {
      ContentValues emailAddressValues = ContactFactory.getValuesForEmailAddress(cursor);
      ContactFactory.addEmailAddress(getPath(), vCard, emailAddressValues);
    }
    cursor.close();
  }

  private Optional<Photo> getThumbnailPhoto(Long rawContactId)
      throws InvalidComponentException, RemoteException
  {
    String   SELECTION      = getColumnNameComponentDataLocalId()     + "=? " +
                              "AND " + ContactsContract.Data.MIMETYPE + "=?";
    String[] SELECTION_ARGS = new String[]{
        rawContactId.toString(),
        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
    };

    Optional<Photo> thumbnailPhoto = Optional.absent();
    Cursor          cursor         = client.query(getUriForData(),
                                                  ContactFactory.getProjectionForThumbnailPhoto(),
                                                  SELECTION,
                                                  SELECTION_ARGS,
                                                  null);

    if (cursor.moveToNext()) {
      ContentValues photoValues    = ContactFactory.getValuesForThumbnailPhoto(cursor);
                    thumbnailPhoto = ContactFactory.getPhotoForThumbnailValues(photoValues);
    }

    cursor.close();
    return thumbnailPhoto;
  }

  private void addPhotos(Long rawContactId, VCard vCard)
      throws InvalidComponentException, RemoteException
  {
    Optional<Photo> photo = ContactFactory.getDisplayPhoto(getPath(), client, rawContactId);
    if (!photo.isPresent())
      photo = getThumbnailPhoto(rawContactId);

    if (photo.isPresent())
      vCard.addPhoto(photo.get());
  }

  private void addOrganizations(Long rawContactId, VCard vCard)
      throws RemoteException
  {
    String   SELECTION      = getColumnNameComponentDataLocalId() + "=? " +
                              "AND " + ContactsContract.Data.MIMETYPE + "=?";
    String[] SELECTION_ARGS = new String[]{
        rawContactId.toString(),
        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
        };

    Cursor cursor = client.query(getUriForData(),
                                 ContactFactory.getProjectionForOrganization(),
                                 SELECTION,
                                 SELECTION_ARGS,
                                 null);

    while (cursor.moveToNext()) {
      ContentValues organizationValues = ContactFactory.getValuesForOrganization(cursor);
      ContactFactory.addOrganizer(vCard, organizationValues);
    }
    cursor.close();
  }

  private void addInstantMessaging(Long rawContactId, VCard vCard)
      throws InvalidComponentException, RemoteException
  {
    String   SELECTION      = getColumnNameComponentDataLocalId() + "=? " +
                              "AND " + ContactsContract.Data.MIMETYPE + "=?";
    String[] SELECTION_ARGS = new String[]{
        rawContactId.toString(),
        ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE
        };

    Cursor cursor = client.query(getUriForData(),
                                 ContactFactory.getProjectionForInstantMessaging(),
                                 SELECTION,
                                 SELECTION_ARGS,
                                 null);

    while (cursor.moveToNext()) {
      ContentValues instantMessagingValues = ContactFactory.getValuesForInstantMessaging(cursor);
      ContactFactory.addInstantMessaging(getPath(), vCard, instantMessagingValues);
    }
    cursor.close();
  }

  private void addNickNames(Long rawContactId, VCard vCard)
      throws RemoteException
  {
    String   SELECTION      = getColumnNameComponentDataLocalId() + "=? " +
                              "AND " + ContactsContract.Data.MIMETYPE + "=?";
    String[] SELECTION_ARGS = new String[]{
        rawContactId.toString(),
        ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE
        };

    Cursor cursor = client.query(getUriForData(),
                                 ContactFactory.getProjectionForNickName(),
                                 SELECTION,
                                 SELECTION_ARGS,
                                 null);

    while (cursor.moveToNext()) {
      ContentValues nickNameValues = ContactFactory.getValuesForNickName(cursor);
      ContactFactory.addNickName(vCard, nickNameValues);
    }
    cursor.close();
  }

  private void addNotes(Long rawContactId, VCard vCard)
      throws RemoteException
  {
    String   SELECTION      = getColumnNameComponentDataLocalId() + "=? " +
                              "AND " + ContactsContract.Data.MIMETYPE + "=?";
    String[] SELECTION_ARGS = new String[]{
        rawContactId.toString(),
        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
        };

    Cursor cursor = client.query(getUriForData(),
                                 ContactFactory.getProjectionForNote(),
                                 SELECTION,
                                 SELECTION_ARGS,
                                 null);

    while (cursor.moveToNext()) {
      ContentValues noteValues = ContactFactory.getValuesForNote(cursor);
      ContactFactory.addNote(vCard, noteValues);
    }
    cursor.close();
  }

  private void addPostalAddresses(Long rawContactId, VCard vCard)
      throws InvalidComponentException, RemoteException
  {
    String   SELECTION      = getColumnNameComponentDataLocalId() + "=? " +
                              "AND " + ContactsContract.Data.MIMETYPE + "=?";
    String[] SELECTION_ARGS = new String[]{
        rawContactId.toString(),
        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE
        };

    Cursor cursor = client.query(getUriForData(),
                                 ContactFactory.getProjectionForPostalAddress(),
                                 SELECTION,
                                 SELECTION_ARGS,
                                 null);

    while (cursor.moveToNext()) {
      ContentValues postalAddressValues = ContactFactory.getValuesForPostalAddress(cursor);
      ContactFactory.addPostalAddress(getPath(), vCard, postalAddressValues);
    }
    cursor.close();
  }

  private void addWebsites(Long rawContactId, VCard vCard)
      throws InvalidComponentException, RemoteException
  {
    String   SELECTION      = getColumnNameComponentDataLocalId() + "=? " +
                              "AND " + ContactsContract.Data.MIMETYPE + "=?";
    String[] SELECTION_ARGS = new String[]{
        rawContactId.toString(),
        ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE
        };

    Cursor cursor = client.query(getUriForData(),
                                 ContactFactory.getProjectionForWebsite(),
                                 SELECTION,
                                 SELECTION_ARGS,
                                 null);

    while (cursor.moveToNext()) {
      ContentValues websiteValues = ContactFactory.getValuesForWebsite(cursor);
      ContactFactory.addWebsite(getPath(), vCard, websiteValues);
    }
    cursor.close();
  }

  private void addEvents(Long rawContactId, VCard vCard)
      throws InvalidComponentException, RemoteException
  {
    String   SELECTION      = getColumnNameComponentDataLocalId() + "=? " +
                              "AND " + ContactsContract.Data.MIMETYPE + "=?";
    String[] SELECTION_ARGS = new String[]{
        rawContactId.toString(),
        ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE
        };

    Cursor cursor = client.query(getUriForData(),
                                 ContactFactory.getProjectionForEvent(),
                                 SELECTION,
                                 SELECTION_ARGS,
                                 null);

    while (cursor.moveToNext()) {
      ContentValues eventValues = ContactFactory.getValuesForEvent(cursor);
      ContactFactory.addEvent(getPath(), vCard, eventValues);
    }
    cursor.close();
  }

  private void addSipAddresses(Long rawContactId, VCard vCard)
      throws RemoteException
  {
    String   SELECTION      = getColumnNameComponentDataLocalId() + "=? " +
                              "AND " + ContactsContract.Data.MIMETYPE + "=?";
    String[] SELECTION_ARGS = new String[]{
        rawContactId.toString(),
        ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE
        };

    Cursor cursor = client.query(getUriForData(),
                                 ContactFactory.getProjectionForSipAddress(),
                                 SELECTION,
                                 SELECTION_ARGS,
                                 null);

    while (cursor.moveToNext()) {
      ContentValues sipAddressValues = ContactFactory.getValuesForSipAddress(cursor);
      ContactFactory.addSipAddress(vCard, sipAddressValues);
    }
    cursor.close();
  }

  private List<Long> getGroupIdsForContact(Long rawContactId)
      throws RemoteException
  {
    String   SELECTION      = getColumnNameComponentDataLocalId() + "=? " +
                              "AND " + ContactsContract.Data.MIMETYPE + "=?";
    String[] SELECTION_ARGS = new String[] {
        rawContactId.toString(),
        ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE
    };

    List<Long> groupIds = new LinkedList<Long>();
    Cursor     cursor   = client.query(getUriForData(),
        ContactFactory.getProjectionForGroupMembership(),
        SELECTION,
        SELECTION_ARGS,
        null);

    while (cursor.moveToNext())
      groupIds.add(ContactFactory.getIdForGroupMembership(cursor));

    cursor.close();
    return groupIds;
  }

  private boolean isContactWithoutGroupVisible()
      throws RemoteException
  {
    boolean contactWithoutGroupVisible = true;
    Cursor  cursor                     = client.query(getSyncAdapterUri(ContactsContract.Settings.CONTENT_URI),
        new String[] {
            ContactsContract.Settings.UNGROUPED_VISIBLE,
        },
        null,
        null,
        null);

    if (cursor.moveToNext())
      contactWithoutGroupVisible = cursor.getInt(0) != 0;

    cursor.close();
    return contactWithoutGroupVisible;
  }

  private void addInvisibleProperty(Long rawContactId, VCard vCard)
      throws RemoteException
  {
    boolean isMemberOfGroup = getGroupIdsForContact(rawContactId).size() > 0;

    if (!isMemberOfGroup && !isContactWithoutGroupVisible())
      ContactFactory.addInvisibleProperty(vCard);
  }

  private void buildContact(Long rawContactId, VCard vCard)
      throws InvalidComponentException, RemoteException
  {
    addStructuredNames(  rawContactId, vCard);
    addPhoneNumbers(     rawContactId, vCard);
    addEmailAddresses(   rawContactId, vCard);
    addPhotos(           rawContactId, vCard);
    addOrganizations(    rawContactId, vCard);
    addInstantMessaging( rawContactId, vCard);
    addNickNames(        rawContactId, vCard);
    addNotes(            rawContactId, vCard);
    addPostalAddresses(  rawContactId, vCard);
    addWebsites(         rawContactId, vCard);
    addEvents(           rawContactId, vCard);
    addSipAddresses(     rawContactId, vCard);
    addInvisibleProperty(rawContactId, vCard);
  }

  @Override
  public Optional<VCard> getComponent(Long rawContactId)
      throws InvalidComponentException, RemoteException
  {
    Cursor cursor = client.query(ContentUris.withAppendedId(getUriForComponents(), rawContactId),
                                 ContactFactory.getProjectionForRawContact(),
                                 null,
                                 null,
                                 null);

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    if (cursor.moveToNext()) {
      ContentValues            rawContactValues = ContactFactory.getValuesForRawContact(cursor);
      ComponentETagPair<VCard> vCard            = ContactFactory.getVCard(rawContactValues);

      buildContact(rawContactId, vCard.getComponent());
      cursor.close();
      return Optional.of(vCard.getComponent());
    }

    cursor.close();
    return Optional.absent();
  }

  @Override
  public Optional<ComponentETagPair<VCard>> getComponent(String uid)
      throws InvalidComponentException, RemoteException
  {
    String   SELECTION      = getColumnNameComponentUid() + "=?";
    String[] SELECTION_ARGS = new String[]{uid};


    Cursor cursor = client.query(getUriForComponents(),
                                 ContactFactory.getProjectionForRawContact(),
                                 SELECTION,
                                 SELECTION_ARGS,
                                 null);

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    if (cursor.moveToNext()) {
      ContentValues            rawContactValues = ContactFactory.getValuesForRawContact(cursor);
      Long                     rawContactId     = rawContactValues.getAsLong(getColumnNameComponentLocalId());
      ComponentETagPair<VCard> vCard            = ContactFactory.getVCard(rawContactValues);

      buildContact(rawContactId, vCard.getComponent());
      cursor.close();

      return Optional.of(vCard);
    }

    cursor.close();
    return Optional.absent();
  }

  @Override
  public List<ComponentETagPair<VCard>> getComponents()
      throws InvalidComponentException, RemoteException
  {
    List<ComponentETagPair<VCard>> vCards = new LinkedList<ComponentETagPair<VCard>>();


    Cursor cursor = client.query(getUriForComponents(),
                                 ContactFactory.getProjectionForRawContact(),
                                 null, null, null);

    if (cursor == null)
      throw new RemoteException("Content provider client gave us a null cursor!");

    while (cursor.moveToNext()) {
      ContentValues            rawContactValues = ContactFactory.getValuesForRawContact(cursor);
      Long                     rawContactId     = rawContactValues.getAsLong(getColumnNameComponentLocalId());
      ComponentETagPair<VCard> vCard            = ContactFactory.getVCard(rawContactValues);

      buildContact(rawContactId, vCard.getComponent());
      vCards.add(vCard);
    }

    cursor.close();
    return vCards;
  }

  @Override
  public void addComponent(ComponentETagPair<VCard> vCard)
      throws InvalidComponentException, RemoteException
  {
    ContentValues rawContactValues = ContactFactory.getValuesForRawContact(vCard);

    int raw_contact_op_index = pendingOperations.size();

    pendingOperations.add(ContentProviderOperation.newInsert(getUriForComponents())
        .withValues(rawContactValues)
        .build());

    Optional<ContentValues> structuredName = ContactFactory.getValuesForStructuredName(vCard.getComponent());
    if (structuredName.isPresent()) {
      pendingOperations.add(ContentProviderOperation.newInsert(getUriForData())
          .withValues(structuredName.get())
          .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, raw_contact_op_index)
          .build());
    }

    List<ContentValues> phoneNumbers = ContactFactory.getValuesForPhoneNumbers(vCard.getComponent());
    for (ContentValues phoneNumber : phoneNumbers) {
      pendingOperations.add(ContentProviderOperation.newInsert(getUriForData())
          .withValues(phoneNumber)
          .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, raw_contact_op_index)
          .build());
    }

    List<ContentValues> emailAddresses = ContactFactory.getValuesForEmailAddresses(vCard.getComponent());
    for (ContentValues emailAddress : emailAddresses) {
      pendingOperations.add(ContentProviderOperation.newInsert(getUriForData())
          .withValues(emailAddress)
          .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, raw_contact_op_index)
          .build());
    }

    Optional<ContentValues> picture = ContactFactory.getValuesForPhoto(vCard.getComponent());
    if (picture.isPresent()) {
      pendingOperations.add(ContentProviderOperation.newInsert(getUriForData())
          .withValues(picture.get())
          .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, raw_contact_op_index)
          .build());
    }

    List<ContentValues> organizations = ContactFactory.getValuesForOrganization(vCard.getComponent());
    for (ContentValues organization : organizations) {
      pendingOperations.add(ContentProviderOperation.newInsert(getUriForData())
          .withValues(organization)
          .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, raw_contact_op_index)
          .build());
    }

    List<ContentValues> instantMessaging = ContactFactory.getValuesForInstantMessaging(vCard.getComponent());
    for (ContentValues instantMessenger : instantMessaging) {
      pendingOperations.add(ContentProviderOperation.newInsert(getUriForData())
          .withValues(instantMessenger)
          .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, raw_contact_op_index)
          .build());
    }

    List<ContentValues> nickNames = ContactFactory.getValuesForNickName(vCard.getComponent());
    for (ContentValues nickName : nickNames) {
      pendingOperations.add(ContentProviderOperation.newInsert(getUriForData())
          .withValues(nickName)
          .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, raw_contact_op_index)
          .build());
    }

    List<ContentValues> notes = ContactFactory.getValuesForNote(vCard.getComponent());
    for (ContentValues note : notes) {
      pendingOperations.add(ContentProviderOperation.newInsert(getUriForData())
          .withValues(note)
          .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, raw_contact_op_index)
          .build());
    }

    List<ContentValues> postalAddresses = ContactFactory.getValuesForPostalAddresses(vCard.getComponent());
    for (ContentValues postalAddress : postalAddresses) {
      pendingOperations.add(ContentProviderOperation.newInsert(getUriForData())
          .withValues(postalAddress)
          .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, raw_contact_op_index)
          .build());
    }

    List<ContentValues> websites = ContactFactory.getValuesForWebsites(vCard.getComponent());
    for (ContentValues website : websites) {
      pendingOperations.add(ContentProviderOperation.newInsert(getUriForData())
          .withValues(website)
          .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, raw_contact_op_index)
          .build());
    }

    List<ContentValues> events = ContactFactory.getValuesForEvents(vCard.getComponent());
    for (ContentValues event : events) {
      pendingOperations.add(ContentProviderOperation.newInsert(getUriForData())
          .withValues(event)
          .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, raw_contact_op_index)
          .build());
    }

    List<ContentValues> sipAddresses = ContactFactory.getValuesForSipAddresses(vCard.getComponent());
    for (ContentValues sipAddress : sipAddresses) {
      pendingOperations.add(ContentProviderOperation.newInsert(getUriForData())
          .withValues(sipAddress)
          .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, raw_contact_op_index)
          .build());
    }
  }

  @Override
  public void updateComponent(ComponentETagPair<VCard> vCard)
      throws InvalidComponentException, RemoteException
  {
    if (vCard.getComponent().getUid() != null) {
      removeComponent(vCard.getComponent().getUid().getValue());
      addComponent(vCard);
    }
    else {
      Log.e(TAG, "was given a vcard with missing uid");
      throw new InvalidComponentException("Cannot update a vCard without UID!", false,
                                          CardDavConstants.CARDDAV_NAMESPACE, getPath());
    }
  }

  public void copyToAccount(Account toAccount, ContactCopiedListener listener)
      throws InvalidComponentException, RemoteException
  {
    Log.d(TAG, "copy my " + getComponentIds().size() + " contacts to " + toAccount.name);

    LocalContactCollection toCollection   = new LocalContactCollection(context, client, toAccount, getPath());

    for (Long contactId : getComponentIds()) {
      try {

        Optional<VCard> copyContact = getComponent(contactId);
        if (copyContact.isPresent()) {
          if (!ContactFactory.hasInvisibleProperty(copyContact.get())) {
            copyContact.get().setUid(null);
            ComponentETagPair<VCard> correctedContact =
                new ComponentETagPair<VCard>(copyContact.get(), Optional.<String>absent());

            toCollection.addComponent(correctedContact);
            toCollection.commitPendingOperations();
          }
          else
            Log.w(TAG, "contact is entirely invisible to the user, skipping.");

          listener.onContactCopied(getAccount(), toAccount);
        }
        else
          throw new InvalidComponentException("absent component for local id on copy",
                                              false, CardDavConstants.CARDDAV_NAMESPACE, getPath());

      } catch (InvalidComponentException e) {
        listener.onContactCopyFailed(e, getAccount(), toAccount);
      } catch (RemoteException e) {
        listener.onContactCopyFailed(e, getAccount(), toAccount);
      } catch (OperationApplicationException e) {
        listener.onContactCopyFailed(e, getAccount(), toAccount);
      }
    }
  }
}
