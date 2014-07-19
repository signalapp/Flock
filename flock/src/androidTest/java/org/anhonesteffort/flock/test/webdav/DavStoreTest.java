package org.anhonesteffort.flock.test.webdav;

import android.test.AndroidTestCase;
import android.util.Log;

import com.google.common.base.Optional;
import org.anhonesteffort.flock.webdav.caldav.CalDavStore;

import java.util.List;


/**
 * Programmer: rhodey
 * Date: 2/4/14
 */
public class DavStoreTest extends AndroidTestCase {

  private static final String TAG = "DavStoreTest";

  private CalDavStore davStore;

  @Override
  protected void setUp() throws Exception {
    davStore = new CalDavStore(DavTestParams.WEBDAV_HOST,
                               DavTestParams.USERNAME,
                               DavTestParams.PASSWORD,
                               Optional.<String>absent(),
                               Optional.<String>absent());
  }

  public void testDavOptions() throws Exception {
    List<String> davOptions = davStore.getDavOptions();
    assertTrue("DAVOptions should be something.", davOptions.size() > 0);
    Log.d(TAG, "DAV Options: " + davOptions);
  }

}
