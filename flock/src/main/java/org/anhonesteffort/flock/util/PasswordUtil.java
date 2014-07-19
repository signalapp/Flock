package org.anhonesteffort.flock.util;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ProgressBar;

import org.anhonesteffort.flock.R;

/**
 * Created by rhodey.
 */
public class PasswordUtil {

  // it works, ok?
  public static int getPasswordStrength(String password) {
    int passwordStrength = 1;

    passwordStrength += (password.length() / 3);

    if (!password.toLowerCase().equals(password))
      passwordStrength += 2;

    int integerCount = 0;
    for (int i = 0; i < password.length(); i++) {
      if (Character.isDigit(password.charAt(i)))
        integerCount++;
    }

    if (integerCount > 0 && integerCount != password.length())
      passwordStrength += (integerCount / 2);

    return passwordStrength;
  }

  public static void handleUpdateProgressWithPasswordStrength(Context     context,
                                                              String      password,
                                                              ProgressBar progressBar)
  {
    progressBar.setMax(10);

    if (password.length() == 0)
      progressBar.setVisibility(View.INVISIBLE);
    else
      progressBar.setVisibility(View.VISIBLE);

    int passwordStrength = PasswordUtil.getPasswordStrength(password);
    progressBar.setProgress(passwordStrength);

    if (passwordStrength > 6)
      progressBar.setProgressDrawable(context.getResources().getDrawable(R.drawable.flocktheme_progress_horizontal_holo_light_green));
    else if (passwordStrength > 3)
      progressBar.setProgressDrawable(context.getResources().getDrawable(R.drawable.flocktheme_progress_horizontal_holo_light_yellow));
    else
      progressBar.setProgressDrawable(context.getResources().getDrawable(R.drawable.flocktheme_progress_horizontal_holo_light_red));
  }

  public static TextWatcher getPasswordStrengthTextWatcher(final Context     context,
                                                           final ProgressBar progressBar)
  {
    return new TextWatcher() {

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) { }

      @Override
      public void afterTextChanged(Editable s) {
        PasswordUtil.handleUpdateProgressWithPasswordStrength(context, s.toString(), progressBar);
      }

    };
  }

}
