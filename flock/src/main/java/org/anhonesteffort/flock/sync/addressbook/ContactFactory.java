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
import android.content.ContentValues;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.util.Log;

import ezvcard.VCard;
import ezvcard.VCardVersion;
import ezvcard.parameter.AddressType;
import ezvcard.parameter.EmailType;
import ezvcard.parameter.ImageType;
import ezvcard.parameter.ImppType;
import ezvcard.parameter.TelephoneType;
import ezvcard.property.Address;
import ezvcard.property.Birthday;
import ezvcard.property.Email;
import ezvcard.property.FormattedName;
import ezvcard.property.Impp;
import ezvcard.property.Nickname;
import ezvcard.property.Note;
import ezvcard.property.Organization;
import ezvcard.property.Photo;
import ezvcard.property.RawProperty;
import ezvcard.property.Role;
import ezvcard.property.StructuredName;
import ezvcard.property.Telephone;
import ezvcard.property.Uid;
import ezvcard.property.Url;

import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.sync.InvalidLocalComponentException;
import org.anhonesteffort.flock.util.Base64;
import org.anhonesteffort.flock.webdav.carddav.CardDavConstants;
import org.anhonesteffort.flock.webdav.ComponentETagPair;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * Programmer: rhodey
 *
 * Much thanks to the DAVDroid project and especially
 * Richard Hirner (bitfire web engineering) for leading
 * the way in shoving VCard objects into Androids Contacts
 * Content Provider. This would have been much more of a
 * pain without a couple hints from the DAVDroid codebase.
 */
public class ContactFactory {

  private static final String TAG = "org.anhonesteffort.flock.sync.addressbook.ContactFactory";

  public static final String COLUMN_NAME_CONTACT_UID  = ContactsContract.RawContacts.SOURCE_ID;
  public static final String COLUMN_NAME_CONTACT_ETAG = ContactsContract.RawContacts.SYNC1;

  public static final String PROPERTY_PHONETIC_GIVEN_NAME  = "X-PHONETIC-GIVEN-NAME";
  public static final String PROPERTY_PHONETIC_MIDDLE_NAME = "X-PHONETIC-MIDDLE-NAME";
  public static final String PROPERTY_PHONETIC_FAMILY_NAME = "X-PHONETIC-FAMILY-NAME";

  public static final EmailType EMAIL_TYPE_MOBILE = EmailType.get("X-MOBILE");

  public static final String PROPERTY_SIP                 = "X-SIP";
  public static final String PROPERTY_STARRED             = "X-STARRED";
  public static final String PROPERTY_EVENT_ANNIVERSARY   = "X-EVENT-ANNIVERSARY";
  public static final String PROPERTY_EVENT_OTHER         = "X-EVENT-OTHER";
  public static final String PROPERTY_EVENT_CUSTOM        = "X-EVENT-CUSTOM";
  public static final String PARAMETER_EVENT_CUSTOM_LABEL = "X-EVENT-CUSTOM-LABEL";

  public static final String PROPERTY_INVISIBLE_CONTACT      = "X-INVISIBLE-CONTACT";
  public static final String PROPERTY_AGGREGATION_EXCEPTIONS = "X-AGGREGATION-EXCEPTIONS";

  private static String getUid(VCard vCard) {
    if (vCard.getUid() != null)
      return vCard.getUid().getValue();

    return null;
  }

  /*
  TODO:
    neither of the below two methods were necessary originally but are now due to legacy :[
   */
  public static String propertyNameToLabel(String propertyName) {
    return WordUtils.capitalize(propertyName.toLowerCase().replace("x-", "").replace("_", " "));
  }

  public static String labelToPropertyName(String label) {
    return "X-" + label.replace(" ","_").toUpperCase();
  }

  public static String[] getProjectionForRawContact() {
    return new String[] {
        ContactsContract.RawContacts._ID,
        COLUMN_NAME_CONTACT_UID,
        COLUMN_NAME_CONTACT_ETAG,
        ContactsContract.RawContacts.STARRED
    };
  }

  public static ContentValues getValuesForRawContact(Cursor cursor) {
    ContentValues values = new ContentValues(4);

    values.put(ContactsContract.RawContacts._ID,     cursor.getLong(0));
    values.put(COLUMN_NAME_CONTACT_UID,              cursor.getString(1));
    values.put(COLUMN_NAME_CONTACT_ETAG,             cursor.getString(2));
    values.put(ContactsContract.RawContacts.STARRED, (cursor.getInt(3) != 0));

    return values;
  }

  public static ContentValues getValuesForRawContact(ComponentETagPair<VCard> vCard) {
    ContentValues values = new ContentValues();
    Uid           uid    = vCard.getComponent().getUid();

    if (uid != null)
      values.put(COLUMN_NAME_CONTACT_UID, uid.getValue());

    if (vCard.getETag().isPresent())
      values.put(COLUMN_NAME_CONTACT_ETAG, vCard.getETag().get());

    RawProperty starredProp = vCard.getComponent().getExtendedProperty(PROPERTY_STARRED);
    if (starredProp != null) {
      boolean is_starred = Integer.parseInt(starredProp.getValue()) != 0;
      values.put(ContactsContract.RawContacts.STARRED, is_starred);
    }

    return values;
  }

  public static ComponentETagPair<VCard> getVCard(ContentValues rawContactValues) {
    String  uidText  = rawContactValues.getAsString(COLUMN_NAME_CONTACT_UID);
    String  eTagText = rawContactValues.getAsString(COLUMN_NAME_CONTACT_ETAG);
    Boolean starred  = rawContactValues.getAsBoolean(ContactsContract.RawContacts.STARRED);

    VCard vCard = new VCard();

    vCard.setVersion(VCardVersion.V3_0);
    vCard.setUid(new Uid(uidText));

    if (starred != null)
      vCard.setExtendedProperty(PROPERTY_STARRED, starred ? "1" : "0");

    return new ComponentETagPair<VCard>(vCard, Optional.fromNullable(eTagText));
  }

  public static String[] getProjectionForStructuredName() {
    return new String[] {
        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,         // 00
        ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,           // 01
        ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,          // 02
        ContactsContract.CommonDataKinds.StructuredName.PREFIX,               // 03
        ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,          // 04
        ContactsContract.CommonDataKinds.StructuredName.SUFFIX,               // 05
        ContactsContract.CommonDataKinds.StructuredName.PHONETIC_GIVEN_NAME,  // 06
        ContactsContract.CommonDataKinds.StructuredName.PHONETIC_MIDDLE_NAME, // 07
        ContactsContract.CommonDataKinds.StructuredName.PHONETIC_FAMILY_NAME  // 08
    };
  }

  public static ContentValues getValuesForStructuredName(Cursor cursor) {
    ContentValues values = new ContentValues(9);

    values.put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,         cursor.getString(0));
    values.put(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,           cursor.getString(1));
    values.put(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,          cursor.getString(2));
    values.put(ContactsContract.CommonDataKinds.StructuredName.PREFIX,               cursor.getString(3));
    values.put(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,          cursor.getString(4));
    values.put(ContactsContract.CommonDataKinds.StructuredName.SUFFIX,               cursor.getString(5));
    values.put(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_GIVEN_NAME,  cursor.getString(6));
    values.put(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_MIDDLE_NAME, cursor.getString(7));
    values.put(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_FAMILY_NAME, cursor.getString(8));

    return values;
  }

  public static Optional<ContentValues> getValuesForStructuredName(VCard vCard) {
    FormattedName  formattedName  = vCard.getFormattedName();
    StructuredName structuredName = vCard.getStructuredName();

    if (formattedName == null && structuredName == null)
      return Optional.absent();

    ContentValues values = new ContentValues();

    values.put(ContactsContract.Data.MIMETYPE,
               ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);

    if (formattedName != null) {
      values.put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                 formattedName.getValue());
    }

    if (structuredName != null) {
      values.put(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                 structuredName.getGiven());
      values.put(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
                 structuredName.getFamily());

      if (structuredName.getPrefixes().size() > 0) {
        values.put(ContactsContract.CommonDataKinds.StructuredName.PREFIX,
                   StringUtils.join(structuredName.getPrefixes(), " "));
      }

      if (structuredName.getAdditional().size() > 0)
        values.put(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,
                   StringUtils.join(structuredName.getAdditional(), " "));

      if (structuredName.getSuffixes().size() > 0)
        values.put(ContactsContract.CommonDataKinds.StructuredName.SUFFIX,
                   StringUtils.join(structuredName.getSuffixes(), " "));
    }

    RawProperty phoneticGivenName = vCard.getExtendedProperty(PROPERTY_PHONETIC_GIVEN_NAME);
    if (phoneticGivenName != null)
      values.put(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_GIVEN_NAME,
                 phoneticGivenName.getValue());

    RawProperty phoneticMiddleName = vCard.getExtendedProperty(PROPERTY_PHONETIC_MIDDLE_NAME);
    if (phoneticMiddleName != null)
      values.put(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_MIDDLE_NAME,
                 phoneticMiddleName.getValue());

    RawProperty phoneticFamilyName = vCard.getExtendedProperty(PROPERTY_PHONETIC_FAMILY_NAME);
    if (phoneticFamilyName != null)
      values.put(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_FAMILY_NAME,
                 phoneticFamilyName.getValue());

    return Optional.of(values);
  }

  public static void addStructuredName(VCard vCard, ContentValues structuredNameValues) {
    String displayName        = structuredNameValues.getAsString(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);
    String givenName          = structuredNameValues.getAsString(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
    String familyName         = structuredNameValues.getAsString(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME);
    String prefixes           = structuredNameValues.getAsString(ContactsContract.CommonDataKinds.StructuredName.PREFIX);
    String middleName         = structuredNameValues.getAsString(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME);
    String suffixes           = structuredNameValues.getAsString(ContactsContract.CommonDataKinds.StructuredName.SUFFIX);
    String phoneticGivenName  = structuredNameValues.getAsString(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_GIVEN_NAME);
    String phoneticMiddleName = structuredNameValues.getAsString(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_MIDDLE_NAME);
    String phoneticFamilyName = structuredNameValues.getAsString(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_FAMILY_NAME);

    if (displayName == null) {
      Log.w(TAG, "display name missing, nothing to add");
      return;
    }

    FormattedName  formattedName  = new FormattedName(displayName);
    StructuredName structuredName = new StructuredName();

    vCard.addFormattedName(formattedName);

    if (givenName != null)
      structuredName.setGiven(givenName);

    if (familyName != null)
      structuredName.setFamily(familyName);

    if (prefixes != null) {
      for (String prefix : StringUtils.split(prefixes))
        structuredName.addPrefix(prefix);
    }

    if (middleName != null)
      structuredName.addAdditional(middleName);

    if (suffixes != null) {
      for (String suffix : StringUtils.split(suffixes))
        structuredName.addSuffix(suffix);
    }

    vCard.setStructuredName(structuredName);

    if (phoneticGivenName != null)
      vCard.setExtendedProperty(PROPERTY_PHONETIC_GIVEN_NAME, phoneticGivenName);

    if (phoneticMiddleName != null)
      vCard.setExtendedProperty(PROPERTY_PHONETIC_MIDDLE_NAME, phoneticMiddleName);

    if (phoneticFamilyName != null)
      vCard.setExtendedProperty(PROPERTY_PHONETIC_FAMILY_NAME, phoneticFamilyName);
  }

  public static String[] getProjectionForPhoneNumber() {
    return new String[] {
        ContactsContract.CommonDataKinds.Phone.TYPE,            // 00
        ContactsContract.CommonDataKinds.Phone.LABEL,           // 01
        ContactsContract.CommonDataKinds.Phone.NUMBER,          // 02
        ContactsContract.CommonDataKinds.Phone.IS_PRIMARY,      // 03
        ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY // 04
    };
  }

  public static ContentValues getValuesForPhoneNumber(Cursor cursor) {
    ContentValues values = new ContentValues(5);

    values.put(ContactsContract.CommonDataKinds.Phone.TYPE,             cursor.getInt(0));
    values.put(ContactsContract.CommonDataKinds.Phone.LABEL,            cursor.getString(1));
    values.put(ContactsContract.CommonDataKinds.Phone.NUMBER,           cursor.getString(2));
    values.put(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY,       (cursor.getInt(3) != 0));
    values.put(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY, (cursor.getInt(4) != 0));

    return values;
  }

  public static List<ContentValues> getValuesForPhoneNumbers(VCard vCard) {
    List<ContentValues> valuesList = new LinkedList<ContentValues>();
    List<Telephone>     telephones = vCard.getTelephoneNumbers();

    for (Telephone telephone : telephones) {
      ContentValues values     = new ContentValues();
      int           phone_type = ContactsContract.CommonDataKinds.Phone.TYPE_OTHER;

      values.put(ContactsContract.Data.MIMETYPE,
                 ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);

      if (telephone.getTypes().contains(TelephoneType.FAX)) {
        if (telephone.getTypes().contains(TelephoneType.HOME))
          phone_type = ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME;
        else if (telephone.getTypes().contains(TelephoneType.WORK))
          phone_type = ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK;
        else
          phone_type = ContactsContract.CommonDataKinds.Phone.TYPE_OTHER_FAX;
      }
      else if (telephone.getTypes().contains(TelephoneType.CELL))
        phone_type = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE;
      else if (telephone.getTypes().contains(TelephoneType.PAGER))
        phone_type = ContactsContract.CommonDataKinds.Phone.TYPE_PAGER;
      else if (telephone.getTypes().contains(TelephoneType.WORK))
        phone_type = ContactsContract.CommonDataKinds.Phone.TYPE_WORK;
      else if (telephone.getTypes().contains(TelephoneType.HOME))
        phone_type = ContactsContract.CommonDataKinds.Phone.TYPE_HOME;
      else if (telephone.getTypes().contains(TelephoneType.PREF))
        phone_type = ContactsContract.CommonDataKinds.Phone.TYPE_MAIN;
      else if (!telephone.getTypes().isEmpty()) {
        phone_type = ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM;
        values.put(ContactsContract.CommonDataKinds.Phone.LABEL,
                   propertyNameToLabel(telephone.getTypes().iterator().next().getValue()));
      }

      values.put(ContactsContract.CommonDataKinds.Phone.TYPE,   phone_type);
      values.put(ContactsContract.CommonDataKinds.Phone.NUMBER, telephone.getText());

      values.put(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY,
                 telephone.getTypes().contains(TelephoneType.PREF) ? 1 : 0);
      values.put(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
                 telephone.getTypes().contains(TelephoneType.PREF) ? 1 : 0);

      valuesList.add(values);
    }

    return valuesList;
  }

  public static void addPhoneNumber(VCard vCard, ContentValues phoneNumberValues) {
    Integer type           = phoneNumberValues.getAsInteger(ContactsContract.CommonDataKinds.Phone.TYPE);
    String  label          = phoneNumberValues.getAsString(ContactsContract.CommonDataKinds.Phone.LABEL);
    String  number         = phoneNumberValues.getAsString(ContactsContract.CommonDataKinds.Phone.NUMBER);
    Boolean isPrimary      = phoneNumberValues.getAsBoolean(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY);
    Boolean isSuperPrimary = phoneNumberValues.getAsBoolean(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY);

    if (type == null || number == null) {
      Log.w(TAG, "phone type or number is null, not adding anything");
      return;
    }

    Telephone telephone = new Telephone(number);

    switch (type) {
      case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE:
        telephone.addType(TelephoneType.CELL);
        break;

      case ContactsContract.CommonDataKinds.Phone.TYPE_WORK:
        telephone.addType(TelephoneType.WORK);
        break;

      case ContactsContract.CommonDataKinds.Phone.TYPE_HOME:
        telephone.addType(TelephoneType.HOME);
        break;

      case ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK:
        telephone.addType(TelephoneType.FAX);
        telephone.addType(TelephoneType.WORK);
        break;

      case ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME:
        telephone.addType(TelephoneType.FAX);
        telephone.addType(TelephoneType.HOME);
        break;

      case ContactsContract.CommonDataKinds.Phone.TYPE_PAGER:
        telephone.addType(TelephoneType.PAGER);
        break;

      case ContactsContract.CommonDataKinds.Phone.TYPE_MAIN:
        telephone.addType(TelephoneType.PREF);
        break;

      default:
        if (label != null)
          telephone.addType(TelephoneType.get(labelToPropertyName(label)));
    }

    if (isPrimary != null && isPrimary)
      telephone.addType(TelephoneType.PREF);
    else if (isSuperPrimary != null && isSuperPrimary)
      telephone.addType(TelephoneType.PREF);

    vCard.addTelephoneNumber(telephone);
  }

  public static String[] getProjectionForEmailAddress() {
    return new String[] {
        ContactsContract.CommonDataKinds.Email.TYPE,            // 00
        ContactsContract.CommonDataKinds.Email.ADDRESS,         // 01
        ContactsContract.CommonDataKinds.Email.LABEL,           // 02
        ContactsContract.CommonDataKinds.Email.IS_PRIMARY,      // 03
        ContactsContract.CommonDataKinds.Email.IS_SUPER_PRIMARY // 04
    };
  }

  public static ContentValues getValuesForEmailAddress(Cursor cursor) {
    ContentValues values = new ContentValues(5);

    values.put(ContactsContract.CommonDataKinds.Email.TYPE,             cursor.getInt(0));
    values.put(ContactsContract.CommonDataKinds.Email.ADDRESS,          cursor.getString(1));
    values.put(ContactsContract.CommonDataKinds.Email.LABEL,            cursor.getString(2));
    values.put(ContactsContract.CommonDataKinds.Email.IS_PRIMARY,       (cursor.getInt(3) != 0));
    values.put(ContactsContract.CommonDataKinds.Email.IS_SUPER_PRIMARY, (cursor.getInt(4) != 0));

    return values;
  }

  public static List<ContentValues> getValuesForEmailAddresses(VCard vCard) {
    List<ContentValues> valuesList = new LinkedList<ContentValues>();
    List<Email>         emails     = vCard.getEmails();

    for (Email email : emails) {
      ContentValues values = new ContentValues();

      values.put(ContactsContract.Data.MIMETYPE,
                 ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE);

      if (email.getTypes().contains(EmailType.HOME))
        values.put(ContactsContract.CommonDataKinds.Email.TYPE,
                   ContactsContract.CommonDataKinds.Email.TYPE_HOME);
      else if (email.getTypes().contains(EmailType.WORK))
        values.put(ContactsContract.CommonDataKinds.Email.TYPE,
                   ContactsContract.CommonDataKinds.Email.TYPE_WORK);
      else if (email.getTypes().contains(EMAIL_TYPE_MOBILE))
        values.put(ContactsContract.CommonDataKinds.Email.TYPE,
                   ContactsContract.CommonDataKinds.Email.TYPE_MOBILE);
      else if (!email.getTypes().isEmpty()) {
        values.put(ContactsContract.CommonDataKinds.Email.TYPE,
                   ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM);
        values.put(ContactsContract.CommonDataKinds.Email.LABEL,
                   propertyNameToLabel(email.getTypes().iterator().next().getValue()));
      }
      else
        values.put(ContactsContract.CommonDataKinds.Email.TYPE,
                   ContactsContract.CommonDataKinds.Email.TYPE_OTHER);

      values.put(ContactsContract.CommonDataKinds.Email.ADDRESS, email.getValue());

      values.put(ContactsContract.CommonDataKinds.Email.IS_PRIMARY,
                 email.getTypes().contains(EmailType.PREF) ? 1 : 0);
      values.put(ContactsContract.CommonDataKinds.Email.IS_SUPER_PRIMARY,
                 email.getTypes().contains(EmailType.PREF) ? 1 : 0);

      valuesList.add(values);
    }

    return valuesList;
  }

  public static void addEmailAddress(VCard vCard, ContentValues emailValues) {
    Integer type           = emailValues.getAsInteger(ContactsContract.CommonDataKinds.Email.TYPE);
    String  label          = emailValues.getAsString(ContactsContract.CommonDataKinds.Email.LABEL);
    String  address        = emailValues.getAsString(ContactsContract.CommonDataKinds.Email.ADDRESS);
    Boolean isPrimary      = emailValues.getAsBoolean(ContactsContract.CommonDataKinds.Email.IS_PRIMARY);
    Boolean isSuperPrimary = emailValues.getAsBoolean(ContactsContract.CommonDataKinds.Email.IS_SUPER_PRIMARY);

    if (type == null || address == null) {
      Log.w(TAG, "email type or address is null, not adding anything");
      return;
    }

    Email email = new Email(address);

    switch (type) {
      case ContactsContract.CommonDataKinds.Email.TYPE_HOME:
        email.addType(EmailType.HOME);
        break;

      case ContactsContract.CommonDataKinds.Email.TYPE_WORK:
        email.addType(EmailType.WORK);
        break;

      case ContactsContract.CommonDataKinds.Email.TYPE_MOBILE:
        email.addType(EMAIL_TYPE_MOBILE);
        break;

      default:
        if (label != null)
          email.addType(EmailType.get(label));
        break;
    }

    if (isPrimary != null && isPrimary)
      email.addType(EmailType.PREF);
    else if (isSuperPrimary != null && isSuperPrimary)
      email.addType(EmailType.PREF);

    vCard.addEmail(email);
  }

  public static String[] getProjectionForPhoto() {
    return new String[] {
        ContactsContract.CommonDataKinds.Photo.PHOTO // 00 raw bytes of image
    };
  }

  public static ContentValues getValuesForPhoto(Cursor cursor) {
    ContentValues values = new ContentValues(1);

    values.put(ContactsContract.CommonDataKinds.Photo.PHOTO, cursor.getBlob(0));

    return values;
  }

  public static Optional<Photo> getPhotoForValues(ContentValues values) {
    byte[] thumbnailBytes = values.getAsByteArray(ContactsContract.CommonDataKinds.Photo.PHOTO);

    if (thumbnailBytes == null || thumbnailBytes.length <= 0)
      return Optional.absent();

    return Optional.of(
        new Photo(thumbnailBytes, ImageType.PNG)
    );
  }

  public static Optional<ContentValues> getValuesForPhoto(VCard vCard) {
    if (vCard.getPhotos().size() > 0) {
      ContentValues values = new ContentValues();

      values.put("skip_processing", true); // 0.o see >> com.android.providers.contacts.DataRowHandlerForPhoto
      values.put(ContactsContract.Data.MIMETYPE,
                 ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);
      values.put(ContactsContract.CommonDataKinds.Photo.PHOTO,
                 vCard.getPhotos().get(0).getData());

      return Optional.of(values);
    }

    return Optional.absent();
  }

  public static Integer getSizeOfPhotoInBytes(ContentValues photoValues) {
    return photoValues.getAsByteArray(ContactsContract.CommonDataKinds.Photo.PHOTO).length;
  }

  public static String[] getProjectionForOrganization() {
    return new String[] {
        ContactsContract.CommonDataKinds.Organization.COMPANY, // 00
        ContactsContract.CommonDataKinds.Organization.TITLE    // 01
    };
  }

  public static ContentValues getValuesForOrganization(Cursor cursor) {
    ContentValues values = new ContentValues(2);

    values.put(ContactsContract.CommonDataKinds.Organization.COMPANY, cursor.getString(0));
    values.put(ContactsContract.CommonDataKinds.Organization.TITLE,   cursor.getString(1));

    return values;
  }

  public static List<ContentValues> getValuesForOrganizations(VCard vCard) {
    List<ContentValues> valuesList = new LinkedList<ContentValues>();

    for (int i = 0; i < vCard.getOrganizations().size(); i++) {
      Organization  organization = vCard.getOrganizations().get(i);
      ContentValues values       = new ContentValues();

      values.put(ContactsContract.Data.MIMETYPE,
                 ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE);

      values.put(ContactsContract.CommonDataKinds.Organization.COMPANY,
                 organization.getValues().get(0));

      if (vCard.getRoles().size() > i)
        values.put(ContactsContract.CommonDataKinds.Organization.TITLE,
                   vCard.getRoles().get(i).getValue());

      valuesList.add(values);
    }

    return valuesList;
  }

  public static void addOrganization(VCard vCard, ContentValues organizationValues) {
    String companyText = organizationValues.getAsString(ContactsContract.CommonDataKinds.Organization.COMPANY);
    String roleText    = organizationValues.getAsString(ContactsContract.CommonDataKinds.Organization.TITLE);

    if (companyText != null) {
      Organization organization = new Organization();
      organization.addValue(companyText);
      vCard.addOrganization(organization);
    }

    if (roleText != null) {
      Role role = new Role(roleText);
      vCard.addRole(role);
    }
  }

  public static String[] getProjectionForInstantMessaging() {
    return new String[] {
        ContactsContract.CommonDataKinds.Im.TYPE,           // 00
        ContactsContract.CommonDataKinds.Im.DATA,           // 01
        ContactsContract.CommonDataKinds.Im.LABEL,          // 02
        ContactsContract.CommonDataKinds.Im.PROTOCOL,       // 03
        ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL // 04
    };
  }

  public static ContentValues getValuesForInstantMessaging(Cursor cursor) {
    ContentValues values = new ContentValues(5);

    values.put(ContactsContract.CommonDataKinds.Im.TYPE,            cursor.getInt(0));
    values.put(ContactsContract.CommonDataKinds.Im.DATA,            cursor.getString(1));
    values.put(ContactsContract.CommonDataKinds.Im.LABEL,           cursor.getString(2));
    values.put(ContactsContract.CommonDataKinds.Im.PROTOCOL,        cursor.getInt(3));
    values.put(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL, cursor.getString(4));

    return values;
  }

  public static List<ContentValues> getValuesForInstantMessaging(VCard vCard) {
    List<ContentValues> valuesList = new LinkedList<ContentValues>();

    for (Impp messenger : vCard.getImpps()) {
      if (messenger.getProtocol() == null || messenger.getProtocol().equalsIgnoreCase("sip"))
        break;

      ContentValues values = new ContentValues();

      values.put(ContactsContract.Data.MIMETYPE,
                 ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE);
      values.put(ContactsContract.CommonDataKinds.Im.TYPE,
                 ContactsContract.CommonDataKinds.Im.TYPE_OTHER);

      values.put(ContactsContract.CommonDataKinds.Im.DATA, messenger.getHandle());

      if (messenger.isAim())
        values.put(ContactsContract.CommonDataKinds.Im.PROTOCOL,
                   ContactsContract.CommonDataKinds.Im.PROTOCOL_AIM);
      else if (messenger.isMsn())
        values.put(ContactsContract.CommonDataKinds.Im.PROTOCOL,
                   ContactsContract.CommonDataKinds.Im.PROTOCOL_MSN);
      else if (messenger.isYahoo())
        values.put(ContactsContract.CommonDataKinds.Im.PROTOCOL,
                   ContactsContract.CommonDataKinds.Im.PROTOCOL_YAHOO);
      else if (messenger.isSkype())
        values.put(ContactsContract.CommonDataKinds.Im.PROTOCOL,
                   ContactsContract.CommonDataKinds.Im.PROTOCOL_SKYPE);
      else if (messenger.isIcq())
        values.put(ContactsContract.CommonDataKinds.Im.PROTOCOL,
                   ContactsContract.CommonDataKinds.Im.PROTOCOL_ICQ);
      else if (messenger.isXmpp())
        values.put(ContactsContract.CommonDataKinds.Im.PROTOCOL,
                   ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER);
      else if (messenger.getProtocol().equalsIgnoreCase("jabber"))
        values.put(ContactsContract.CommonDataKinds.Im.PROTOCOL,
                   ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER);
      else if (messenger.getProtocol().equalsIgnoreCase("qq"))
        values.put(ContactsContract.CommonDataKinds.Im.PROTOCOL,
                   ContactsContract.CommonDataKinds.Im.PROTOCOL_QQ);
      else if (messenger.getProtocol().equalsIgnoreCase("google-talk"))
        values.put(ContactsContract.CommonDataKinds.Im.PROTOCOL,
                   ContactsContract.CommonDataKinds.Im.PROTOCOL_GOOGLE_TALK);
      else if (messenger.getProtocol().equalsIgnoreCase("netmeeting"))
        values.put(ContactsContract.CommonDataKinds.Im.PROTOCOL,
                   ContactsContract.CommonDataKinds.Im.PROTOCOL_NETMEETING);
      else {
        values.put(ContactsContract.CommonDataKinds.Im.PROTOCOL,
                   ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM);
        values.put(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL,
                   messenger.getProtocol());
      }

      valuesList.add(values);
    }

    return valuesList;
  }

  public static void addInstantMessaging(VCard vCard, ContentValues imValues) {
    Integer protocol       = imValues.getAsInteger(ContactsContract.CommonDataKinds.Im.PROTOCOL);
    String  customProtocol = imValues.getAsString(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL);
    String  handle         = imValues.getAsString(ContactsContract.CommonDataKinds.Im.DATA);

    if (protocol == null || handle == null) {
      Log.w(TAG, "im protocol, or handle is null, not adding anything");
      return;
    }

    Impp impp;

    switch (protocol) {
      case ContactsContract.CommonDataKinds.Im.PROTOCOL_AIM:
        impp = Impp.aim(handle);
        break;

      case ContactsContract.CommonDataKinds.Im.PROTOCOL_MSN:
        impp = Impp.msn(handle);
        break;

      case ContactsContract.CommonDataKinds.Im.PROTOCOL_YAHOO:
        impp = Impp.yahoo(handle);
        break;

      case ContactsContract.CommonDataKinds.Im.PROTOCOL_SKYPE:
        impp = Impp.skype(handle);
        break;

      case ContactsContract.CommonDataKinds.Im.PROTOCOL_ICQ:
        impp = Impp.icq(handle);
        break;

      case ContactsContract.CommonDataKinds.Im.PROTOCOL_QQ:
        impp = new Impp("qq", handle);
        break;

      case ContactsContract.CommonDataKinds.Im.PROTOCOL_GOOGLE_TALK:
        impp = new Impp("google-talk", handle);
        break;

      case ContactsContract.CommonDataKinds.Im.PROTOCOL_NETMEETING:
        impp = new Impp("netmeeting", handle);
        break;

      case ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM:
        impp = new Impp(customProtocol, handle);
        break;

      default:
        impp = Impp.xmpp(handle);
        break;
    }

    impp.addType(ImppType.PERSONAL);
    vCard.addImpp(impp);
  }

  public static String[] getProjectionForNickName() {
    return new String[] {
        ContactsContract.CommonDataKinds.Nickname.NAME // 00
    };
  }

  public static ContentValues getValuesForNickName(Cursor cursor) {
    ContentValues values = new ContentValues(1);

    values.put(ContactsContract.CommonDataKinds.Nickname.NAME, cursor.getString(0));

    return values;
  }

  public static List<ContentValues> getValuesForNickNames(VCard vCard) {
    List<ContentValues> valuesList = new LinkedList<ContentValues>();

    for (Nickname nickname : vCard.getNicknames()) {
      ContentValues values = new ContentValues();

      values.put(ContactsContract.Data.MIMETYPE,
                 ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE);
      values.put(ContactsContract.CommonDataKinds.Nickname.NAME,
                 nickname.getValues().get(0));

      valuesList.add(values);
    }

    return valuesList;
  }

  public static void addNickName(VCard vCard, ContentValues nickNameValues) {
    String nickNameText = nickNameValues.getAsString(ContactsContract.CommonDataKinds.Nickname.NAME);

    if (nickNameText != null) {
      Nickname nickname = new Nickname();
      nickname.addValue(nickNameText);

      vCard.addNickname(nickname);
    }
  }

  public static String[] getProjectionForNote() {
    return new String[] {
        ContactsContract.CommonDataKinds.Note.NOTE // 00
    };
  }

  public static ContentValues getValuesForNote(Cursor cursor) {
    ContentValues values = new ContentValues(1);

    values.put(ContactsContract.CommonDataKinds.Note.NOTE, cursor.getString(0));

    return values;
  }

  public static List<ContentValues> getValuesForNotes(VCard vCard) {
    List<ContentValues> valuesList = new LinkedList<ContentValues>();

    for (Note note : vCard.getNotes()) {
      ContentValues values       = new ContentValues();
      String        noteContents = note.getValue();

      values.put(ContactsContract.Data.MIMETYPE,
                 ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE);

      try {

        noteContents = new String(Base64.decode(noteContents));

      } catch (IOException e) {
        Log.w(TAG, "error base64 decoding note, skipping decode", e);
      }

      values.put(ContactsContract.CommonDataKinds.Note.NOTE, noteContents);
      valuesList.add(values);
    }

    return valuesList;
  }

  public static void addNote(VCard vCard, ContentValues noteValues) {
    String noteText = noteValues.getAsString(ContactsContract.CommonDataKinds.Note.NOTE);

    if (noteText != null) {
      Note note = new Note(Base64.encodeBytes(noteText.getBytes()));
      vCard.addNote(note);
    }
  }

  public static Integer getSizeOfNoteInBytes(ContentValues noteValues) {
    return noteValues.getAsString(ContactsContract.CommonDataKinds.Note.NOTE).getBytes().length;
  }

  public static String[] getProjectionForPostalAddress() {
    return new String[] {
        ContactsContract.CommonDataKinds.StructuredPostal.TYPE,              // 00
        ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, // 01
        ContactsContract.CommonDataKinds.StructuredPostal.LABEL,             // 02
        ContactsContract.CommonDataKinds.StructuredPostal.STREET,            // 03
        ContactsContract.CommonDataKinds.StructuredPostal.POBOX,             // 04
        ContactsContract.CommonDataKinds.StructuredPostal.NEIGHBORHOOD,      // 05
        ContactsContract.CommonDataKinds.StructuredPostal.CITY,              // 06
        ContactsContract.CommonDataKinds.StructuredPostal.REGION,            // 07
        ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE,          // 08
        ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY            // 09
    };
  }

  public static ContentValues getValuesForPostalAddress(Cursor cursor) {
    ContentValues values = new ContentValues(10);

    values.put(ContactsContract.CommonDataKinds.StructuredPostal.TYPE,              cursor.getInt(0));
    values.put(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, cursor.getString(1));
    values.put(ContactsContract.CommonDataKinds.StructuredPostal.LABEL,             cursor.getString(2));
    values.put(ContactsContract.CommonDataKinds.StructuredPostal.STREET,            cursor.getString(3));
    values.put(ContactsContract.CommonDataKinds.StructuredPostal.POBOX,             cursor.getString(4));
    values.put(ContactsContract.CommonDataKinds.StructuredPostal.NEIGHBORHOOD,      cursor.getString(5));
    values.put(ContactsContract.CommonDataKinds.StructuredPostal.CITY,              cursor.getString(6));
    values.put(ContactsContract.CommonDataKinds.StructuredPostal.REGION,            cursor.getString(7));
    values.put(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE,          cursor.getString(8));
    values.put(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY,           cursor.getString(9));

    return values;
  }

  public static List<ContentValues> getValuesForPostalAddresses(VCard vCard) {
    List<ContentValues> valuesList = new LinkedList<ContentValues>();

    for (ezvcard.property.Address address : vCard.getAddresses()) {
      ContentValues values = new ContentValues();

      values.put(ContactsContract.Data.MIMETYPE,
                 ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE);

      if (address.getTypes().contains(AddressType.HOME))
        values.put(ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
                   ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME);
      else if (address.getTypes().contains(AddressType.WORK))
        values.put(ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
                   ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK);
      else if (!address.getTypes().isEmpty()) {
        values.put(ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
                   ContactsContract.CommonDataKinds.StructuredPostal.TYPE_CUSTOM);
        values.put(ContactsContract.CommonDataKinds.StructuredPostal.LABEL,
                   address.getTypes().iterator().next().getValue());
      }
      else
        values.put(ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
                   ContactsContract.CommonDataKinds.StructuredPostal.TYPE_OTHER);

      if (address.getLabel() != null) {
        String formattedAddress = address.getLabel();

        try {

          formattedAddress = new String(Base64.decode(formattedAddress));

        } catch (IOException e) {
          Log.w(TAG, "formatted address is not base64 encoded, skipping decode.");
        }

        values.put(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, formattedAddress);
        values.put(ContactsContract.CommonDataKinds.StructuredPostal.STREET,            address.getStreetAddress());
        values.put(ContactsContract.CommonDataKinds.StructuredPostal.POBOX,             address.getPoBox());
        values.put(ContactsContract.CommonDataKinds.StructuredPostal.NEIGHBORHOOD,      address.getExtendedAddress());
        values.put(ContactsContract.CommonDataKinds.StructuredPostal.CITY,              address.getLocality());
        values.put(ContactsContract.CommonDataKinds.StructuredPostal.REGION,            address.getRegion());
        values.put(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE,          address.getPostalCode());
        values.put(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY,           address.getCountry());

        valuesList.add(values);
      }
    }

    return valuesList;
  }

  public static void addPostalAddress(VCard vCard, ContentValues addressValues) {
    Integer addressType      = addressValues.getAsInteger(ContactsContract.CommonDataKinds.StructuredPostal.TYPE);
    String  formattedAddress = addressValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS);
    String  label            = addressValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.LABEL);
    String  street           = addressValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.STREET);
    String  poBox            = addressValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.POBOX);
    String  neighborhood     = addressValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.NEIGHBORHOOD);
    String  city             = addressValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.CITY);
    String  region           = addressValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.REGION);
    String  postcode         = addressValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE);
    String  country          = addressValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY);

    if (addressType == null || formattedAddress == null) {
      Log.w(TAG, "address type or formatted address is null, not adding anything");
      return;
    }

    Address address = new Address();
    address.setLabel(Base64.encodeBytes(formattedAddress.getBytes()));

    switch (addressType) {
      case ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME:
        address.addType(AddressType.HOME);
        break;

      case ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK:
        address.addType(AddressType.WORK);
        break;

      case ContactsContract.CommonDataKinds.StructuredPostal.TYPE_CUSTOM:
        if (label != null)
          address.addType(AddressType.get(label));
        break;
    }

    if (street != null)
      address.setStreetAddress(street);

    if (poBox != null)
      address.setPoBox(poBox);

    if (neighborhood != null)
      address.setExtendedAddress(neighborhood);

    if (city != null)
      address.setLocality(city);

    if (region != null)
      address.setRegion(region);

    if (postcode != null)
      address.setPostalCode(postcode);

    if (country != null)
      address.setCountry(country);

    vCard.addAddress(address);
  }

  public static String[] getProjectionForWebsite() {
    return new String[] {
        ContactsContract.CommonDataKinds.Website.URL // 00
    };
  }

  public static ContentValues getValuesForWebsite(Cursor cursor) {
    ContentValues values = new ContentValues(1);

    values.put(ContactsContract.CommonDataKinds.Website.URL, cursor.getString(0));

    return values;
  }

  public static List<ContentValues> getValuesForWebsites(VCard vCard) {
    List<ContentValues> valuesList = new LinkedList<ContentValues>();

    for (Url url : vCard.getUrls()) {
      ContentValues values = new ContentValues();

      values.put(ContactsContract.Data.MIMETYPE,
                 ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE);
      values.put(ContactsContract.CommonDataKinds.Website.URL, url.getValue());

      valuesList.add(values);
    }

    return valuesList;
  }

  public static void addWebsite(VCard vCard, ContentValues websiteValues) {
    String urlText = websiteValues.getAsString(ContactsContract.CommonDataKinds.Website.URL);

    if (urlText != null) {
      Url url = new Url(urlText);
      vCard.addUrl(url);
    }
  }

  public static String[] getProjectionForEvent() {
    return new String[] {
        ContactsContract.CommonDataKinds.Event.TYPE,      // 00
        ContactsContract.CommonDataKinds.Event.LABEL,     // 01
        ContactsContract.CommonDataKinds.Event.START_DATE // 02
    };
  }

  public static ContentValues getValuesForEvent(Cursor cursor) {
    ContentValues values = new ContentValues(3);

    values.put(ContactsContract.CommonDataKinds.Event.TYPE,       cursor.getInt(0));
    values.put(ContactsContract.CommonDataKinds.Event.LABEL,      cursor.getString(1));
    values.put(ContactsContract.CommonDataKinds.Event.START_DATE, cursor.getString(2));

    return values;
  }

  public static List<ContentValues> getValuesForEvents(VCard vCard) {
    List<ContentValues> valuesList = new LinkedList<ContentValues>();

    if (vCard.getBirthday() != null && vCard.getBirthday().getDate() != null) {
      SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
      String           formatted = formatter.format(vCard.getBirthday().getDate());
      ContentValues    values    = new ContentValues();

       /*
        aCalendar birthday with "year unknown" option...
        https://github.com/WhisperSystems/Flock/issues/26
      */
      if (formatted.startsWith("0001-"))
        formatted = "-" + formatted.substring(4);

      values.put(ContactsContract.Data.MIMETYPE,
                 ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE);
      values.put(ContactsContract.CommonDataKinds.Event.TYPE,
                 ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY);
      values.put(ContactsContract.CommonDataKinds.Event.START_DATE,
                 formatted);

      valuesList.add(values);
    }

    if (vCard.getExtendedProperty(PROPERTY_EVENT_ANNIVERSARY) != null) {
      ContentValues values = new ContentValues();

      values.put(ContactsContract.Data.MIMETYPE,
                 ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE);
      values.put(ContactsContract.CommonDataKinds.Event.TYPE,
                 ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY);
      values.put(ContactsContract.CommonDataKinds.Event.START_DATE,
                 vCard.getExtendedProperty(PROPERTY_EVENT_ANNIVERSARY).getValue());

      valuesList.add(values);
    }

    if (vCard.getExtendedProperty(PROPERTY_EVENT_OTHER) != null) {
      ContentValues values = new ContentValues();

      values.put(ContactsContract.Data.MIMETYPE,
                 ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE);
      values.put(ContactsContract.CommonDataKinds.Event.TYPE,
                 ContactsContract.CommonDataKinds.Event.TYPE_OTHER);
      values.put(ContactsContract.CommonDataKinds.Event.START_DATE,
                 vCard.getExtendedProperty(PROPERTY_EVENT_OTHER).getValue());

      valuesList.add(values);
    }

    if (vCard.getExtendedProperty(PROPERTY_EVENT_CUSTOM) != null) {
      ContentValues values = new ContentValues();

      values.put(ContactsContract.Data.MIMETYPE,
                 ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE);
      values.put(ContactsContract.CommonDataKinds.Event.TYPE,
                 ContactsContract.CommonDataKinds.Event.TYPE_CUSTOM);
      values.put(ContactsContract.CommonDataKinds.Event.LABEL,
                 vCard.getExtendedProperty(PROPERTY_EVENT_CUSTOM).getParameter(PARAMETER_EVENT_CUSTOM_LABEL));
      values.put(ContactsContract.CommonDataKinds.Event.START_DATE,
                 vCard.getExtendedProperty(PROPERTY_EVENT_CUSTOM).getValue());

      valuesList.add(values);
    }

    return valuesList;
  }

  public static void addEvent(String path, VCard vCard, ContentValues eventValues)
      throws InvalidLocalComponentException
  {
    Integer          eventType      = eventValues.getAsInteger(ContactsContract.CommonDataKinds.Event.TYPE);
    String           eventLabel     = eventValues.getAsString(ContactsContract.CommonDataKinds.Event.LABEL);
    String           eventStartDate = eventValues.getAsString(ContactsContract.CommonDataKinds.Event.START_DATE);
    SimpleDateFormat formatter      = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    if (eventType == null || eventStartDate == null) {
      Log.w(TAG, "event type or event start date is null, not adding anything");
      return;
    }

    try {

      if (eventType == ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY) {
        /*
          aCalendar birthday with "year unknown" option...
          https://github.com/WhisperSystems/Flock/issues/26
        */
        if (eventStartDate.startsWith("-"))
          eventStartDate = "0001" + eventStartDate.substring(1);

        Birthday birthday = new Birthday(formatter.parse(eventStartDate));
        vCard.setBirthday(birthday);
      }

    } catch (ParseException e) {
      throw new InvalidLocalComponentException("caught exception while parsing birthday",
                                               CardDavConstants.CARDDAV_NAMESPACE, path, getUid(vCard), e);
    }

    if (eventType == ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY)
      vCard.setExtendedProperty(PROPERTY_EVENT_ANNIVERSARY, eventStartDate);
    else if (eventType == ContactsContract.CommonDataKinds.Event.TYPE_OTHER)
      vCard.setExtendedProperty(PROPERTY_EVENT_OTHER, eventStartDate);
    else if (eventType == ContactsContract.CommonDataKinds.Event.TYPE_CUSTOM)
      vCard.setExtendedProperty(PROPERTY_EVENT_CUSTOM, eventStartDate)
           .setParameter(PARAMETER_EVENT_CUSTOM_LABEL, eventLabel);
  }

  public static String[] getProjectionForSipAddress() {
    return new String[] {
        ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS, // 00
        ContactsContract.CommonDataKinds.SipAddress.TYPE,        // 01
        ContactsContract.CommonDataKinds.SipAddress.LABEL        // 02
    };
  }

  public static ContentValues getValuesForSipAddress(Cursor cursor) {
    ContentValues values = new ContentValues(3);

    values.put(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS, cursor.getString(0));
    values.put(ContactsContract.CommonDataKinds.SipAddress.TYPE,        cursor.getInt(1));
    values.put(ContactsContract.CommonDataKinds.SipAddress.LABEL,       cursor.getString(2));

    return values;
  }

  public static List<ContentValues> getValuesForSipAddresses(VCard vCard) {
    List<ContentValues> valuesList = new LinkedList<ContentValues>();

    for (RawProperty sipAddress : vCard.getExtendedProperties(PROPERTY_SIP)) {
      ContentValues values = new ContentValues();

      values.put(ContactsContract.Data.MIMETYPE,
                 ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE);

      values.put(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS,
                 sipAddress.getValue());

      values.put(ContactsContract.CommonDataKinds.SipAddress.TYPE,
                 ContactsContract.CommonDataKinds.SipAddress.TYPE_OTHER);

      valuesList.add(values);
    }

    return valuesList;
  }

  public static void addSipAddress(VCard vCard, ContentValues sipAddressValues) {
    String sipAddress = sipAddressValues.getAsString(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS);

    if (sipAddress != null)
      vCard.setExtendedProperty(PROPERTY_SIP, sipAddress);
  }

  public static String[] getProjectionForGroupMembership() {
    return new String[] {
        ContactsContract.Data.DATA1, // 00
    };
  }

  public static Long getIdForGroupMembership(Cursor cursor) {
    return cursor.getLong(0);
  }

  public static void addInvisibleProperty(VCard vCard) {
    Log.d(TAG, "addInvisibleProperty()");
    vCard.setExtendedProperty(PROPERTY_INVISIBLE_CONTACT, "true");
  }

  public static boolean hasInvisibleProperty(VCard vCard) {
    return vCard.getExtendedProperty(PROPERTY_INVISIBLE_CONTACT) != null;
  }

  public static List<AggregationException> getAggregationExceptions(VCard vCard)
      throws IllegalArgumentException
  {
    List<AggregationException> exceptions     = new LinkedList<AggregationException>();
    RawProperty                exceptionsProp = vCard.getExtendedProperty(PROPERTY_AGGREGATION_EXCEPTIONS);

    if (exceptionsProp != null && exceptionsProp.getValue() != null) {
      String[] exceptionStrings = exceptionsProp.getValue().split(",");
      for (String exceptionString : exceptionStrings)
        exceptions.add(AggregationException.build(exceptionString));
    }

    return exceptions;
  }

  public static void addAggregationExceptions(VCard vCard, List<AggregationException> exceptions) {
    if (exceptions.isEmpty())
      return;

    Log.d(TAG, "need to add " + exceptions.size() + " aggregate exceptions.");

    String exceptionsString = null;
    for (AggregationException exception : exceptions) {
      if (exceptionsString != null)
        exceptionsString += "," + exception.toString();
      else
        exceptionsString = exception.toString();
    }

    vCard.setExtendedProperty(PROPERTY_AGGREGATION_EXCEPTIONS, exceptionsString);
  }

  public static class AggregationException {

    private int     type;
    private Account contactAccount;
    private String  contactUid;

    public AggregationException(int type, Account contactAccount, String contactUid) {
      this.type           = type;
      this.contactAccount = contactAccount;
      this.contactUid     = contactUid;
    }

    public static AggregationException build(String stringEncodedAggregateException)
        throws IllegalArgumentException
    {
      String[] parts = stringEncodedAggregateException.split(":");
      if (parts.length < 4)
        throw new IllegalArgumentException("invalid encoding for aggregate exception.");

      try {

        return new AggregationException(
            Integer.parseInt(parts[0]),
            new Account(
                new String(Base64.decode(parts[1])),
                new String(Base64.decode(parts[2]))
            ),
            new String(Base64.decode(parts[3])
        ));

      } catch (IOException e) {
        throw new IllegalArgumentException("invalid encoding for aggregate exception.");
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("invalid encoding for aggregate exception.");
      }
    }

    public int getType() {
      return type;
    }

    public Account getContactAccount() {
      return contactAccount;
    }

    public String getContactUid() {
      return contactUid;
    }

    @Override
    public String toString() {
      return type + ":" + Base64.encodeBytes(contactAccount.name.getBytes()) +
                    ":" + Base64.encodeBytes(contactAccount.type.getBytes()) +
                    ":" + Base64.encodeBytes(contactUid.getBytes());
    }
  }
}
