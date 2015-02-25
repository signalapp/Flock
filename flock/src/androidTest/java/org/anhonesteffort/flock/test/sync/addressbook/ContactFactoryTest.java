/*
 * Copyright (C) 2015 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.anhonesteffort.flock.test.sync.addressbook;

import android.accounts.Account;
import android.content.ContentValues;
import android.provider.ContactsContract;
import android.test.AndroidTestCase;

import org.anhonesteffort.flock.sync.addressbook.ContactFactory;
import org.anhonesteffort.flock.test.InstrumentationTestCaseWithMocks;
import org.anhonesteffort.flock.util.Base64;
import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.webdav.ComponentETagPair;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import ezvcard.VCard;
import ezvcard.parameter.AddressType;
import ezvcard.parameter.EmailType;
import ezvcard.parameter.ImageType;
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
import ezvcard.util.StringUtils;

/**
 * rhodey
 */
public class ContactFactoryTest extends AndroidTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  public void testGetValuesForRawContactNotStarred() throws Exception {
    final String UID  = "such-unique";
    final String ETAG = "much-change";

    final VCard                    inVCard     = new VCard();
    final ComponentETagPair<VCard> inVCardPair = new ComponentETagPair<>(inVCard, Optional.of(ETAG));

    inVCard.setUid(new Uid(UID));
    inVCard.setExtendedProperty(ContactFactory.PROPERTY_STARRED, "0");

    final ContentValues outValues = ContactFactory.getValuesForRawContact(inVCardPair);

    assertTrue(outValues.getAsString(ContactFactory.COLUMN_NAME_CONTACT_UID).equals(UID));
    assertTrue(outValues.getAsString(ContactFactory.COLUMN_NAME_CONTACT_ETAG).equals(ETAG));
    assertTrue(!outValues.getAsBoolean(ContactsContract.RawContacts.STARRED));
  }

  public void testGetValuesForRawContactStarred() throws Exception {
    final String UID  = "such-unique";
    final String ETAG = "much-change";

    final VCard                    inVCard     = new VCard();
    final ComponentETagPair<VCard> inVCardPair = new ComponentETagPair<>(inVCard, Optional.of(ETAG));

    inVCard.setUid(new Uid(UID));
    inVCard.setExtendedProperty(ContactFactory.PROPERTY_STARRED, "1");

    final ContentValues outValues = ContactFactory.getValuesForRawContact(inVCardPair);

    assertTrue(outValues.getAsString(ContactFactory.COLUMN_NAME_CONTACT_UID).equals(UID));
    assertTrue(outValues.getAsString(ContactFactory.COLUMN_NAME_CONTACT_ETAG).equals(ETAG));
    assertTrue(outValues.getAsBoolean(ContactsContract.RawContacts.STARRED));
  }

  public void testGetVCardNotStarred() throws Exception {
    final String UID  = "such-unique";
    final String ETAG = "much-change";

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactFactory.COLUMN_NAME_CONTACT_UID,  UID);
    inValues.put(ContactFactory.COLUMN_NAME_CONTACT_ETAG, ETAG);
    inValues.put(ContactsContract.RawContacts.STARRED, false);

    final ComponentETagPair<VCard> outVCardPair = ContactFactory.getVCard(inValues);

    assertTrue(outVCardPair.getETag().get().equals(ETAG));
    assertTrue(outVCardPair.getComponent().getUid().getValue().equals(UID));
    assertTrue(outVCardPair.getComponent().getExtendedProperty(ContactFactory.PROPERTY_STARRED).getValue().equals("0"));
  }

  public void testGetVCardStarred() throws Exception {
    final String UID  = "such-unique";
    final String ETAG = "much-change";

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactFactory.COLUMN_NAME_CONTACT_UID,  UID);
    inValues.put(ContactFactory.COLUMN_NAME_CONTACT_ETAG, ETAG);
    inValues.put(ContactsContract.RawContacts.STARRED, true);

    final ComponentETagPair<VCard> outVCardPair = ContactFactory.getVCard(inValues);

    assertTrue(outVCardPair.getETag().get().equals(ETAG));
    assertTrue(outVCardPair.getComponent().getUid().getValue().equals(UID));
    assertTrue(outVCardPair.getComponent().getExtendedProperty(ContactFactory.PROPERTY_STARRED).getValue().equals("1"));
  }

  public void testGetValuesForStructuredName() throws Exception {
    final String DISPLAY_NAME         = "ms. mr. crypto yolo doge jr. sr.";
    final String PREFIXES             = "ms. mr.";
    final String GIVEN_NAME           = "crypto";
    final String MIDDLE_NAME          = "yolo";
    final String FAMILY_NAME          = "doge";
    final String SUFFIXES             = "jr. sr.";
    final String PHONETIC_GIVEN_NAME  = "crip-toe";
    final String PHONETIC_MIDDLE_NAME = "yo-low";
    final String PHONETIC_FAMILY_NAME = "dough-eh";

    final VCard          inVCard          = new VCard();
    final StructuredName inStructuredName = new StructuredName();

    inVCard.setFormattedName(DISPLAY_NAME);
    inVCard.setStructuredName(inStructuredName);

    inStructuredName.addPrefix(PREFIXES.split(" ")[0]);
    inStructuredName.addPrefix(PREFIXES.split(" ")[1]);
    inStructuredName.setGiven(GIVEN_NAME);
    inStructuredName.addAdditional(MIDDLE_NAME);
    inStructuredName.setFamily(FAMILY_NAME);
    inStructuredName.addSuffix(SUFFIXES.split(" ")[0]);
    inStructuredName.addSuffix(SUFFIXES.split(" ")[1]);

    inVCard.setExtendedProperty(ContactFactory.PROPERTY_PHONETIC_GIVEN_NAME,  PHONETIC_GIVEN_NAME);
    inVCard.setExtendedProperty(ContactFactory.PROPERTY_PHONETIC_MIDDLE_NAME, PHONETIC_MIDDLE_NAME);
    inVCard.setExtendedProperty(ContactFactory.PROPERTY_PHONETIC_FAMILY_NAME, PHONETIC_FAMILY_NAME);

    final ContentValues outValues = ContactFactory.getValuesForStructuredName(inVCard).get();

    assertTrue(outValues.getAsString(ContactsContract.Data.MIMETYPE).equals(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME).equals(DISPLAY_NAME));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredName.PREFIX).equals(PREFIXES));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME).equals(GIVEN_NAME));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME).equals(MIDDLE_NAME));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME).equals(FAMILY_NAME));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredName.SUFFIX).equals(SUFFIXES));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_GIVEN_NAME).equals(PHONETIC_GIVEN_NAME));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_MIDDLE_NAME).equals(PHONETIC_MIDDLE_NAME));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_FAMILY_NAME).equals(PHONETIC_FAMILY_NAME));
  }

  public void testAddStructuredName() throws Exception {
    final String DISPLAY_NAME         = "ms. mr. crypto yolo doge jr. sr.";
    final String PREFIXES             = "ms. mr.";
    final String GIVEN_NAME           = "crypto";
    final String MIDDLE_NAME          = "yolo";
    final String FAMILY_NAME          = "doge";
    final String SUFFIXES             = "jr. sr.";
    final String PHONETIC_GIVEN_NAME  = "crip-toe";
    final String PHONETIC_MIDDLE_NAME = "yo-low";
    final String PHONETIC_FAMILY_NAME = "dough-eh";

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, DISPLAY_NAME);
    inValues.put(ContactsContract.CommonDataKinds.StructuredName.PREFIX, PREFIXES);
    inValues.put(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, GIVEN_NAME);
    inValues.put(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, MIDDLE_NAME);
    inValues.put(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, FAMILY_NAME);
    inValues.put(ContactsContract.CommonDataKinds.StructuredName.SUFFIX, SUFFIXES);
    inValues.put(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_GIVEN_NAME, PHONETIC_GIVEN_NAME);
    inValues.put(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_MIDDLE_NAME, PHONETIC_MIDDLE_NAME);
    inValues.put(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_FAMILY_NAME, PHONETIC_FAMILY_NAME);

    final VCard outVCard = new VCard();
    ContactFactory.addStructuredName(outVCard, inValues);

    final FormattedName  outFormattedName  = outVCard.getFormattedName();
    final StructuredName outStructuredName = outVCard.getStructuredName();

    assertTrue(outFormattedName.getValue().equals(DISPLAY_NAME));
    assertTrue(StringUtils.join(outStructuredName.getPrefixes(), " ").equals(PREFIXES));
    assertTrue(outStructuredName.getGiven().equals(GIVEN_NAME));
    assertTrue(StringUtils.join(outStructuredName.getAdditional(), " ").equals(MIDDLE_NAME));
    assertTrue(outStructuredName.getFamily().equals(FAMILY_NAME));
    assertTrue(StringUtils.join(outStructuredName.getSuffixes(), " ").equals(SUFFIXES));
    assertTrue(outVCard.getExtendedProperty(ContactFactory.PROPERTY_PHONETIC_GIVEN_NAME).getValue().equals(PHONETIC_GIVEN_NAME));
    assertTrue(outVCard.getExtendedProperty(ContactFactory.PROPERTY_PHONETIC_MIDDLE_NAME).getValue().equals(PHONETIC_MIDDLE_NAME));
    assertTrue(outVCard.getExtendedProperty(ContactFactory.PROPERTY_PHONETIC_FAMILY_NAME).getValue().equals(PHONETIC_FAMILY_NAME));
  }

  public void testGetValuesForPhoneNumbers() throws Exception {
    final String          PHONE_NUMBER = "5555555555";
    final TelephoneType[] PHONE_TYPES  = {TelephoneType.CELL, TelephoneType.PREF};

    final VCard     inVCard     = new VCard();
    final Telephone inTelephone = new Telephone(PHONE_NUMBER);

    for(TelephoneType type : PHONE_TYPES)
      inTelephone.addType(type);

    inVCard.addTelephoneNumber(inTelephone);

    final List<ContentValues> outValuesList = ContactFactory.getValuesForPhoneNumbers(inVCard);

    assertTrue(outValuesList.size() == 1);
    final ContentValues outValues = outValuesList.get(0);

    final String  outMimeType       = outValues.getAsString(ContactsContract.Data.MIMETYPE);
    final String  outPhoneNumber    = outValues.getAsString(ContactsContract.CommonDataKinds.Phone.NUMBER);
    final Integer outPhoneType      = outValues.getAsInteger(ContactsContract.CommonDataKinds.Phone.TYPE);
    final Integer outPhonePref      = outValues.getAsInteger(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY);
    final Integer outPhoneSuperPref = outValues.getAsInteger(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY);

    assertTrue(outMimeType.equals(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE));
    assertTrue(outPhoneNumber.equals(PHONE_NUMBER));
    assertTrue(outPhoneType.equals(ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE));
    assertTrue(outPhonePref == 1);
    assertTrue(outPhoneSuperPref == 1);
  }

  public void testGetValuesForPhoneNumbersWithCustomLabel() throws Exception {
    final String        PHONE_NUMBER     = "5555555555";
    final String        PHONE_TYPE_LABEL = "dogephone";
    final TelephoneType PHONE_TYPE       = TelephoneType.get(ContactFactory.labelToPropertyName(PHONE_TYPE_LABEL));

    final VCard     inVCard     = new VCard();
    final Telephone inTelephone = new Telephone(PHONE_NUMBER);

    inTelephone.addType(PHONE_TYPE);
    inVCard.addTelephoneNumber(inTelephone);

    final List<ContentValues> outValuesList = ContactFactory.getValuesForPhoneNumbers(inVCard);

    assertTrue(outValuesList.size() == 1);
    final ContentValues outValues = outValuesList.get(0);

    final String  outMimeType       = outValues.getAsString(ContactsContract.Data.MIMETYPE);
    final String  outPhoneNumber    = outValues.getAsString(ContactsContract.CommonDataKinds.Phone.NUMBER);
    final Integer outPhoneType      = outValues.getAsInteger(ContactsContract.CommonDataKinds.Phone.TYPE);
    final String  outPhoneTypeLabel = outValues.getAsString(ContactsContract.CommonDataKinds.Phone.LABEL);
    final Integer outPhonePref      = outValues.getAsInteger(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY);
    final Integer outPhoneSuperPref = outValues.getAsInteger(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY);

    assertTrue(outMimeType.equals(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE));
    assertTrue(outPhoneNumber.equals(PHONE_NUMBER));
    assertTrue(outPhoneType.equals(ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM));
    assertTrue(outPhoneTypeLabel.toLowerCase().equals(PHONE_TYPE_LABEL.toLowerCase()));
    assertTrue(outPhonePref == 0);
    assertTrue(outPhoneSuperPref == 0);
  }

  public void testAddPhoneNumber() throws Exception {
    final String  PHONE_NUMBER = "5555555555";
    final Integer PHONE_TYPE   = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE;

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactsContract.CommonDataKinds.Phone.NUMBER,           PHONE_NUMBER);
    inValues.put(ContactsContract.CommonDataKinds.Phone.TYPE,             PHONE_TYPE);
    inValues.put(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY,       true);
    inValues.put(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY, true);

    final VCard outVCard = new VCard();
    ContactFactory.addPhoneNumber(outVCard, inValues);

    assertTrue(outVCard.getTelephoneNumbers().size() == 1);
    final Telephone outPhone = outVCard.getTelephoneNumbers().get(0);

    assertTrue(outPhone.getText().equals(PHONE_NUMBER));
    assertTrue(outPhone.getTypes().size() == 2);
    assertTrue(outPhone.getTypes().contains(TelephoneType.CELL));
    assertTrue(outPhone.getTypes().contains(TelephoneType.PREF));
  }

  public void testAddPhoneNumberWithCustomLabel() throws Exception {
    final String  PHONE_NUMBER     = "5555555555";
    final String  PHONE_TYPE_LABEL = "dogephone";
    final Integer PHONE_TYPE       = ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM;

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactsContract.CommonDataKinds.Phone.NUMBER, PHONE_NUMBER);
    inValues.put(ContactsContract.CommonDataKinds.Phone.TYPE,   PHONE_TYPE);
    inValues.put(ContactsContract.CommonDataKinds.Phone.LABEL,  PHONE_TYPE_LABEL);

    final VCard outVCard = new VCard();
    ContactFactory.addPhoneNumber(outVCard, inValues);

    assertTrue(outVCard.getTelephoneNumbers().size() == 1);
    final Telephone outPhone = outVCard.getTelephoneNumbers().get(0);

    assertTrue(outPhone.getText().equals(PHONE_NUMBER));
    assertTrue(outPhone.getTypes().size() == 1);

    final TelephoneType outPhoneType  = outPhone.getTypes().iterator().next();
    final String        outPhoneLabel = ContactFactory.propertyNameToLabel(outPhoneType.getValue());

    assertTrue(outPhoneLabel.toLowerCase().equals(PHONE_TYPE_LABEL.toLowerCase()));
  }

  public void testGetValuesForEmailAddress() throws Exception {
    final String      EMAIL_ADDRESS = "crypto@doge.wut";
    final EmailType[] EMAIL_TYPES   = {EmailType.HOME, EmailType.PREF};

    final VCard inVCard = new VCard();
    final Email inEmail = new Email(EMAIL_ADDRESS);

    for (EmailType emailType : EMAIL_TYPES)
      inEmail.addType(emailType);
    inVCard.addEmail(inEmail);

    final List<ContentValues> outValuesList = ContactFactory.getValuesForEmailAddresses(inVCard);

    assertTrue(outValuesList.size() == 1);
    final ContentValues outValues = outValuesList.get(0);

    final String  outMimeType       = outValues.getAsString(ContactsContract.Data.MIMETYPE);
    final String  outEmailAddress   = outValues.getAsString(ContactsContract.CommonDataKinds.Email.ADDRESS);
    final Integer outEmailType      = outValues.getAsInteger(ContactsContract.CommonDataKinds.Email.TYPE);
    final Integer outEmailPref      = outValues.getAsInteger(ContactsContract.CommonDataKinds.Email.IS_PRIMARY);
    final Integer outEmailSuperPref = outValues.getAsInteger(ContactsContract.CommonDataKinds.Email.IS_SUPER_PRIMARY);

    assertTrue(outMimeType.equals(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE));
    assertTrue(outEmailAddress.equals(EMAIL_ADDRESS));
    assertTrue(outEmailType.equals(ContactsContract.CommonDataKinds.Email.TYPE_HOME));
    assertTrue(outEmailPref == 1);
    assertTrue(outEmailSuperPref == 1);
  }

  public void testGetValuesForEmailAddressWithCustomLabel() throws Exception {
    final String    EMAIL_ADDRESS    = "crypto@doge.wut";
    final String    EMAIL_TYPE_LABEL = "dogemail";
    final EmailType EMAIL_TYPE       = EmailType.get(ContactFactory.labelToPropertyName(EMAIL_TYPE_LABEL));

    final VCard inVCard = new VCard();
    final Email inEmail = new Email(EMAIL_ADDRESS);

    inEmail.addType(EMAIL_TYPE);
    inVCard.addEmail(inEmail);

    final List<ContentValues> outValuesList = ContactFactory.getValuesForEmailAddresses(inVCard);

    assertTrue(outValuesList.size() == 1);
    final ContentValues outValues = outValuesList.get(0);

    final String  outMimeType       = outValues.getAsString(ContactsContract.Data.MIMETYPE);
    final String  outEmailAddress   = outValues.getAsString(ContactsContract.CommonDataKinds.Email.ADDRESS);
    final Integer outEmailType      = outValues.getAsInteger(ContactsContract.CommonDataKinds.Email.TYPE);
    final String  outEmailTypeLabel = outValues.getAsString(ContactsContract.CommonDataKinds.Email.LABEL);
    final Integer outEmailPref      = outValues.getAsInteger(ContactsContract.CommonDataKinds.Email.IS_PRIMARY);
    final Integer outEmailSuperPref = outValues.getAsInteger(ContactsContract.CommonDataKinds.Email.IS_SUPER_PRIMARY);

    assertTrue(outMimeType.equals(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE));
    assertTrue(outEmailAddress.equals(EMAIL_ADDRESS));
    assertTrue(outEmailType.equals(ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM));
    assertTrue(outEmailTypeLabel.toLowerCase().equals(EMAIL_TYPE_LABEL.toLowerCase()));
    assertTrue(outEmailPref == 0);
    assertTrue(outEmailSuperPref == 0);
  }

  public void testAddEmailAddress() throws Exception {
    final String  EMAIL_ADDRESS      = "crypto@doge.wut";
    final Integer EMAIL_ADDRESS_TYPE = ContactsContract.CommonDataKinds.Email.TYPE_HOME;

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactsContract.CommonDataKinds.Email.ADDRESS,          EMAIL_ADDRESS);
    inValues.put(ContactsContract.CommonDataKinds.Email.TYPE,             EMAIL_ADDRESS_TYPE);
    inValues.put(ContactsContract.CommonDataKinds.Email.IS_PRIMARY,       true);
    inValues.put(ContactsContract.CommonDataKinds.Email.IS_SUPER_PRIMARY, true);

    final VCard outVCard = new VCard();
    ContactFactory.addEmailAddress(outVCard, inValues);

    final List<Email> outEmailList = outVCard.getEmails();

    assertTrue(outEmailList.size() == 1);
    final Email outEmail = outEmailList.get(0);

    assertTrue(outEmail.getValue().equals(EMAIL_ADDRESS));
    assertTrue(outEmail.getTypes().size() == 2);
    assertTrue(outEmail.getTypes().contains(EmailType.HOME));
    assertTrue(outEmail.getTypes().contains(EmailType.PREF));
  }

  public void testAddEmailAddressWithCustomLabel() throws Exception {
    final String  EMAIL_ADDRESS            = "crypto@doge.wut";
    final String  EMAIL_ADDRESS_TYPE_LABEL = "dogemail";
    final Integer EMAIL_ADDRESS_TYPE       = ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM;

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactsContract.CommonDataKinds.Email.ADDRESS, EMAIL_ADDRESS);
    inValues.put(ContactsContract.CommonDataKinds.Email.TYPE,    EMAIL_ADDRESS_TYPE);
    inValues.put(ContactsContract.CommonDataKinds.Email.LABEL,   EMAIL_ADDRESS_TYPE_LABEL);

    final VCard outVCard = new VCard();
    ContactFactory.addEmailAddress(outVCard, inValues);

    final List<Email> outEmailList = outVCard.getEmails();

    assertTrue(outEmailList.size() == 1);
    final Email outEmail = outEmailList.get(0);

    assertTrue(outEmail.getValue().equals(EMAIL_ADDRESS));
    assertTrue(outEmail.getTypes().size() == 1);

    final EmailType outEmailType      = outEmail.getTypes().iterator().next();
    final String    outEmailTypeLabel = ContactFactory.propertyNameToLabel(outEmailType.getValue());

    assertTrue(outEmailTypeLabel.toLowerCase().equals(EMAIL_ADDRESS_TYPE_LABEL));
  }

  public void testGetPhotoForValues() throws Exception {
    final byte[]        PHOTO_BYTES = {0x00, 0x01, 0x02, 0x03};
    final ContentValues values      = new ContentValues();

    values.put(ContactsContract.CommonDataKinds.Photo.PHOTO, PHOTO_BYTES);

    final Photo outPhoto = ContactFactory.getPhotoForValues(values).get();

    assertTrue(Arrays.equals(outPhoto.getData(), PHOTO_BYTES));
  }

  public void testGetValuesForPhoto() throws Exception {
    final byte[]PHOTO_BYTES = {0x00, 0x01, 0x02, 0x03};

    final Photo inPhoto = new Photo(PHOTO_BYTES, ImageType.PNG);
    final VCard inVCard = new VCard();

    inVCard.addPhoto(inPhoto);

    ContentValues outValues = ContactFactory.getValuesForPhoto(inVCard).get();

    assertTrue(outValues.getAsString(ContactsContract.Data.MIMETYPE).equals(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE));
    assertTrue(Arrays.equals(
        outValues.getAsByteArray(ContactsContract.CommonDataKinds.Photo.PHOTO),
        PHOTO_BYTES
    ));
  }

  public void testGetValuesForOrganization() throws Exception {
    final String ORG_NAME  = "Doge LLC";
    final String ORG_TITLE = "CTO";

    final VCard        inVCard = new VCard();
    final Organization inOrg   = new Organization();
    final Role         inRole  = new Role(ORG_TITLE);

    inOrg.addValue(ORG_NAME);
    inVCard.addOrganization(inOrg);
    inVCard.addRole(inRole);

    final List<ContentValues> outValuesList = ContactFactory.getValuesForOrganizations(inVCard);

    assertTrue(outValuesList.size() == 1);
    final ContentValues outValues = outValuesList.get(0);

    assertTrue(outValues.getAsString(ContactsContract.Data.MIMETYPE).equals(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.Organization.COMPANY).equals(ORG_NAME));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.Organization.TITLE).equals(ORG_TITLE));
  }

  public void testAddOrganization() throws Exception {
    final String ORG_NAME  = "Doge LLC";
    final String ORG_TITLE = "CTO";

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactsContract.CommonDataKinds.Organization.COMPANY, ORG_NAME);
    inValues.put(ContactsContract.CommonDataKinds.Organization.TITLE,   ORG_TITLE);

    final VCard outVCard = new VCard();
    ContactFactory.addOrganization(outVCard, inValues);

    assertTrue(outVCard.getOrganizations().size() == 1);
    assertTrue(outVCard.getRoles().size() == 1);

    final Organization outOrg  = outVCard.getOrganizations().get(0);
    final Role         outRole = outVCard.getRoles().get(0);

    assertTrue(outOrg.getValues().size() == 1);
    assertTrue(outOrg.getValues().get(0).equals(ORG_NAME));
    assertTrue(outRole.getValue().equals(ORG_TITLE));
  }

  public void testGetValuesForInstantMessaging() throws Exception {
    final String IM_HANDLE = "dogebot";

    final Impp  inImpp  = Impp.aim(IM_HANDLE);
    final VCard inVCard = new VCard();

    inVCard.addImpp(inImpp);

    final List<ContentValues> outValuesList = ContactFactory.getValuesForInstantMessaging(inVCard);

    assertTrue(outValuesList.size() == 1);
    final ContentValues outValues = outValuesList.get(0);

    assertTrue(outValues.getAsString(ContactsContract.Data.MIMETYPE).equals(ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.Im.DATA).equals(IM_HANDLE));
    assertTrue(outValues.getAsInteger(ContactsContract.CommonDataKinds.Im.PROTOCOL).equals(ContactsContract.CommonDataKinds.Im.PROTOCOL_AIM));
  }

  public void testGetValuesForInstantMessagingWithCustomProtocol() throws Exception {
    final String IM_HANDLE   = "dogebot";
    final String IM_PROTOCOL = "doge-irc";

    final Impp  inImpp  = new Impp(IM_PROTOCOL, IM_HANDLE);
    final VCard inVCard = new VCard();

    inVCard.addImpp(inImpp);

    final List<ContentValues> outValuesList = ContactFactory.getValuesForInstantMessaging(inVCard);

    assertTrue(outValuesList.size() == 1);
    final ContentValues outValues = outValuesList.get(0);

    assertTrue(outValues.getAsString(ContactsContract.Data.MIMETYPE).equals(ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.Im.DATA).equals(IM_HANDLE));
    assertTrue(outValues.getAsInteger(ContactsContract.CommonDataKinds.Im.PROTOCOL).equals(ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL).equals(IM_PROTOCOL));
  }

  public void testAddInstantMessaging() throws Exception {
    final String  IM_HANDLE   = "dogebot";
    final Integer IM_PROTOCOL = ContactsContract.CommonDataKinds.Im.PROTOCOL_AIM;

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactsContract.CommonDataKinds.Im.DATA,     IM_HANDLE);
    inValues.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, IM_PROTOCOL);

    final VCard outVCard = new VCard();
    ContactFactory.addInstantMessaging(outVCard, inValues);

    assertTrue(outVCard.getImpps().size() == 1);
    final Impp outImpp = outVCard.getImpps().get(0);

    assertTrue(outImpp.getHandle().equals(IM_HANDLE));
    assertTrue(outImpp.getProtocol().equals("aim"));
  }

  public void testAddInstantMessagingWithCustomProtocol() throws Exception {
    final String  IM_HANDLE        = "dogebot";
    final Integer IM_PROTOCOL_TYPE = ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM;
    final String  IM_PROTOCOL      = "doge-irc";

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactsContract.CommonDataKinds.Im.DATA,            IM_HANDLE);
    inValues.put(ContactsContract.CommonDataKinds.Im.PROTOCOL,        IM_PROTOCOL_TYPE);
    inValues.put(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL, IM_PROTOCOL);

    final VCard outVCard = new VCard();
    ContactFactory.addInstantMessaging(outVCard, inValues);

    assertTrue(outVCard.getImpps().size() == 1);
    final Impp outImpp = outVCard.getImpps().get(0);

    assertTrue(outImpp.getHandle().equals(IM_HANDLE));
    assertTrue(outImpp.getProtocol().equals(IM_PROTOCOL));
  }

  public void testGetValuesForNickName() throws Exception {
    final String NICK_NAME = "dge";

    final Nickname inNick  = new Nickname();
    final VCard    inVCard = new VCard();

    inNick.addValue(NICK_NAME);
    inVCard.addNickname(inNick);

    final List<ContentValues> outValuesList = ContactFactory.getValuesForNickNames(inVCard);

    assertTrue(outValuesList.size() == 1);
    final ContentValues outValues = outValuesList.get(0);

    assertTrue(outValues.getAsString(ContactsContract.Data.MIMETYPE).equals(ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.Nickname.NAME).equals(NICK_NAME));
  }

  public void testAddNickName() throws Exception {
    final String NICK_NAME = "dge";

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactsContract.CommonDataKinds.Nickname.NAME, NICK_NAME);

    final VCard outVCard = new VCard();
    ContactFactory.addNickName(outVCard, inValues);

    assertTrue(outVCard.getNicknames().size() == 1);
    final Nickname outNick = outVCard.getNicknames().get(0);

    assertTrue(outNick.getValues().size() == 1);
    assertTrue(outNick.getValues().get(0).equals(NICK_NAME));
  }

  public void testGetValuesForNotesWithoutBase64() throws Exception {
    final String NOTE = "hello, this is doge";

    final Note  inNote  = new Note(NOTE);
    final VCard inVCard = new VCard();

    inVCard.addNote(inNote);

    final List<ContentValues> outValuesList = ContactFactory.getValuesForNotes(inVCard);

    assertTrue(outValuesList.size() == 1);
    final ContentValues outValues = outValuesList.get(0);

    assertTrue(outValues.getAsString(ContactsContract.Data.MIMETYPE).equals(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.Note.NOTE).equals(NOTE));
  }

  public void testGetValuesForNotesWithBase64() throws Exception {
    final String NOTE = "hello, this is doge";

    final Note  inNote  = new Note(Base64.encodeBytes(NOTE.getBytes()));
    final VCard inVCard = new VCard();

    inVCard.addNote(inNote);

    final List<ContentValues> outValuesList = ContactFactory.getValuesForNotes(inVCard);

    assertTrue(outValuesList.size() == 1);
    final ContentValues outValues = outValuesList.get(0);

    assertTrue(outValues.getAsString(ContactsContract.Data.MIMETYPE).equals(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.Note.NOTE).equals(NOTE));
  }

  public void testAddNote() throws Exception {
    final String NOTE = "hello, this is doge";

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactsContract.CommonDataKinds.Note.NOTE, NOTE);

    final VCard outVCard = new VCard();
    ContactFactory.addNote(outVCard, inValues);

    assertTrue(outVCard.getNotes().size() == 1);
    final Note outNote = outVCard.getNotes().get(0);

    assertTrue(new String(Base64.decode(outNote.getValue())).equals(NOTE));
  }

  public void testGetValuesForPostalAddressesWithoutBase64() throws Exception {
    final AddressType ADDRESS_TYPE      = AddressType.HOME;
    final String      FORMATTED_ADDRESS = "1337 doge blvd., PO BOX 9001, Oakland, CA 94607, US";
    final String      STREET            = "1337 doge blvd.";
    final String      PO_BOX            = "PO BOX 9001";
    final String      NEIGHBORHOOD      = "";
    final String      LOCALITY          = "Oakland";
    final String      REGION            = "CA";
    final String      POST_CODE         = "94607";
    final String      COUNTRY           = "US";

    final VCard   inVCard   = new VCard();
    final Address inAddress = new Address();

    inAddress.setLabel(FORMATTED_ADDRESS);
    inAddress.setStreetAddress(STREET);
    inAddress.setPoBox(PO_BOX);
    inAddress.setExtendedAddress(NEIGHBORHOOD);
    inAddress.setLocality(LOCALITY);
    inAddress.setRegion(REGION);
    inAddress.setPostalCode(POST_CODE);
    inAddress.setCountry(COUNTRY);
    inAddress.addType(ADDRESS_TYPE);
    inVCard.addAddress(inAddress);

    final List<ContentValues> outValuesList = ContactFactory.getValuesForPostalAddresses(inVCard);

    assertTrue(outValuesList.size() == 1);
    final ContentValues outValues = outValuesList.get(0);

    assertTrue(outValues.getAsString(ContactsContract.Data.MIMETYPE).equals(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE));
    assertTrue(outValues.getAsInteger(ContactsContract.CommonDataKinds.StructuredPostal.TYPE).equals(ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS).equals(FORMATTED_ADDRESS));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.STREET).equals(STREET));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.POBOX).equals(PO_BOX));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.NEIGHBORHOOD).equals(NEIGHBORHOOD));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.CITY).equals(LOCALITY));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.REGION).equals(REGION));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE).equals(POST_CODE));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY).equals(COUNTRY));
  }

  public void testGetValuesForPostalAddressesWithBase64() throws Exception {
    final AddressType ADDRESS_TYPE      = AddressType.HOME;
    final String      FORMATTED_ADDRESS = "1337 doge blvd., PO BOX 9001, Oakland, CA 94607, US";
    final String      STREET            = "1337 doge blvd.";
    final String      PO_BOX            = "PO BOX 9001";
    final String      NEIGHBORHOOD      = "";
    final String      LOCALITY          = "Oakland";
    final String      REGION            = "CA";
    final String      POST_CODE         = "94607";
    final String      COUNTRY           = "US";

    final VCard   inVCard   = new VCard();
    final Address inAddress = new Address();

    inAddress.setLabel(Base64.encodeBytes(FORMATTED_ADDRESS.getBytes()));
    inAddress.setStreetAddress(STREET);
    inAddress.setPoBox(PO_BOX);
    inAddress.setExtendedAddress(NEIGHBORHOOD);
    inAddress.setLocality(LOCALITY);
    inAddress.setRegion(REGION);
    inAddress.setPostalCode(POST_CODE);
    inAddress.setCountry(COUNTRY);
    inAddress.addType(ADDRESS_TYPE);
    inVCard.addAddress(inAddress);

    final List<ContentValues> outValuesList = ContactFactory.getValuesForPostalAddresses(inVCard);

    assertTrue(outValuesList.size() == 1);
    final ContentValues outValues = outValuesList.get(0);

    assertTrue(outValues.getAsString(ContactsContract.Data.MIMETYPE).equals(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE));
    assertTrue(outValues.getAsInteger(ContactsContract.CommonDataKinds.StructuredPostal.TYPE).equals(ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS).equals(FORMATTED_ADDRESS));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.STREET).equals(STREET));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.POBOX).equals(PO_BOX));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.NEIGHBORHOOD).equals(NEIGHBORHOOD));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.CITY).equals(LOCALITY));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.REGION).equals(REGION));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE).equals(POST_CODE));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY).equals(COUNTRY));
  }

  public void testGetValuesForPostalAddressesWithCustomLabel() throws Exception {
    final String      ADDRESS_TYPE_LABEL = "sumtype";
    final AddressType ADDRESS_TYPE       = AddressType.get(ADDRESS_TYPE_LABEL);
    final String      FORMATTED_ADDRESS  = "1337 doge blvd., PO BOX 9001, Oakland, CA 94607, US";
    final String      STREET             = "1337 doge blvd.";
    final String      PO_BOX             = "PO BOX 9001";
    final String      NEIGHBORHOOD       = "";
    final String      LOCALITY           = "Oakland";
    final String      REGION             = "CA";
    final String      POST_CODE          = "94607";
    final String      COUNTRY            = "US";

    final VCard   inVCard   = new VCard();
    final Address inAddress = new Address();

    inAddress.setLabel(FORMATTED_ADDRESS);
    inAddress.setStreetAddress(STREET);
    inAddress.setPoBox(PO_BOX);
    inAddress.setExtendedAddress(NEIGHBORHOOD);
    inAddress.setLocality(LOCALITY);
    inAddress.setRegion(REGION);
    inAddress.setPostalCode(POST_CODE);
    inAddress.setCountry(COUNTRY);
    inAddress.addType(ADDRESS_TYPE);
    inVCard.addAddress(inAddress);

    final List<ContentValues> outValuesList = ContactFactory.getValuesForPostalAddresses(inVCard);

    assertTrue(outValuesList.size() == 1);
    final ContentValues outValues = outValuesList.get(0);

    assertTrue(outValues.getAsString(ContactsContract.Data.MIMETYPE).equals(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE));
    assertTrue(outValues.getAsInteger(ContactsContract.CommonDataKinds.StructuredPostal.TYPE).equals(ContactsContract.CommonDataKinds.StructuredPostal.TYPE_CUSTOM));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS).equals(FORMATTED_ADDRESS));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.STREET).equals(STREET));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.POBOX).equals(PO_BOX));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.NEIGHBORHOOD).equals(NEIGHBORHOOD));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.CITY).equals(LOCALITY));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.REGION).equals(REGION));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE).equals(POST_CODE));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY).equals(COUNTRY));

    final String outAddressTypeLabel = outValues.getAsString(ContactsContract.CommonDataKinds.StructuredPostal.LABEL);

    assertTrue(outAddressTypeLabel.toLowerCase().equals(ADDRESS_TYPE_LABEL.toLowerCase()));
  }

  public void testAddPostalAddress() throws Exception {
    final Integer ADDRESS_TYPE      = ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME;
    final String  FORMATTED_ADDRESS = "1337 doge blvd., PO BOX 9001, Oakland, CA 94607, US";
    final String  STREET            = "1337 doge blvd.";
    final String  PO_BOX            = "PO BOX 9001";
    final String  NEIGHBORHOOD      = "";
    final String  LOCALITY          = "Oakland";
    final String  REGION            = "CA";
    final String  POST_CODE         = "94607";
    final String  COUNTRY           = "US";

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactsContract.CommonDataKinds.StructuredPostal.TYPE,              ADDRESS_TYPE);
    inValues.put(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, FORMATTED_ADDRESS);
    inValues.put(ContactsContract.CommonDataKinds.StructuredPostal.STREET,            STREET);
    inValues.put(ContactsContract.CommonDataKinds.StructuredPostal.POBOX,             PO_BOX);
    inValues.put(ContactsContract.CommonDataKinds.StructuredPostal.NEIGHBORHOOD,      NEIGHBORHOOD);
    inValues.put(ContactsContract.CommonDataKinds.StructuredPostal.CITY,              LOCALITY);
    inValues.put(ContactsContract.CommonDataKinds.StructuredPostal.REGION,            REGION);
    inValues.put(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE,          POST_CODE);
    inValues.put(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY,           COUNTRY);

    final VCard outVCard = new VCard();
    ContactFactory.addPostalAddress(outVCard, inValues);

    assertTrue(outVCard.getAddresses().size() == 1);
    final Address outAddress = outVCard.getAddresses().get(0);

    assertTrue(outAddress.getTypes().size() == 1);
    assertTrue(outAddress.getTypes().iterator().next().equals(AddressType.HOME));
    assertTrue(outAddress.getLabel().equals(Base64.encodeBytes(FORMATTED_ADDRESS.getBytes())));
    assertTrue(outAddress.getStreetAddress().equals(STREET));
    assertTrue(outAddress.getPoBox().equals(PO_BOX));
    assertTrue(outAddress.getExtendedAddress().equals(NEIGHBORHOOD));
    assertTrue(outAddress.getLocality().equals(LOCALITY));
    assertTrue(outAddress.getRegion().equals(REGION));
    assertTrue(outAddress.getPostalCode().equals(POST_CODE));
    assertTrue(outAddress.getCountry().equals(COUNTRY));
  }

  public void testAddPostalAddressWithCustomLabel() throws Exception {
    final Integer ADDRESS_TYPE       = ContactsContract.CommonDataKinds.StructuredPostal.TYPE_CUSTOM;
    final String  ADDRESS_TYPE_LABEL = "sumtype";
    final String  FORMATTED_ADDRESS  = "1337 doge blvd., PO BOX 9001, Oakland, CA 94607, US";
    final String  STREET             = "1337 doge blvd.";
    final String  PO_BOX             = "PO BOX 9001";
    final String  NEIGHBORHOOD       = "";
    final String  LOCALITY           = "Oakland";
    final String  REGION             = "CA";
    final String  POST_CODE          = "94607";
    final String  COUNTRY            = "US";

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactsContract.CommonDataKinds.StructuredPostal.TYPE,              ADDRESS_TYPE);
    inValues.put(ContactsContract.CommonDataKinds.StructuredPostal.LABEL,             ADDRESS_TYPE_LABEL);
    inValues.put(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, FORMATTED_ADDRESS);
    inValues.put(ContactsContract.CommonDataKinds.StructuredPostal.STREET,            STREET);
    inValues.put(ContactsContract.CommonDataKinds.StructuredPostal.POBOX,             PO_BOX);
    inValues.put(ContactsContract.CommonDataKinds.StructuredPostal.NEIGHBORHOOD,      NEIGHBORHOOD);
    inValues.put(ContactsContract.CommonDataKinds.StructuredPostal.CITY,              LOCALITY);
    inValues.put(ContactsContract.CommonDataKinds.StructuredPostal.REGION,            REGION);
    inValues.put(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE,          POST_CODE);
    inValues.put(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY,           COUNTRY);

    final VCard outVCard = new VCard();
    ContactFactory.addPostalAddress(outVCard, inValues);

    assertTrue(outVCard.getAddresses().size() == 1);
    final Address outAddress = outVCard.getAddresses().get(0);

    assertTrue(outAddress.getTypes().size() == 1);
    assertTrue(outAddress.getTypes().iterator().next().getValue().equals(ADDRESS_TYPE_LABEL));
    assertTrue(outAddress.getLabel().equals(Base64.encodeBytes(FORMATTED_ADDRESS.getBytes())));
    assertTrue(outAddress.getStreetAddress().equals(STREET));
    assertTrue(outAddress.getPoBox().equals(PO_BOX));
    assertTrue(outAddress.getExtendedAddress().equals(NEIGHBORHOOD));
    assertTrue(outAddress.getLocality().equals(LOCALITY));
    assertTrue(outAddress.getRegion().equals(REGION));
    assertTrue(outAddress.getPostalCode().equals(POST_CODE));
    assertTrue(outAddress.getCountry().equals(COUNTRY));
  }

  public void testGetValuesForWebsites() throws Exception {
    final String WEBSITE_URL = "https://do.ge";

    final VCard inVCard = new VCard();
    final Url   inUrl   = new Url(WEBSITE_URL);

    inVCard.addUrl(inUrl);

    final List<ContentValues> outValuesList = ContactFactory.getValuesForWebsites(inVCard);

    assertTrue(outValuesList.size() == 1);
    final ContentValues outValues = outValuesList.get(0);

    assertTrue(outValues.getAsString(ContactsContract.Data.MIMETYPE).equals(ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.Website.URL).equals(WEBSITE_URL));
  }

  public void testAddWebsite() throws Exception {
    final String WEBSITE_URL = "https://do.ge";

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactsContract.CommonDataKinds.Website.URL, WEBSITE_URL);

    final VCard outVCard = new VCard();
    ContactFactory.addWebsite(outVCard, inValues);

    assertTrue(outVCard.getUrls().size() == 1);
    assertTrue(outVCard.getUrls().get(0).getValue().equals(WEBSITE_URL));
  }

  public void testGetValuesForEvents() throws Exception {
    final String EVENT_BIRTHDAY     = "2001-01-01";
    final String EVENT_ANNIVERSARY  = "2002-02-02";
    final String EVENT_OTHER        = "2003-03-03";
    final String EVENT_CUSTOM       = "2004-04-04";
    final String EVENT_CUSTOM_LABEL = "nothing really";

    final VCard            inVCard    = new VCard();
    final SimpleDateFormat formatter  = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    final Birthday         inBirthday = new Birthday(formatter.parse(EVENT_BIRTHDAY));

    inVCard.setBirthday(inBirthday);
    inVCard.setExtendedProperty(ContactFactory.PROPERTY_EVENT_ANNIVERSARY, EVENT_ANNIVERSARY);
    inVCard.setExtendedProperty(ContactFactory.PROPERTY_EVENT_OTHER,       EVENT_OTHER);
    inVCard.setExtendedProperty(ContactFactory.PROPERTY_EVENT_CUSTOM,      EVENT_CUSTOM)
           .setParameter(ContactFactory.PARAMETER_EVENT_CUSTOM_LABEL,      EVENT_CUSTOM_LABEL);

    final List<ContentValues> outValuesList = ContactFactory.getValuesForEvents(inVCard);
    assertTrue(outValuesList.size() == 4);

    boolean foundBirthday    = false;
    boolean foundAnniversary = false;
    boolean foundOther       = false;
    boolean foundCustom      = false;

    for (ContentValues outValues : outValuesList) {
      assertTrue(outValues.getAsString(ContactsContract.Data.MIMETYPE).equals(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE));

      switch (outValues.getAsInteger(ContactsContract.CommonDataKinds.Event.TYPE)) {
        case ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY:
          foundBirthday = true;
          assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.Event.START_DATE).equals(EVENT_BIRTHDAY));
          break;

        case ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY:
          foundAnniversary = true;
          assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.Event.START_DATE).equals(EVENT_ANNIVERSARY));
          break;

        case ContactsContract.CommonDataKinds.Event.TYPE_OTHER:
          foundOther = true;
          assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.Event.START_DATE).equals(EVENT_OTHER));
          break;

        case ContactsContract.CommonDataKinds.Event.TYPE_CUSTOM:
          foundCustom = true;
          assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.Event.LABEL).equals(EVENT_CUSTOM_LABEL));
          assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.Event.START_DATE).equals(EVENT_CUSTOM));
          break;

        default:
          throw new RuntimeException("unexpected event type!");
      }
    }

    assertTrue(foundBirthday && foundAnniversary && foundOther && foundCustom);
  }

  public void testAddBirthdayEvent() throws Exception {
    final String EVENT_BIRTHDAY = "2001-01-01";

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactsContract.CommonDataKinds.Event.TYPE,       ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY);
    inValues.put(ContactsContract.CommonDataKinds.Event.START_DATE, EVENT_BIRTHDAY);

    final VCard outVCard = new VCard();
    ContactFactory.addEvent("wow", outVCard, inValues);

    final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    assertTrue(outVCard.getBirthday().getDate().equals(formatter.parse(EVENT_BIRTHDAY)));
  }

  public void testAddAnniversaryEvent() throws Exception {
    final String EVENT_ANNIVERSARY = "2002-02-02";

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactsContract.CommonDataKinds.Event.TYPE,       ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY);
    inValues.put(ContactsContract.CommonDataKinds.Event.START_DATE, EVENT_ANNIVERSARY);

    final VCard outVCard = new VCard();
    ContactFactory.addEvent("wow", outVCard, inValues);

    assertTrue(outVCard.getExtendedProperty(ContactFactory.PROPERTY_EVENT_ANNIVERSARY).getValue().equals(EVENT_ANNIVERSARY));
  }

  public void testAddOtherEvent() throws Exception {
    final String EVENT_OTHER = "2002-03-03";

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactsContract.CommonDataKinds.Event.TYPE,       ContactsContract.CommonDataKinds.Event.TYPE_OTHER);
    inValues.put(ContactsContract.CommonDataKinds.Event.START_DATE, EVENT_OTHER);

    final VCard outVCard = new VCard();
    ContactFactory.addEvent("wow", outVCard, inValues);

    assertTrue(outVCard.getExtendedProperty(ContactFactory.PROPERTY_EVENT_OTHER).getValue().equals(EVENT_OTHER));
  }

  public void testAddCustomEvent() throws Exception {
    final String EVENT_CUSTOM = "2003-03-03";
    final String EVENT_LABEL  = "nothing really";

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactsContract.CommonDataKinds.Event.TYPE,       ContactsContract.CommonDataKinds.Event.TYPE_CUSTOM);
    inValues.put(ContactsContract.CommonDataKinds.Event.START_DATE, EVENT_CUSTOM);
    inValues.put(ContactsContract.CommonDataKinds.Event.LABEL,      EVENT_LABEL);

    final VCard outVCard = new VCard();
    ContactFactory.addEvent("wow", outVCard, inValues);

    final RawProperty outEvent = outVCard.getExtendedProperty(ContactFactory.PROPERTY_EVENT_CUSTOM);

    assertTrue(outEvent.getValue().equals(EVENT_CUSTOM));
    assertTrue(outEvent.getParameter(ContactFactory.PARAMETER_EVENT_CUSTOM_LABEL).equals(EVENT_LABEL));
  }

  public void testGetValuesForSipAddresses() throws Exception {
    final String SIP_ADDRESS = "sip:1-999-123-4567@voip-provider.example.net";

    final VCard inVCard = new VCard();
    inVCard.setExtendedProperty(ContactFactory.PROPERTY_SIP, SIP_ADDRESS);

    final List<ContentValues> outValuesList = ContactFactory.getValuesForSipAddresses(inVCard);

    assertTrue(outValuesList.size() == 1);
    final ContentValues outValues = outValuesList.get(0);

    assertTrue(outValues.getAsString(ContactsContract.Data.MIMETYPE).equals(ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE));
    assertTrue(outValues.getAsInteger(ContactsContract.CommonDataKinds.SipAddress.TYPE).equals(ContactsContract.CommonDataKinds.SipAddress.TYPE_OTHER));
    assertTrue(outValues.getAsString(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS).equals(SIP_ADDRESS));
  }

  public void testAddSipAddress() throws Exception {
    final String SIP_ADDRESS = "sip:1-999-123-4567@voip-provider.example.net";

    final ContentValues inValues = new ContentValues();
    inValues.put(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS, SIP_ADDRESS);

    final VCard outVCard = new VCard();
    ContactFactory.addSipAddress(outVCard, inValues);

    assertTrue(outVCard.getExtendedProperty(ContactFactory.PROPERTY_SIP).getValue().equals(SIP_ADDRESS));
  }

  public void testAddGetAggregationExceptions() throws Exception {
    final Integer AGGREGATE_TYPE  = ContactsContract.AggregationExceptions.TYPE_KEEP_SEPARATE;
    final Account CONTACT_ACCOUNT = new Account("doge@wow", "org.doge.wow");
    final String  CONTACT_UID     = "such-unique";

    final List<ContactFactory.AggregationException> inExceptions = new LinkedList<>();

    inExceptions.add(
        new ContactFactory.AggregationException(AGGREGATE_TYPE, CONTACT_ACCOUNT, CONTACT_UID)
    );
    inExceptions.add(
        new ContactFactory.AggregationException(AGGREGATE_TYPE, CONTACT_ACCOUNT, CONTACT_UID)
    );

    final VCard outInVCard = new VCard();
    ContactFactory.addAggregationExceptions(outInVCard, inExceptions);

    final List<ContactFactory.AggregationException> outExceptions =
        ContactFactory.getAggregationExceptions(outInVCard);

    assertTrue(outExceptions.size() == 2);
    for (ContactFactory.AggregationException outException : outExceptions) {
      assertTrue(outException.getType() == AGGREGATE_TYPE);
      assertTrue(outException.getContactAccount().equals(CONTACT_ACCOUNT));
      assertTrue(outException.getContactUid().equals(CONTACT_UID));
    }
  }

}
