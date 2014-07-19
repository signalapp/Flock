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
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Optional;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.stripe.exception.StripeException;
import com.stripe.model.Receiver;
import de.passsy.holocircularprogressbar.HoloCircularProgressBar;

import org.anhonesteffort.flock.auth.DavAccount;
import org.anhonesteffort.flock.registration.OwsRegistration;
import org.anhonesteffort.flock.registration.model.FlockAccount;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Programmer: rhodey
 */
public class SendBitcoinActivity extends Activity {

  private static final String TAG = "org.anhonesteffort.flock.SendBitcoinActivity";

  public static final String KEY_DAV_ACCOUNT_BUNDLE = "KEY_DAV_ACCOUNT_BUNDLE";

  private final Handler   uiHandler     = new Handler();
  private       Timer     intervalTimer = new Timer();
  private       AsyncTask asyncTaskBtc;

  private DavAccount             davAccount;
  private Optional<Receiver>     btcReceiver  = Optional.absent();
  private Optional<Bitmap>       qrCodeBitmap = Optional.absent();
  private Optional<String>       lastBtcUri   = Optional.absent();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    requestWindowFeature(Window.FEATURE_PROGRESS);

    setContentView(R.layout.activity_send_bitcoin);
    getActionBar().setDisplayHomeAsUpEnabled(true);
    getActionBar().setTitle(R.string.title_send_bitcoin);

    if (savedInstanceState != null && !savedInstanceState.isEmpty()) {
      if (!DavAccount.build(savedInstanceState.getBundle(KEY_DAV_ACCOUNT_BUNDLE)).isPresent()) {
        finish();
        return;
      }

      davAccount = DavAccount.build(savedInstanceState.getBundle(KEY_DAV_ACCOUNT_BUNDLE)).get();
    }
    else if (getIntent().getExtras() != null) {
      if (!DavAccount.build(getIntent().getExtras().getBundle(KEY_DAV_ACCOUNT_BUNDLE)).isPresent()) {
        finish();
        return;
      }

      davAccount = DavAccount.build(getIntent().getExtras().getBundle(KEY_DAV_ACCOUNT_BUNDLE)).get();
    }

    initButtons();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        break;
    }

    return false;
  }

  @Override
  public void onResume() {
    super.onResume();

    handleStartPerpetualRefresh();
  }

  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    savedInstanceState.putBundle(KEY_DAV_ACCOUNT_BUNDLE, davAccount.toBundle());
    super.onSaveInstanceState(savedInstanceState);
  }

  @Override
  public void onPause() {
    super.onPause();

    if (asyncTaskBtc != null && !asyncTaskBtc.isCancelled())
      asyncTaskBtc.cancel(true);

    if (intervalTimer != null)
      intervalTimer.cancel();
  }

  private boolean handleLaunchBtcWallet(String btcURI) {
    Log.d(TAG, "handleLaunchBtcWallet()");

    Intent btcWalletIntent = new Intent(Intent.ACTION_VIEW);
    btcWalletIntent.setData(Uri.parse(btcURI));

    try {

      startActivity(btcWalletIntent);
      Log.d(TAG, "I think something was actually launched");

    } catch (ActivityNotFoundException e) {
      return false;
    }

    return true;
  }

  private void initButtons() {
    findViewById(R.id.button_cancel).setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        finish();
      }

    });

    findViewById(R.id.layout_btc_address).setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        if (!btcReceiver.isPresent())
          return false;

        if (event.getAction() == MotionEvent.ACTION_DOWN &&
            !handleLaunchBtcWallet(btcReceiver.get().getBitcoinUri()))
        {
          TextView addressBtcView = (TextView)findViewById(R.id.btc_address);
          View     triangleView   = findViewById(R.id.btc_address_triangle);

          addressBtcView.setBackgroundResource(R.drawable.rounded_thing_orange);
          triangleView.setBackgroundResource(R.drawable.conversation_item_received_triangle_shape_orange);

          ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
          ClipData         clip      = ClipData.newPlainText("oioi", btcReceiver.get().getInboundAddress());

          clipboard.setPrimaryClip(clip);
          Toast.makeText(getBaseContext(),
                         R.string.address_copied_to_clipboard,
                         Toast.LENGTH_SHORT).show();
        }

        else if (event.getAction() == MotionEvent.ACTION_UP) {
          TextView addressBtcView = (TextView) findViewById(R.id.btc_address);
          View     triangleView   = findViewById(R.id.btc_address_triangle);

          addressBtcView.setBackgroundResource(R.drawable.rounded_thing_grey);
          triangleView.setBackgroundResource(R.drawable.conversation_item_received_triangle_shape_grey);
        }

        return true;
      }
    });

    findViewById(R.id.layout_btc_address).setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        if (!btcReceiver.isPresent())
          return;

        TextView addressBtcView = (TextView)findViewById(R.id.btc_address);
        addressBtcView.setBackgroundResource(R.drawable.rounded_thing_orange);

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData         clip      = ClipData.newPlainText("oioi", btcReceiver.get().getInboundAddress());

        clipboard.setPrimaryClip(clip);
        Toast.makeText(getBaseContext(),
                       R.string.address_copied_to_clipboard,
                       Toast.LENGTH_SHORT).show();
      }

    });
  }

  private void handleUpdateUi() {
    ImageView               qrCodeView           = (ImageView)findViewById(R.id.image_btc_qr_code);
    TextView                microBtcLargeView    = (TextView)findViewById(R.id.mbtc_due_large);
    TextView                microBtcSmallView    = (TextView)findViewById(R.id.mbtc_due_small);
    TextView                addressBtcView       = (TextView)findViewById(R.id.btc_address);
    TextView                minutesRemainingView = (TextView)findViewById(R.id.btc_minutes_remaining);
    HoloCircularProgressBar progressBarView      = (HoloCircularProgressBar)findViewById(R.id.btc_address_progress);

    if (!btcReceiver.isPresent())
      return;

    if (!qrCodeBitmap.isPresent() ||
        (lastBtcUri.isPresent() &&
            !lastBtcUri.get().equals(btcReceiver.get().getBitcoinUri())))
    {
      try {

        qrCodeBitmap = encodeAsBitmap(btcReceiver.get().getBitcoinUri(),
                                      BarcodeFormat.AZTEC,
                                      qrCodeView.getWidth(),
                                      qrCodeView.getHeight());

      } catch (WriterException e) {
        Log.e(TAG, "caught exception while encoding BTC URI", e);
      }

      if (qrCodeBitmap.isPresent())
        qrCodeView.setImageBitmap(qrCodeBitmap.get());
    }
    else
      qrCodeView.setImageBitmap(qrCodeBitmap.get());

    lastBtcUri = Optional.of(btcReceiver.get().getBitcoinUri());

    Double amountMBtc          = btcReceiver.get().getBitcoinAmount() / 100000000.0 * 1000.0;
    int    decimal_index       = amountMBtc.toString().indexOf(".");
    String amountMBtcRemainder = null;

    if (decimal_index + 3 > amountMBtc.toString().length())
      amountMBtcRemainder = "0";
    else if ((amountMBtc.toString().length() - decimal_index) > 6)
      amountMBtcRemainder = amountMBtc.toString().substring(decimal_index + 3, decimal_index + 9);
    else
      amountMBtcRemainder = amountMBtc.toString().substring(decimal_index + 3);

    microBtcLargeView.setText(new DecimalFormat("0.00").format(amountMBtc));
    microBtcSmallView.setText(amountMBtcRemainder);

    String addressBtcFormatted = "";
    for(int i = 0; i < btcReceiver.get().getInboundAddress().length(); i++) {
      if (i % 4 == 0 && i != 0)
        addressBtcFormatted += " ";
      addressBtcFormatted += btcReceiver.get().getInboundAddress().charAt(i);
    }

    addressBtcView.setText(addressBtcFormatted);

    long    secondsRemaining = ((btcReceiver.get().getCreated() * 1000) - new Date().getTime()) / 1000 + ((10 * 60) - 1);
    Integer minutesRemaining = (int) (secondsRemaining / 60);

    if ((secondsRemaining - (minutesRemaining * 60)) < 0) {
      minutesRemainingView.setText("0:00");
      progressBarView.setProgress(1.0F);
    }
    else if ((secondsRemaining - (minutesRemaining * 60)) < 10) {
      minutesRemainingView.setText(minutesRemaining + ":0" + (secondsRemaining - (minutesRemaining * 60)));
      progressBarView.setProgress(1.0F - ((float) secondsRemaining / (10 * 60)));
    }
    else {
      minutesRemainingView.setText(minutesRemaining + ":" + (secondsRemaining - (minutesRemaining * 60)));
      progressBarView.setProgress(1.0F - ((float) secondsRemaining / (10 * 60)));
    }
  }

  private void handleRefreshBitcoinReceiver() {
    asyncTaskBtc = new AsyncTask<String, Void, Bundle>() {

      private Receiver receiver;

      @Override
      protected void onPreExecute() {
        Log.d(TAG, "handleRefreshBitcoinReceiver()");
        setProgressBarIndeterminateVisibility(true);
        setProgressBarVisibility(true);
      }

      private void handleExpireStoredBtcReceiver() {
        String            KEY_ID_BTC_RECEIVER = "KEY_ID_BTC_RECEIVER";
        SharedPreferences preferences         = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String            btcReceiverId       = preferences.getString(KEY_ID_BTC_RECEIVER, null);

        if (btcReceiverId != null)
          preferences.edit().remove(KEY_ID_BTC_RECEIVER).commit();
      }

      private Receiver handleCreateNewBtcReceiver(Double costUsd) throws StripeException {
        Map<String, Object> receiverParams = new HashMap<String, Object>();

        receiverParams.put("currency",    "usd");
        receiverParams.put("amount",      (int) (costUsd * 100));
        receiverParams.put("description", "Flock Subscription");
        receiverParams.put("email",       davAccount.getUserId());

        return Receiver.create(receiverParams, OwsRegistration.STRIPE_PUBLIC_KEY);
      }

      private Receiver getOrCreateNewBtcReceiver(Double costUsd) throws StripeException {
        Receiver          btcReceiver         = null;
        String            KEY_ID_BTC_RECEIVER = "KEY_ID_BTC_RECEIVER";
        SharedPreferences preferences         = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String            btcReceiverId       = preferences.getString(KEY_ID_BTC_RECEIVER, null);

        if (btcReceiverId != null) {
               btcReceiver = Receiver.retrieve(btcReceiverId, OwsRegistration.STRIPE_PUBLIC_KEY);
          Long timeCreated = btcReceiver.getCreated() * 1000;

          if ((timeCreated + (10 * 60 * 1000)) < new Date().getTime()) {
            Log.d(TAG, "btc receiver expired, creating new");
            btcReceiverId = null;
          }
        }

        if (btcReceiverId == null) {
          btcReceiver = handleCreateNewBtcReceiver(costUsd);
          preferences.edit().putString(KEY_ID_BTC_RECEIVER, btcReceiver.getId()).commit();
          return btcReceiver;
        }

        return Receiver.retrieve(btcReceiverId, OwsRegistration.STRIPE_PUBLIC_KEY);
      }

      @Override
      protected Bundle doInBackground(String... params) {
        Bundle result         = new Bundle();
        Double costPerYearUsd = (double) getResources().getInteger(R.integer.cost_per_year_usd);

        try {

          receiver = getOrCreateNewBtcReceiver(costPerYearUsd);

        } catch (StripeException e) {
          Log.e(TAG, "stripe is mad", e);
          ErrorToaster.handleBundleError(e, result);
        }

        return result;
      }

      @Override
      protected void onPostExecute(Bundle result) {
        Log.d(TAG, "STATUS: " + result.getInt(ErrorToaster.KEY_STATUS_CODE));

        asyncTaskBtc = null;
        setProgressBarIndeterminateVisibility(false);
        setProgressBarVisibility(false);

        if (receiver != null) {
          btcReceiver = Optional.of(receiver);
          handleUpdateUi();

          if (receiver.getFilled()) {
            handleExpireStoredBtcReceiver();
            Toast.makeText(getBaseContext(),
                           R.string.bitcoin_received,
                           Toast.LENGTH_SHORT).show();
            finish();
          }
        }
        else
          ErrorToaster.handleDisplayToastBundledError(getBaseContext(), result);
      }
    }.execute();
  }

  private final Runnable refreshUiRunnable = new Runnable() {

    @Override
    public void run() {
      handleUpdateUi();
    }

  };
  private final Runnable refreshBtcRunnable = new Runnable() {

    @Override
    public void run() {
      if (asyncTaskBtc == null || asyncTaskBtc.isCancelled())
        handleRefreshBitcoinReceiver();
    }

  };

  private void handleStartPerpetualRefresh() {
              intervalTimer = new Timer();
    TimerTask uiTask        = new TimerTask() {

      @Override
      public void run() {
        uiHandler.post(refreshUiRunnable);
      }

    };
    TimerTask btcTask = new TimerTask() {

      @Override
      public void run() {
        uiHandler.post(refreshBtcRunnable);
      }

    };

    intervalTimer.schedule(uiTask,  0, 1000);
    intervalTimer.schedule(btcTask, 0, 10000);
  }

  Optional<Bitmap> encodeAsBitmap(String        dataToEncode,
                                  BarcodeFormat format,
                                  int           bitmap_width,
                                  int           bitmap_height)
      throws WriterException
  {
    Map<EncodeHintType, Object> hints = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
    hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

    MultiFormatWriter writer = new MultiFormatWriter();
    BitMatrix         result;

    try {

      result = writer.encode(dataToEncode, format, bitmap_width, bitmap_height, hints);

    } catch (IllegalArgumentException e) {
      Log.e(TAG, "caught exception while attempting to encode " +
                 dataToEncode + " as bitmap", e);
      return Optional.absent();
    }


    int[] pixel_array = new int[result.getWidth() * result.getHeight()];
    for (int y = 0; y < result.getHeight(); y++) {
      for (int x = 0; x < result.getWidth(); x++)
        pixel_array[(y * result.getWidth()) + x] = result.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
    }

    Bitmap bitmap = Bitmap.createBitmap(result.getWidth(), result.getHeight(), Bitmap.Config.ARGB_8888);
    bitmap.setPixels(pixel_array, 0, result.getWidth(), 0, 0, result.getWidth(), result.getHeight());

    return Optional.of(bitmap);
  }

}
