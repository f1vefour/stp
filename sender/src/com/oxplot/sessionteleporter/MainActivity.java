package com.oxplot.sessionteleporter;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

//TODO stop the webview from reloading the page on orientation changes
//TODO make the back button work with webview

public class MainActivity extends Activity {

  private static final Pattern DOMAIN_PAT = Pattern
      .compile("(https?://)([^/]+)");

  private WebView browser;
  private EditText addressBar;
  private TeleportTask teleportTask;

  @SuppressLint("SetJavaScriptEnabled")
  @Override
  protected void onCreate(Bundle savedInstanceState) {

    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    browser = (WebView) findViewById(R.id.browser);
    addressBar = (EditText) findViewById(R.id.addressBar);

    browser.getSettings().setJavaScriptEnabled(true);
    browser.getSettings().setLoadWithOverviewMode(true);
    browser.getSettings().setUseWideViewPort(true);
    browser.getSettings().setBuiltInZoomControls(true);
    browser.getSettings().setUserAgentString(
        getString(R.string.config_user_agent));
    browser.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url) {
        navigate(url);
        return true;
      }
    });

    addressBar.setOnEditorActionListener(new OnEditorActionListener() {
      @Override
      public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_GO) {
          MainActivity.this.navigate(addressBar.getText().toString());
          browser.requestFocus();
          // XXX we shouldn't need to do this hackery just to get rid of the
          // keyboard
          InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
          imm.hideSoftInputFromWindow(addressBar.getWindowToken(), 0);
          return true;
        }
        return false;
      }
    });

  }

  private void navigate(String url) {
    // Some Chrome inspired prettifying activities here
    addressBar.setText(url.replaceFirst("^http://", ""));
    browser.loadUrl(url.replaceFirst("^(?![^:]+://)", "http://"));
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  private void teleport(String url, String domain, String cookies) {
    teleportTask = new TeleportTask(this);
    teleportTask.execute(new String[] { url, domain, cookies });
  }

  @Override
  public boolean onMenuItemSelected(int featureId, MenuItem item) {
    if (item.getItemId() == R.id.action_teleport) {
      String url = browser.getUrl();
      if (url != null) {
        CookieManager cm = CookieManager.getInstance();
        HashSet<String> allCookies = new HashSet<String>();

        // IMPORTANT we cannot get the response headers that set the cookies.
        // Therefore we have to assume certain details such as whether the
        // cookies apply to subdomains, etc. But more crucial is our inability
        // to know whether the login process set cookies on other domains before
        // redirecting the user to the current page. This should be rare but if
        // it happens, we miss it.
        // So we will walk up the (sub)domain hierarchy until we can't find any
        // cookies.
        // We the collect the total set of all cookies encountered and teleport
        // them.

        Matcher m = DOMAIN_PAT.matcher(url);
        if (!m.find()) {
          Toast.makeText(this, getString(R.string.only_http),
              Toast.LENGTH_SHORT).show();
          return super.onMenuItemSelected(featureId, item);
        }
        String cookieDomain = null;
        String curDomain = m.group(2);
        String curCookies = cm.getCookie(m.group(1) + curDomain + "/");

        while (curCookies != null) {

          for (String c : curCookies.split(";"))
            allCookies.add(c.trim());

          cookieDomain = curDomain;

          int dotIndex = curDomain.indexOf('.');
          dotIndex = dotIndex == -1 ? curDomain.length() : dotIndex;
          curDomain = curDomain.substring(dotIndex + 1);
          curCookies = cm.getCookie(m.group(1) + curDomain + "/");

        }

        if (cookieDomain != null && allCookies.size() > 0) {

          StringBuilder serCookies = new StringBuilder();
          for (String c : allCookies)
            serCookies.append(c + ";");
          if (allCookies.size() > 0)
            serCookies.deleteCharAt(serCookies.length() - 1);

          teleport(url, cookieDomain, serCookies.toString());
          return true;

        }

      }
      Toast.makeText(this, getString(R.string.no_session), Toast.LENGTH_SHORT)
          .show();
    }
    return super.onMenuItemSelected(featureId, item);
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (teleportTask != null)
      teleportTask.cancel(true);
    teleportTask = null;
  }

}
