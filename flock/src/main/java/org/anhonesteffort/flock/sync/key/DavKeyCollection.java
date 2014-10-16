package org.anhonesteffort.flock.sync.key;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.common.base.Optional;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;

import org.anhonesteffort.flock.sync.OwsWebDav;
import org.anhonesteffort.flock.webdav.AbstractDavComponentCollection;
import org.anhonesteffort.flock.webdav.InvalidComponentException;
import org.anhonesteffort.flock.webdav.MultiStatusResult;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.anhonesteffort.flock.webdav.caldav.CalDavCollection;
import org.anhonesteffort.flock.webdav.caldav.CalDavConstants;
import org.anhonesteffort.flock.webdav.caldav.CalDavStore;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.security.report.PrincipalMatchReport;
import org.apache.jackrabbit.webdav.version.report.ReportType;

import java.io.IOException;

/**
 * rhodey
 */
public class DavKeyCollection extends AbstractDavComponentCollection<Calendar> {

  private static final String TAG = "org.anhonesteffort.flock.sync.key.DavKeyCollection";

  private static final String KEY_WE_STARTED_MIGRATION = "KEY_WE_STARTED_MIGRATION";

  private static final String UID_MIGRATION_COMPLETE = "migration-complete";
  private static final String UID_MIGRATION_STARTED  = "migration00-started";

  protected static final String PROPERTY_NAME_KEY_MATERIAL_SALT      = "X-KEY-MATERIAL-SALT";
  protected static final String PROPERTY_NAME_ENCRYPTED_KEY_MATERIAL = "X-ENCRYPTED-KEY-MATERIAL";

  protected static final DavPropertyName PROPERTY_KEY_MATERIAL_SALT = DavPropertyName.create(
      PROPERTY_NAME_KEY_MATERIAL_SALT,
      OwsWebDav.NAMESPACE
  );
  protected static final DavPropertyName PROPERTY_ENCRYPTED_KEY_MATERIAL = DavPropertyName.create(
      PROPERTY_NAME_ENCRYPTED_KEY_MATERIAL,
      OwsWebDav.NAMESPACE
  );

  public DavKeyCollection(CalDavStore store, String path) {
    super(store, path);
  }

  public DavKeyCollection(CalDavCollection collection, String path) {
    super(collection.getStore(), path, collection.getProperties());
  }

  @Override
  protected String getComponentPathFromUid(String uid) {
    return getPath().concat(uid.concat(CalDavConstants.ICAL_FILE_EXTENSION));
  }

  @Override
  protected DavPropertyNameSet getPropertyNamesForFetch() {
    DavPropertyNameSet keyMaterialProps = super.getPropertyNamesForFetch();

    keyMaterialProps.add(PROPERTY_KEY_MATERIAL_SALT);
    keyMaterialProps.add(PROPERTY_ENCRYPTED_KEY_MATERIAL);

    return keyMaterialProps;
  }

  @Override
  protected ReportType getQueryReportType() {
    return ReportType.register(CalDavConstants.PROPERTY_CALENDAR_QUERY,
                               CalDavConstants.CALDAV_NAMESPACE,
                               PrincipalMatchReport.class);
  }

  @Override
  protected ReportType getMultiGetReportType() {
    return ReportType.register(CalDavConstants.PROPERTY_CALENDAR_MULTIGET,
                               CalDavConstants.CALDAV_NAMESPACE,
                               PrincipalMatchReport.class);
  }

  @Override
  protected DavPropertyNameSet getPropertyNamesForReports() {
    DavPropertyNameSet calendarProperties = new DavPropertyNameSet();
    calendarProperties.add(CalDavConstants.PROPERTY_NAME_CALENDAR_DATA);
    calendarProperties.add(DavPropertyName.GETETAG);
    return calendarProperties;
  }

  public Optional<String> getKeyMaterialSalt() throws PropertyParseException {
    return getProperty(PROPERTY_KEY_MATERIAL_SALT, String.class);
  }

  public void setKeyMaterialSalt(String keyMaterialSalt)
      throws DavException, IOException
  {
    Log.w(TAG, "setKeyMaterialSalt()");

    DavPropertySet updateProperties = new DavPropertySet();
    updateProperties.add(new DefaultDavProperty<String>(PROPERTY_KEY_MATERIAL_SALT, keyMaterialSalt));

    patchProperties(updateProperties, new DavPropertyNameSet());
  }

  public Optional<String> getEncryptedKeyMaterial() throws PropertyParseException {
    return getProperty(PROPERTY_ENCRYPTED_KEY_MATERIAL, String.class);
  }

  public void setEncryptedKeyMaterial(String encryptedKeyMaterial)
      throws DavException, IOException
  {
    Log.w(TAG, "setEncryptedKeyMaterial()");

    DavPropertySet updateProperties = new DavPropertySet();
    updateProperties.add(new DefaultDavProperty<String>(PROPERTY_ENCRYPTED_KEY_MATERIAL, encryptedKeyMaterial));

    patchProperties(updateProperties, new DavPropertyNameSet());
  }

  public static boolean weStartedMigration(Context context) {
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
    return settings.getBoolean(KEY_WE_STARTED_MIGRATION, false);
  }

  private static void setWeStartedMigration(Context context, boolean weStarted) {
    Log.w(TAG, "setWeStartedMigration() >> " + weStarted);

    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
    settings.edit().putBoolean(KEY_WE_STARTED_MIGRATION, weStarted).apply();
  }

  private static Calendar getMockCalendarForUid(String uid) {
    java.util.Calendar calendar = java.util.Calendar.getInstance();
    calendar.set(java.util.Calendar.MONTH, java.util.Calendar.JULY);
    calendar.set(java.util.Calendar.DAY_OF_MONTH, 24);

    net.fortuna.ical4j.model.Calendar putCalendar = new net.fortuna.ical4j.model.Calendar();
    putCalendar.getProperties().add(Version.VERSION_2_0);
    putCalendar.getProperties().add(CalScale.GREGORIAN);

    Date putStartDate = new Date(calendar.getTime());
    Date putEndDate   = new Date(putStartDate.getTime() + (1000 * 60 * 60 * 24));

    VEvent vEventPut = new VEvent(putStartDate, putEndDate, "Mock");
    vEventPut.getProperties().add(new Uid(uid));
    vEventPut.getProperties().add(new Description("Mock"));
    putCalendar.getComponents().add(vEventPut);

    return putCalendar;
  }

  public boolean setMigrationStarted(Context context)
      throws InvalidComponentException, DavException, IOException
  {
    Log.w(TAG, "setMigrationStarted()");

    CalDavCollection calDavCollection = new CalDavCollection((CalDavStore) getStore(), getPath());

    try {

      calDavCollection.addComponent(getMockCalendarForUid(UID_MIGRATION_STARTED));
      setWeStartedMigration(context, true);

    } catch (DavException e) {
      if (e.getErrorCode() == DavServletResponse.SC_PRECONDITION_FAILED)
        return false;

      throw e;
    }

    return true;
  }

  public boolean isMigrationStarted()
      throws InvalidComponentException, DavException, IOException
  {
    CalDavCollection calDavCollection = new CalDavCollection((CalDavStore) getStore(), getPath());
    return calDavCollection.getComponent(UID_MIGRATION_STARTED).isPresent();
  }

  public boolean isMigrationComplete()
      throws InvalidComponentException, DavException, IOException
  {
    CalDavCollection calDavCollection = new CalDavCollection((CalDavStore) getStore(), getPath());
    return calDavCollection.getComponent(UID_MIGRATION_COMPLETE).isPresent();
  }

  public void setMigrationComplete(Context context)
      throws InvalidComponentException, DavException, IOException
  {
    Log.w(TAG, "setMigrationComplete()");

    CalDavCollection calDavCollection = new CalDavCollection((CalDavStore) getStore(), getPath());

    try {

      calDavCollection.addComponent(getMockCalendarForUid(UID_MIGRATION_COMPLETE));
      setWeStartedMigration(context, false);

    } catch (DavException e) {
      if (e.getErrorCode() != DavServletResponse.SC_PRECONDITION_FAILED)
        throw e;
    }
  }

  @Override
  protected MultiStatusResult<Calendar> getComponentsFromMultiStatus(MultiStatusResponse[] msResponses) {
    return null;
  }

  @Override
  protected void putComponentToServer(Calendar calendar, Optional<String> ifMatchETag)
      throws DavException, IOException, InvalidComponentException
  {

  }

}
