package org.anhonesteffort.flock.sync.key;

import android.content.Context;
import android.util.Log;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.DavAccountHelper;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.webdav.DavClient;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.anhonesteffort.flock.webdav.caldav.CalDavCollection;
import org.anhonesteffort.flock.webdav.caldav.CalDavConstants;
import org.anhonesteffort.flock.webdav.caldav.CalDavStore;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;

import java.io.IOException;
import java.util.List;

/**
 * rhodey
 */
public class DavKeyStore extends CalDavStore {

  private static final String TAG                 = "org.anhonesteffort.flock.sync.key.DavKeyStore";
  public  static final String PATH_KEY_COLLECTION = "key-material/";

  public DavKeyStore(DavClient        client,
                     Optional<String> currentUserPrincipal,
                     Optional<String> calendarHomeSet)
  {
    super(client, currentUserPrincipal, calendarHomeSet);
  }

  public static void createCollection(Context    context,
                                      DavAccount account)
      throws PropertyParseException, DavException, IOException
  {
    Log.w(TAG, "creating key collection");
    DavKeyStore store = DavAccountHelper.getDavKeyStore(context, account);

    try {

      Optional<String> calendarHomeSet = store.getCalendarHomeSet();
      if (!calendarHomeSet.isPresent())
        throw new PropertyParseException("No calendar-home-set property found for user.",
                                         store.getHostHREF(), CalDavConstants.PROPERTY_NAME_CALENDAR_HOME_SET);

      store.addCollection(calendarHomeSet.get().concat(PATH_KEY_COLLECTION));

    } finally {
      store.closeHttpConnection();
    }
  }

  public Optional<DavKeyCollection> getCollection()
      throws PropertyParseException, DavException, IOException
  {
    Optional<String>  calendarHomeSet = getCalendarHomeSet();
    if (!calendarHomeSet.isPresent())
      throw new PropertyParseException("No calendar-home-set property found for user.",
                                        getHostHREF(), CalDavConstants.PROPERTY_NAME_CALENDAR_HOME_SET);

    String             collectionPath   = calendarHomeSet.get().concat(PATH_KEY_COLLECTION);
    DavKeyCollection   targetCollection = new DavKeyCollection(this, collectionPath);
    DavPropertyNameSet collectionProps  = targetCollection.getPropertyNamesForFetch();
    PropFindMethod     propFindMethod   = new PropFindMethod(collectionPath, collectionProps, PropFindMethod.DEPTH_0);

    try {

      getClient().execute(propFindMethod);

      MultiStatus           multiStatus = propFindMethod.getResponseBodyAsMultiStatus();
      MultiStatusResponse[] responses   = multiStatus.getResponses();

      List<CalDavCollection> returnedCollections = getCollectionsFromMultiStatusResponses(this, responses);

      if (returnedCollections.size() == 0)
        Optional.absent();

      return Optional.of(
          new DavKeyCollection(returnedCollections.get(0),
                               returnedCollections.get(0).getPath())
      );

    } catch (DavException e) {
      if (e.getErrorCode() == DavServletResponse.SC_NOT_FOUND)
        return Optional.absent();

      throw e;
    } finally {
      propFindMethod.releaseConnection();
    }
  }
}