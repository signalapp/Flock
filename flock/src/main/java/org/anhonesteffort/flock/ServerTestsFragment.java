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

package org.anhonesteffort.flock;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import ezvcard.VCard;
import ezvcard.VCardVersion;
import ezvcard.property.StructuredName;
import ezvcard.property.Uid;
import net.fortuna.ical4j.model.ConstraintViolationException;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.util.Calendars;
import org.anhonesteffort.flock.util.guava.Optional;
import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.sync.OwsWebDav;
import org.anhonesteffort.flock.webdav.ComponentETagPair;
import org.anhonesteffort.flock.webdav.InvalidComponentException;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.anhonesteffort.flock.webdav.WebDavConstants;
import org.anhonesteffort.flock.webdav.caldav.CalDavCollection;
import org.anhonesteffort.flock.webdav.caldav.CalDavStore;
import org.anhonesteffort.flock.webdav.carddav.CardDavCollection;
import org.anhonesteffort.flock.webdav.carddav.CardDavStore;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import javax.net.ssl.SSLException;

/**
 * Programmer: rhodey
 */
public class ServerTestsFragment extends Fragment {

  private static final String TAG = "org.anhonesteffort.flock.ServerTestsFragment";

  private static final String KEY_HREF_DAV_HOST = "KEY_HREF_DAV_HOST";
  private static final String KEY_DAV_USERNAME  = "KEY_DAV_USERNAME";

  private static final int CODE_ERROR_CARDDAV_CURRENT_USER_PRINCIPAL           = 100;
  private static final int CODE_ERROR_CALDAV_CURRENT_USER_PRINCIPAL            = 101;
  private static final int CODE_ERROR_CALDAV_CALENDAR_HOMESET                  = 102;
  private static final int CODE_ERROR_CARDDAV_ADDRESSBOOK_HOMESET              = 103;
  private static final int CODE_ERROR_CALDAV_CREATE_DELETE_COLLECTION          = 104;
  private static final int CODE_ERROR_CALDAV_CREATE_EDIT_COLLECTION_PROPERTIES = 105;
  private static final int CODE_ERROR_CARDDAV_CREATE_DELETE_CONTACTS           = 106;
  private static final int CODE_ERROR_CALDAV_CREATE_DELETE_EVENTS              = 107;

  private SetupActivity setupActivity;
  private AsyncTask     asyncTask;

  private Optional<String> hrefDavHost = Optional.absent();
  private Optional<String> davUsername = Optional.absent();

  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    if (savedInstanceState != null) {
      hrefDavHost = Optional.fromNullable(savedInstanceState.getString(KEY_HREF_DAV_HOST));
      davUsername = Optional.fromNullable(savedInstanceState.getString(KEY_DAV_USERNAME));
    }
  }

  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    super.onSaveInstanceState(savedInstanceState);

    TextView davHostView     = (TextView)getView().findViewById(R.id.href_webdav_host);
    TextView davUsernameView = (TextView)getView().findViewById(R.id.account_username);


    if (davHostView.getText() != null)
      savedInstanceState.putString(KEY_HREF_DAV_HOST, davHostView.getText().toString());

    if (davUsernameView.getText() != null)
      savedInstanceState.putString(KEY_DAV_USERNAME, davUsernameView.getText().toString());
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);

    if (activity instanceof SetupActivity)
      this.setupActivity = (SetupActivity) activity;
    else
      throw new ClassCastException(activity.toString() + " not what I expected D: !");
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup      container,
                           Bundle         savedInstanceState)
  {
    View view = inflater.inflate(R.layout.fragment_server_tests, container, false);

    initButtons();
    initForm();

    return view;
  }

  @Override
  public void onPause() {
    super.onPause();

    if (asyncTask != null && !asyncTask.isCancelled())
      asyncTask.cancel(true);
  }

  private void initButtons() {
    getActivity().findViewById(R.id.button_next).setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        handleStartTests();
      }

    });
  }

  private void initForm() {
    TextView davHostView     = (TextView)getActivity().findViewById(R.id.href_webdav_host);
    TextView davUsernameView = (TextView)getActivity().findViewById(R.id.account_username);

    if (hrefDavHost.isPresent())
      davHostView.setText(hrefDavHost.get());

    if (davUsername.isPresent())
      davUsernameView.setText(davUsername.get());
  }

  private void handleTestsSucceeded() {
    Log.d(TAG, "handleTestsSucceeded()");

    String webDavHost = ((TextView)getView().findViewById(R.id.href_webdav_host)).getText().toString().trim();
    String username   = ((TextView)getView().findViewById(R.id.account_username)).getText().toString().trim();

    Toast.makeText(getActivity(),
                   R.string.tests_completed_successfully,
                   Toast.LENGTH_SHORT).show();

    if (StringUtils.isNotEmpty(webDavHost) && StringUtils.isNotEmpty(username))
      setupActivity.setDavTestOptions(webDavHost, username);

    setupActivity.updateFragmentUsingState(SetupActivity.STATE_CONFIGURE_SERVICE_PROVIDER);
  }

  private void handleStartTests() {
    Log.d(TAG, "handleStartTests()");

    if (asyncTask == null || asyncTask.isCancelled()) {
      ((Button) getActivity().findViewById(R.id.button_next)).setText(R.string.stop_tests);
      startTests();
    }
    else {
      ((Button) getActivity().findViewById(R.id.button_next)).setText(R.string.restart_tests);
      asyncTask.cancel(true);
      asyncTask = null;
    }
  }

  private void startTests() {

    asyncTask = new AsyncTask<String, Void, Bundle>() {

      private TextView    currentTest;
      private ImageView   testErrorImage;
      private ProgressBar progressBar;
      private int         progress = 0;

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "startTests()");

        currentTest          = (TextView)    getView().findViewById(R.id.text_current_test);
        testErrorImage       = (ImageView)   getView().findViewById(R.id.image_current_test_failed);
        progressBar          = (ProgressBar) getView().findViewById(R.id.progress_server_tests);

        currentTest.setText(R.string.tests_not_yet_started);
        testErrorImage.setVisibility(View.GONE);

        progressBar.setMax(9);
        progressBar.setProgress(0);
      }

      private void handleCardDavTestCurrentUserPrincipal(Bundle result, DavAccount testAccount) {
        try {

          CardDavStore     cardDavStore         = DavAccountHelper.getCardDavStore(getActivity(), testAccount);
          Optional<String> currentUserPrincipal = cardDavStore.getCurrentUserPrincipal();

          if (currentUserPrincipal.isPresent())
            result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);
          else
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CARDDAV_CURRENT_USER_PRINCIPAL);

        } catch (DavException e) {
          Log.e(TAG, "carddav current user principal", e);

          if (e.getErrorCode() == WebDavConstants.SC_UNAUTHORIZED)
            result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_UNAUTHORIZED);
          else
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CARDDAV_CURRENT_USER_PRINCIPAL);

        } catch (SSLException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (IOException e) {
          ErrorToaster.handleBundleError(e, result);
        }
      }

      private void handleCalDavTestCurrentUserPrincipal(Bundle result, DavAccount testAccount) {
        try {

          CalDavStore      calDavStore          = DavAccountHelper.getCalDavStore(getActivity(), testAccount);
          Optional<String> currentUserPrincipal = calDavStore.getCurrentUserPrincipal();

          if (currentUserPrincipal.isPresent())
            result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);
          else
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CURRENT_USER_PRINCIPAL);

        } catch (DavException e) {
          Log.e(TAG, "calddav current user principal", e);

          if (e.getErrorCode() == WebDavConstants.SC_UNAUTHORIZED)
            result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_UNAUTHORIZED);
          else
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CURRENT_USER_PRINCIPAL);

        } catch (SSLException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (IOException e) {
          ErrorToaster.handleBundleError(e, result);
        }
      }

      private void handleCardDavTestAddressbookHomeset(Bundle result, DavAccount testAccount) {
        try {

          CardDavStore     cardDavStore       = DavAccountHelper.getCardDavStore(getActivity(), testAccount);
          Optional<String> addressbookHomeset = cardDavStore.getAddressbookHomeSet();

          if (addressbookHomeset.isPresent())
            result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);
          else
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CARDDAV_ADDRESSBOOK_HOMESET);

        } catch (DavException e) {
          Log.e(TAG, "carddav addressbook homeset", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CARDDAV_ADDRESSBOOK_HOMESET);
        } catch (PropertyParseException e) {
          Log.e(TAG, "carddav addressbook homeset", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CARDDAV_ADDRESSBOOK_HOMESET);
        } catch (SSLException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (IOException e) {
          ErrorToaster.handleBundleError(e, result);
        }
      }

      private void handleCalDavTestCalendarHomeset(Bundle result, DavAccount testAccount) {
        try {

          CalDavStore      calDavStore     = DavAccountHelper.getCalDavStore(getActivity(), testAccount);
          Optional<String> calendarHomeset = calDavStore.getCalendarHomeSet();

          if (calendarHomeset.isPresent())
            result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);
          else
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CALENDAR_HOMESET);

        } catch (DavException e) {
          Log.e(TAG, "caldav calendar homeset", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CALENDAR_HOMESET);
        } catch (PropertyParseException e) {
          Log.e(TAG, "caldav calendar homeset", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CALENDAR_HOMESET);
        } catch (SSLException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (IOException e) {
          ErrorToaster.handleBundleError(e, result);
        }
      }

      private void handleCalDavTestCreateDeleteCollection(Bundle result, DavAccount testAccount) {
        try {

          CalDavStore      calDavStore     = DavAccountHelper.getCalDavStore(getActivity(), testAccount);
          Optional<String> calendarHomeset = calDavStore.getCalendarHomeSet();

          if (!calendarHomeset.isPresent()) {
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CALENDAR_HOMESET);
            return;
          }

          String tempCollectionPath = calendarHomeset.get().concat("delete-me/");

          calDavStore.addCollection(tempCollectionPath);
          Optional<CalDavCollection> tempCollection = calDavStore.getCollection(tempCollectionPath);

          if (!tempCollection.isPresent()) {
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CREATE_DELETE_COLLECTION);
            return;
          }

          calDavStore.removeCollection(tempCollectionPath);
          tempCollection = calDavStore.getCollection(tempCollectionPath);

          if (tempCollection.isPresent())
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CREATE_DELETE_COLLECTION);
          else
            result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (DavException e) {
          Log.e(TAG, "caldav create delete collection", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CREATE_DELETE_COLLECTION);
        } catch (PropertyParseException e) {
          Log.e(TAG, "caldav create delete collection", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CREATE_DELETE_COLLECTION);
        } catch (SSLException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (IOException e) {
          ErrorToaster.handleBundleError(e, result);
        }
      }

      private void handleCalDavTestCreateEditCollectionProperties(Bundle     result,
                                                                  DavAccount testAccount)
      {
        final String TEMP_DISPLAY_NAME = "TEMP DISPLAY NAME";

        try {

          CalDavStore      calDavStore     = DavAccountHelper.getCalDavStore(getActivity(), testAccount);
          Optional<String> calendarHomeset = calDavStore.getCalendarHomeSet();

          if (!calendarHomeset.isPresent()) {
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CALENDAR_HOMESET);
            return;
          }

          String tempCollectionPath = calendarHomeset.get().concat("delete-me/");

          calDavStore.addCollection(tempCollectionPath);
          Optional<CalDavCollection> tempCollection = calDavStore.getCollection(tempCollectionPath);

          if (!tempCollection.isPresent()) {
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CREATE_DELETE_COLLECTION);
            return;
          }

          tempCollection.get().setDisplayName(TEMP_DISPLAY_NAME);

          if (!tempCollection.get().getDisplayName().isPresent() ||
              !tempCollection.get().getDisplayName().get().equals(TEMP_DISPLAY_NAME))
          {
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CREATE_EDIT_COLLECTION_PROPERTIES);
            return;
          }

          final DavPropertyName TEST_PROP_NAME  = DavPropertyName.create("X-TEST-XPROPERTIES", OwsWebDav.NAMESPACE);
          final String          TEST_PROP_VALUE = "TEST PROPERTY VALUE";

          DavPropertySet     setProps       = new DavPropertySet();
          DavPropertyNameSet fetchPropNames = new DavPropertyNameSet();

          setProps.add(new DefaultDavProperty<String>(TEST_PROP_NAME, TEST_PROP_VALUE));
          fetchPropNames.add(TEST_PROP_NAME);

          tempCollection.get().patchProperties(setProps, new DavPropertyNameSet());
          tempCollection.get().fetchProperties(fetchPropNames);

          Optional<String> gotTestProp = tempCollection.get().getProperty(TEST_PROP_NAME, String.class);

          if (!gotTestProp.isPresent() || !gotTestProp.get().equals(TEST_PROP_VALUE))  {
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CREATE_EDIT_COLLECTION_PROPERTIES);
            return;
          }

          calDavStore.removeCollection(tempCollectionPath);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (DavException e) {
          Log.e(TAG, "caldav create edit collection properties", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CREATE_EDIT_COLLECTION_PROPERTIES);
        } catch (PropertyParseException e) {
          Log.e(TAG, "caldav create edit collection properties", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CREATE_EDIT_COLLECTION_PROPERTIES);
        } catch (SSLException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (IOException e) {
          ErrorToaster.handleBundleError(e, result);
        }
      }

      private void handleCardDavTestCreateDeleteContacts(Bundle     result,
                                                         DavAccount testAccount)
      {
        try {

          CardDavStore            cardDavStore = DavAccountHelper.getCardDavStore(getActivity(), testAccount);
          List<CardDavCollection> collections  = cardDavStore.getCollections();

          if (collections.size() == 0) {
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CARDDAV_CREATE_DELETE_CONTACTS);
            return;
          }

          CardDavCollection testCollection = collections.get(0);

          final StructuredName structuredName = new StructuredName();
          structuredName.setFamily("Strangelove");
          structuredName.setGiven("?");
          structuredName.addPrefix("Dr");
          structuredName.addSuffix("");

          VCard putVCard = new VCard();
          putVCard.setVersion(VCardVersion.V3_0);
          putVCard.setUid(new Uid(UUID.randomUUID().toString()));
          putVCard.setStructuredName(structuredName);
          putVCard.setFormattedName("you need this too");

          final String EXTENDED_PROPERTY_NAME  = "X-EXTENDED-PROPERTY-NAME";
          final String EXTENDED_PROPERTY_VALUE = "THIS IS A LINE LONG ENOUGH TO BE SPLIT IN TWO BY THE VCARD FOLDING NONSENSE WHY DOES THIS EXIST?!?!??!?!?!?!?!??";
          putVCard.setExtendedProperty(EXTENDED_PROPERTY_NAME, EXTENDED_PROPERTY_VALUE);

          testCollection.addComponent(putVCard);

          Optional<ComponentETagPair<VCard>> gotVCard = testCollection.getComponent(putVCard.getUid().getValue());

          if (!gotVCard.isPresent()) {
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CARDDAV_CREATE_DELETE_CONTACTS);
            return;
          }

          if (!gotVCard.get().getComponent().getStructuredName().getFamily().equals(structuredName.getFamily())) {
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CARDDAV_CREATE_DELETE_CONTACTS);
            return;
          }

          if (gotVCard.get().getComponent().getExtendedProperty(EXTENDED_PROPERTY_NAME) == null ||
              !gotVCard.get().getComponent().getExtendedProperty(EXTENDED_PROPERTY_NAME).getValue().equals(EXTENDED_PROPERTY_VALUE))
          {
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CARDDAV_CREATE_DELETE_CONTACTS);
            return;
          }


          testCollection.removeComponent(putVCard.getUid().getValue());

          if (testCollection.getComponent(putVCard.getUid().getValue()).isPresent())
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CARDDAV_CREATE_DELETE_CONTACTS);
          else
            result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (DavException e) {
          Log.e(TAG, "carddav create delete contacts", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CARDDAV_CREATE_DELETE_CONTACTS);
        } catch (PropertyParseException e) {
          Log.e(TAG, "carddav create delete contacts", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CARDDAV_CREATE_DELETE_CONTACTS);
        } catch (InvalidComponentException e) {
          Log.e(TAG, "carddav create delete contacts", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CARDDAV_CREATE_DELETE_CONTACTS);
        } catch (SSLException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (IOException e) {
          ErrorToaster.handleBundleError(e, result);
        }
      }

      private void handleCalDavTestCreateDeleteEvents(Bundle result, DavAccount testAccount) {
        try {

          CalDavStore      calDavStore     = DavAccountHelper.getCalDavStore(getActivity(), testAccount);
          Optional<String> calendarHomeset = calDavStore.getCalendarHomeSet();

          if (!calendarHomeset.isPresent()) {
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CALENDAR_HOMESET);
            return;
          }

          String tempCollectionPath = calendarHomeset.get().concat("delete-me/");

          calDavStore.addCollection(tempCollectionPath);
          Optional<CalDavCollection> tempCollection = calDavStore.getCollection(tempCollectionPath);

          if (!tempCollection.isPresent()) {
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CREATE_DELETE_COLLECTION);
            return;
          }

          Calendar calendar = Calendar.getInstance();
          calendar.set(Calendar.MONTH, Calendar.JUNE);
          calendar.set(Calendar.DAY_OF_MONTH, 5);

          net.fortuna.ical4j.model.Calendar putCalendar = new net.fortuna.ical4j.model.Calendar();
          putCalendar.getProperties().add(Version.VERSION_2_0);
          putCalendar.getProperties().add(CalScale.GREGORIAN);

          Date putStartDate = new Date(calendar.getTime());
          Date putEndDate   = new Date(putStartDate.getTime() + (1000 * 60 * 60 * 24));

          VEvent vEventPut = new VEvent(putStartDate, putEndDate, "Delete Me!");
          vEventPut.getProperties().add(new net.fortuna.ical4j.model.property.Uid(UUID.randomUUID().toString()));
          vEventPut.getProperties().add(new Description("THIS IS A LINE LONG ENOUGH TO BE SPLIT IN TWO BY THE ICAL FOLDING NONSENSE WHY DOES THIS EXIST?!?!??!?!?!?!?!??"));
          putCalendar.getComponents().add(vEventPut);

          tempCollection.get().addComponent(putCalendar);

          Optional<ComponentETagPair<net.fortuna.ical4j.model.Calendar>> gotCalendar =
              tempCollection.get().getComponent(Calendars.getUid(putCalendar).getValue());

          if (!gotCalendar.isPresent()) {
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CREATE_DELETE_EVENTS);
            return;
          }

          VEvent vEventGot = (VEvent) gotCalendar.get().getComponent().getComponent(VEvent.VEVENT);

          if (vEventGot == null ||
              !vEventGot.getSummary().getValue().equals(vEventPut.getSummary().getValue()))
          {
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CREATE_DELETE_EVENTS);
            return;
          }

          tempCollection.get().removeComponent(Calendars.getUid(putCalendar).getValue());

          gotCalendar = tempCollection.get().getComponent(Calendars.getUid(putCalendar).getValue());

          if (gotCalendar.isPresent()) {
            result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CREATE_DELETE_EVENTS);
            return;
          }

          calDavStore.removeCollection(tempCollectionPath);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SUCCESS);

        } catch (DavException e) {
          Log.e(TAG, "caldav create delete events", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CREATE_DELETE_EVENTS);
        } catch (PropertyParseException e) {
          Log.e(TAG, "caldav create delete events", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CREATE_DELETE_EVENTS);
        } catch (InvalidComponentException e) {
          Log.e(TAG, "caldav create delete events", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CREATE_DELETE_EVENTS);
        } catch (ConstraintViolationException e) {
          Log.e(TAG, "caldav create delete events", e);
          result.putInt(ErrorToaster.KEY_STATUS_CODE, CODE_ERROR_CALDAV_CREATE_DELETE_EVENTS);
        } catch (SSLException e) {
          ErrorToaster.handleBundleError(e, result);
        } catch (IOException e) {
          ErrorToaster.handleBundleError(e, result);
        }
      }

      @Override
      protected Bundle doInBackground(String... params) {
        String webDavHost = ((TextView)getView().findViewById(R.id.href_webdav_host)).getText().toString().trim();
        String username   = ((TextView)getView().findViewById(R.id.account_username)).getText().toString().trim();
        String password   = ((TextView)getView().findViewById(R.id.account_password)).getText().toString().trim();
        Bundle result     = new Bundle();

        if (StringUtils.isEmpty(username)) {
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_EMPTY_ACCOUNT_ID);
          return result;
        }

        if (StringUtils.isEmpty(password)) {
          result.putInt(ErrorToaster.KEY_STATUS_CODE, ErrorToaster.CODE_SHORT_PASSWORD);
          return result;
        }

        DavAccount testAccount = new DavAccount(username, password, webDavHost);

        progress++;
        publishProgress();
        handleCardDavTestCurrentUserPrincipal(result, testAccount);

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {
          progress++;
          publishProgress();
          handleCalDavTestCurrentUserPrincipal(result, testAccount);
        }

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {
          progress++;
          publishProgress();
          handleCardDavTestAddressbookHomeset(result, testAccount);
        }

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {
          progress++;
          publishProgress();
          handleCalDavTestCalendarHomeset(result, testAccount);
        }

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {
          progress++;
          publishProgress();
          handleCalDavTestCreateDeleteCollection(result, testAccount);
        }

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {
          progress++;
          publishProgress();
          handleCalDavTestCreateEditCollectionProperties(result, testAccount);
        }

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {
          progress++;
          publishProgress();
          handleCardDavTestCreateDeleteContacts(result, testAccount);
        }

        if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {
          progress++;
          publishProgress();
          handleCalDavTestCreateDeleteEvents(result, testAccount);

          if (result.getInt(ErrorToaster.KEY_STATUS_CODE) == ErrorToaster.CODE_SUCCESS) {
            progress++;
            publishProgress();
          }
        }

        return result;
      }

      @Override
      protected void onProgressUpdate(final Void... values) {
        progressBar.setProgress(progress);

        switch (progress) {

          case 0:
            currentTest.setText(R.string.tests_not_yet_started);
            break;

          case 1:
            currentTest.setText(R.string.test_dav_current_user_principal);
            break;

          case 2:
            currentTest.setText(R.string.test_dav_current_user_principal);
            break;

          case 3:
            currentTest.setText(R.string.test_carddav_addressbook_homeset);
            break;

          case 4:
            currentTest.setText(R.string.test_caldav_calendar_homeset);
            break;

          case 5:
            currentTest.setText(R.string.test_caldav_create_and_delete_collections);
            break;

          case 6:
            currentTest.setText(R.string.test_caldav_create_and_edit_collection_properties);
            break;

          case 7:
            currentTest.setText(R.string.test_carddav_create_and_delete_contacts);
            break;

          case 8:
            currentTest.setText(R.string.test_caldav_create_and_delete_events);
            break;

        }
      }

      @Override
      protected void onCancelled() {
        currentTest.setText(R.string.tests_interrupted);
        progressBar.setProgress(0);
      }

      @Override
      protected void onPostExecute(Bundle result) {
        Log.d(TAG, "STATUS: " + result.getInt(ErrorToaster.KEY_STATUS_CODE));

        asyncTask = null;
        testErrorImage.setVisibility(View.VISIBLE);
        ((Button)getActivity().findViewById(R.id.button_next)).setText(R.string.restart_tests);

        switch (result.getInt(ErrorToaster.KEY_STATUS_CODE)) {

          case ErrorToaster.CODE_SUCCESS:
            testErrorImage.setVisibility(View.GONE);
            handleTestsSucceeded();
            break;

          case CODE_ERROR_CARDDAV_CURRENT_USER_PRINCIPAL:

            Toast.makeText(getActivity(),
                           R.string.test_error_carddav_current_user_principal,
                           Toast.LENGTH_LONG).show();
            break;

          case CODE_ERROR_CALDAV_CURRENT_USER_PRINCIPAL:
            Toast.makeText(getActivity(),
                           R.string.test_error_carddav_current_user_principal,
                           Toast.LENGTH_LONG).show();
            break;

          case CODE_ERROR_CARDDAV_ADDRESSBOOK_HOMESET:
            Toast.makeText(getActivity(),
                           R.string.test_error_carddav_addressbook_homeset,
                           Toast.LENGTH_LONG).show();
            break;

          case CODE_ERROR_CALDAV_CALENDAR_HOMESET:
            Toast.makeText(getActivity(),
                           R.string.test_error_caldav_calendar_homeset,
                           Toast.LENGTH_LONG).show();
            break;

          case CODE_ERROR_CALDAV_CREATE_DELETE_COLLECTION:
            Toast.makeText(getActivity(),
                           R.string.test_error_caldav_create_and_delete_collections,
                           Toast.LENGTH_LONG).show();
            break;

          case CODE_ERROR_CALDAV_CREATE_EDIT_COLLECTION_PROPERTIES:
            Toast.makeText(getActivity(),
                           R.string.test_error_caldav_create_and_edit_collection_properties,
                           Toast.LENGTH_LONG).show();
            break;

          case CODE_ERROR_CARDDAV_CREATE_DELETE_CONTACTS:
            Toast.makeText(getActivity(),
                           R.string.test_error_carddav_create_and_delete_contacts,
                           Toast.LENGTH_LONG).show();
            break;

          case CODE_ERROR_CALDAV_CREATE_DELETE_EVENTS:
            Toast.makeText(getActivity(),
                           R.string.test_error_caldav_create_and_delete_events,
                           Toast.LENGTH_LONG).show();
            break;

          default:
            ErrorToaster.handleDisplayToastBundledError(getActivity(), result);

        }

        ((TextView)getView().findViewById(R.id.account_password)).setText("");
      }
    }.execute();
  }

}
