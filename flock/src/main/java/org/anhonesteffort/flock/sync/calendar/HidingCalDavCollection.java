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

package org.anhonesteffort.flock.sync.calendar;

import android.util.Log;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.webdav.caldav.CalDavConstants;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.model.property.XProperty;
import org.anhonesteffort.flock.crypto.InvalidMacException;
import org.anhonesteffort.flock.crypto.MasterCipher;
import org.anhonesteffort.flock.sync.HidingDavCollection;
import org.anhonesteffort.flock.sync.HidingDavCollectionMixin;
import org.anhonesteffort.flock.sync.HidingUtil;
import org.anhonesteffort.flock.sync.OwsWebDav;
import org.anhonesteffort.flock.webdav.ComponentETagPair;
import org.anhonesteffort.flock.webdav.InvalidComponentException;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.anhonesteffort.flock.webdav.caldav.CalDavCollection;
import org.anhonesteffort.flock.webdav.caldav.CalDavStore;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.util.LinkedList;
import java.util.List;

/**
 * Programmer: rhodey
 */
public class HidingCalDavCollection extends CalDavCollection implements HidingDavCollection<Calendar> {

  private static final String TAG = "org.anhonesteffort.flock.sync.calendar.HidingCalDavCollection";

  protected static final String PROPERTY_NAME_FLOCK_HIDDEN_CALENDAR = "X-FLOCK-HIDDEN-CALENDAR";
  protected static final String PROPERTY_NAME_HIDDEN_COLOR          = "X-FLOCK-HIDDEN-CALENDAR-COLOR";

  protected static final DavPropertyName PROPERTY_HIDDEN_COLOR = DavPropertyName.create(
      PROPERTY_NAME_HIDDEN_COLOR,
      OwsWebDav.NAMESPACE
  );

  private MasterCipher             masterCipher;
  private HidingDavCollectionMixin delegate;

  protected HidingCalDavCollection(CalDavStore calDavStore, String path, MasterCipher masterCipher) {
    super(calDavStore, path, new DavPropertySet());

    this.masterCipher = masterCipher;
    this.delegate     = new HidingDavCollectionMixin(this, masterCipher);
  }

  protected HidingCalDavCollection(CalDavCollection calDavCollection, MasterCipher masterCipher) {
    super((CalDavStore) calDavCollection.getStore(),
          calDavCollection.getPath(),
          calDavCollection.getProperties());

    this.masterCipher = masterCipher;
    this.delegate     = new HidingDavCollectionMixin(this, masterCipher);
  }

  @Override
  protected DavPropertyNameSet getPropertyNamesForFetch() {
    DavPropertyNameSet calendarProps = super.getPropertyNamesForFetch();
    DavPropertyNameSet hidingProps   = delegate.getPropertyNamesForFetch();

    calendarProps.addAll(hidingProps);
    calendarProps.add(PROPERTY_HIDDEN_COLOR);

    return calendarProps;
  }

  @Override
  public boolean isFlockCollection() throws PropertyParseException {
    return delegate.isFlockCollection();
  }

  @Override
  public void makeFlockCollection(String displayName)
      throws DavException, IOException, GeneralSecurityException
  {
    DavPropertyNameSet removeNameSet = new DavPropertyNameSet();
    removeNameSet.add(CalDavConstants.PROPERTY_NAME_CALENDAR_COLOR);

    delegate.makeFlockCollection(displayName, removeNameSet);
  }

  @Override
  public Optional<String> getHiddenDisplayName()
      throws PropertyParseException, InvalidMacException,
      GeneralSecurityException, IOException
  {
    return delegate.getHiddenDisplayName();
  }

  @Override
  public void setHiddenDisplayName(String displayName)
      throws DavException, IOException, GeneralSecurityException
  {
    delegate.setHiddenDisplayName(displayName);
  }

  public Optional<Integer> getHiddenColor()
      throws PropertyParseException, InvalidMacException,
             GeneralSecurityException, IOException
  {
    Optional<String> hiddenColor = getProperty(PROPERTY_HIDDEN_COLOR, String.class);

    if (!hiddenColor.isPresent())
      return getColor();

    String hiddenColorString = HidingUtil.decodeAndDecryptIfNecessary(masterCipher, hiddenColor.get());

    return Optional.of(Integer.valueOf(hiddenColorString));
  }

  public void setHiddenColor(Integer color)
      throws DavException, IOException, GeneralSecurityException
  {
    String hiddenColorString = HidingUtil.encryptEncodeAndPrefix(masterCipher, color.toString());

    DavPropertySet updateProperties = new DavPropertySet();
    updateProperties.add(new DefaultDavProperty<String>(PROPERTY_HIDDEN_COLOR, hiddenColorString));

    patchProperties(updateProperties, new DavPropertyNameSet());
  }

  @Override
  public Optional<ComponentETagPair<Calendar>> getHiddenComponent(String uid)
      throws InvalidComponentException, DavException,
      InvalidMacException, GeneralSecurityException, IOException
  {
    Optional<ComponentETagPair<Calendar>> originalComponentPair = super.getComponent(uid);
    if (!originalComponentPair.isPresent())
      return Optional.absent();

    Calendar  exposedComponent   = originalComponentPair.get().getComponent();
    XProperty protectedComponent = (XProperty) exposedComponent.getProperty(PROPERTY_NAME_FLOCK_HIDDEN_CALENDAR);

    if (protectedComponent == null)
      return originalComponentPair;

    String          recoveredComponentText = HidingUtil.decodeAndDecryptIfNecessary(masterCipher, protectedComponent.getValue());
    StringReader    stringReader           = new StringReader(recoveredComponentText);
    CalendarBuilder calendarBuilder        = new CalendarBuilder();

    try {

      Calendar recoveredComponent = calendarBuilder.build(stringReader);
      return Optional.of(new ComponentETagPair<Calendar>(recoveredComponent,
                                                         originalComponentPair.get().getETag()));

    } catch (ParserException e) {
      Log.e(TAG, "caught exception while trying to build from hidden component", e);
      throw new InvalidComponentException("caught exception while trying to build from hidden component",
                                          true, CalDavConstants.CALDAV_NAMESPACE, getPath(), uid, e);
    }
  }

  @Override
  public List<ComponentETagPair<Calendar>> getHiddenComponents()
      throws InvalidComponentException, DavException,
      InvalidMacException, GeneralSecurityException, IOException
  {
    List<ComponentETagPair<Calendar>> exposedComponentPairs   = super.getComponents();
    List<ComponentETagPair<Calendar>> recoveredComponentPairs = new LinkedList<ComponentETagPair<Calendar>>();

    for (ComponentETagPair<Calendar> exposedComponentPair : exposedComponentPairs) {
      Calendar  exposedComponent   = exposedComponentPair.getComponent();
      XProperty protectedComponent = (XProperty) exposedComponent.getProperty(PROPERTY_NAME_FLOCK_HIDDEN_CALENDAR);

      if   (protectedComponent == null)
        recoveredComponentPairs.add(exposedComponentPair);
      else {
        String          recoveredComponentText = HidingUtil.decodeAndDecryptIfNecessary(masterCipher, protectedComponent.getValue());
        StringReader    stringReader           = new StringReader(recoveredComponentText);
        CalendarBuilder calendarBuilder        = new CalendarBuilder();

        try {

          Calendar recoveredComponent = calendarBuilder.build(stringReader);
          recoveredComponentPairs.add(new ComponentETagPair<Calendar>(recoveredComponent,
                                                                      exposedComponentPair.getETag()));

        } catch (ParserException e) {
          Log.e(TAG, "caught exception while trying to build from hidden component", e);
          throw new InvalidComponentException("caught exception while trying to build from hidden component",
                                              true, CalDavConstants.CALDAV_NAMESPACE, getPath(), e);
        }
      }
    }

    return recoveredComponentPairs;
  }

  // NOTICE: All events starting within a given month will appear to start on the first day
  // NOTICE... of the month and end on the last day of this month... is this acceptable???
  protected void putHiddenComponentToServer(Calendar exposedComponent, Optional<String> ifMatchETag)
      throws InvalidComponentException, GeneralSecurityException, IOException, DavException
  {
    exposedComponent.getProperties().remove(ProdId.PRODID);
    exposedComponent.getProperties().add(new ProdId(((CalDavStore)getStore()).getProductId()));

    Calendar protectedComponent = new Calendar();
    protectedComponent.getProperties().add(Version.VERSION_2_0);
    protectedComponent.getProperties().add(CalScale.GREGORIAN);

    java.util.Calendar calendar     = java.util.Calendar.getInstance();
    VEvent             exposedEvent = (VEvent) exposedComponent.getComponent(VEvent.VEVENT);
    VToDo              exposedToDo  = (VToDo ) exposedComponent.getComponent(VEvent.VTODO);

    if (exposedEvent != null) {
      if (exposedEvent.getUid() == null || exposedEvent.getUid().getValue() == null) {
        Log.e(TAG, "was given a VEVENT with no UID");
        throw new InvalidComponentException("Cannot put an iCal to server without UID!",
                                            false, CalDavConstants.CALDAV_NAMESPACE, getPath());
      }

      Date startDate = exposedEvent.getStartDate().getDate();
      calendar.setTime(new java.util.Date(startDate.getTime()));

      calendar.set(java.util.Calendar.DAY_OF_MONTH, 1);
      calendar.set(java.util.Calendar.HOUR,         1);
      calendar.set(java.util.Calendar.MINUTE,       1);
      calendar.set(java.util.Calendar.SECOND,       1);
      calendar.set(java.util.Calendar.MILLISECOND,  1);

      Date  approximateEndDate = new Date(calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH));
      DtEnd approximateDtEnd   = new DtEnd(approximateEndDate);

      VEvent protectedEvent = new VEvent(new Date(calendar.getTime()), "Open Whisper Systems - Flock");
      protectedEvent.getProperties().add(approximateDtEnd);
      protectedEvent.getProperties().add(exposedEvent.getUid());

      protectedComponent.getComponents().add(protectedEvent);
    }
    else if (exposedToDo != null) {
      if (exposedToDo.getUid() == null || exposedToDo.getUid().getValue() == null) {
        Log.e(TAG, "was given a VTODO with no UID");
        throw new InvalidComponentException("Cannot put an iCal to server without UID!",
                                            false, CalDavConstants.CALDAV_NAMESPACE, getPath());
      }

      Date startDate = exposedToDo.getStartDate().getDate();
      calendar.setTime(new java.util.Date(startDate.getTime()));

      calendar.set(java.util.Calendar.DAY_OF_MONTH, 1);
      calendar.set(java.util.Calendar.HOUR, 1);
      calendar.set(java.util.Calendar.MINUTE, 1);
      calendar.set(java.util.Calendar.SECOND, 1);
      calendar.set(java.util.Calendar.MILLISECOND, 1);

      Date  approximateEndDate = new Date(calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH));
      DtEnd approximateDtEnd   = new DtEnd(approximateEndDate);

      VToDo protectedToDo = new VToDo(new Date(calendar.getTime()), "Open Whisper Systems - Flock");
      protectedToDo.getProperties().add(approximateDtEnd);
      protectedToDo.getProperties().add(exposedToDo.getUid());

      protectedComponent.getComponents().add(protectedToDo);
    }
    else {
      Log.e(TAG, "was given an calendar component containing neither VEVENT or VTODO");
      throw new InvalidComponentException("was given an calendar component containing neither VEVENT or VTODO",
                                          false, CalDavConstants.CALDAV_NAMESPACE, getPath());
    }

    CalendarOutputter     calendarOutputter = new CalendarOutputter();
    ByteArrayOutputStream byteStream        = new ByteArrayOutputStream();

    try {

      calendarOutputter.output(exposedComponent, byteStream);
      String    protectedComponentData = HidingUtil.encryptEncodeAndPrefix(masterCipher, byteStream.toString());
      XProperty xSecureSyncHiddenProp  = new XProperty(PROPERTY_NAME_FLOCK_HIDDEN_CALENDAR, protectedComponentData);
      protectedComponent.getProperties().add(xSecureSyncHiddenProp);

      super.putComponentToServer(protectedComponent, ifMatchETag);

    } catch (ValidationException e) {
      Log.e(TAG, "caught exception while trying to output component to byte stream", e);
      throw new InvalidComponentException("Caught exception while trying to output component to byte stream",
                                          false, CalDavConstants.CALDAV_NAMESPACE, getPath(), e);
    }
  }

  @Override
  public void addHiddenComponent(Calendar component)
      throws InvalidComponentException, DavException, GeneralSecurityException, IOException
  {
    putHiddenComponentToServer(component, Optional.<String>absent());
  }

  @Override
  public void updateHiddenComponent(ComponentETagPair<Calendar> component)
      throws InvalidComponentException, DavException, GeneralSecurityException, IOException
  {
    putHiddenComponentToServer(component.getComponent(), component.getETag());
  }

  @Override
  public void closeHttpConnection() {
    getStore().closeHttpConnection();
  }
}