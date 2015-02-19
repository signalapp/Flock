package org.anhonesteffort.flock.sync.key;

import android.util.Log;

import net.fortuna.ical4j.model.Calendar;

import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.sync.OwsWebDav;
import org.anhonesteffort.flock.webdav.AbstractDavComponentCollection;
import org.anhonesteffort.flock.webdav.InvalidComponentException;
import org.anhonesteffort.flock.webdav.MultiStatusResult;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.anhonesteffort.flock.webdav.caldav.CalDavCollection;
import org.anhonesteffort.flock.webdav.caldav.CalDavConstants;
import org.anhonesteffort.flock.webdav.caldav.CalDavStore;
import org.apache.jackrabbit.webdav.DavException;
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
