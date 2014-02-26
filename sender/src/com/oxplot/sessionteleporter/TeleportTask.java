package com.oxplot.sessionteleporter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.AsyncTask;
import android.util.Base64;

public class TeleportTask extends AsyncTask<String[], Integer, String> {

  private final int KEY_ITER;
  private final int KEY_LEN;
  private final int SALT_LEN;
  private final int IV_LEN;
  private final int MAX_PORTAL_ID_LEN;
  private final char[] PWD_CHARS;
  private final int PWD_LEN;

  private ProgressDialog teleportWait;
  private Context context;

  public TeleportTask(Context context) {
    this.context = context;
    KEY_ITER = Integer.parseInt(context.getString(R.string.config_key_iter));
    KEY_LEN = Integer.parseInt(context.getString(R.string.config_key_len));
    SALT_LEN = Integer.parseInt(context.getString(R.string.config_salt_len));
    IV_LEN = Integer.parseInt(context.getString(R.string.config_iv_len));
    MAX_PORTAL_ID_LEN = Integer.parseInt(context
        .getString(R.string.config_max_portal_id_len));
    PWD_CHARS = context.getString(R.string.config_pass_chars).toCharArray();
    PWD_LEN = Integer.parseInt(context.getString(R.string.config_pass_len));
  }

  @Override
  protected String doInBackground(String[]... args) {
    String url = args[0][0];
    String domain = args[0][1];
    String cookies = args[0][2];

    String token = null;

    Random rnd = new SecureRandom();
    char[] password = new char[PWD_LEN];
    for (int i = 0; i < password.length; i++)
      password[i] = PWD_CHARS[rnd.nextInt(PWD_CHARS.length)];

    try {
      String encrypted = encrypt(url + '\n' + domain + "\n" + cookies,
          new String(password));
      String portalRes = upload(encrypted);
      token = new String(password) + '/' + portalRes;

    } catch (Exception e1) {
      // XXX got fed up with 100 lines here catching various crypto exceptions
      e1.printStackTrace();
    }

    return token;
  }

  @Override
  protected void onPreExecute() {
    teleportWait = new ProgressDialog(context);
    teleportWait.setCancelable(true);
    teleportWait.setIndeterminate(true);
    teleportWait.setMessage(context.getString(R.string.teleport_wait));
    teleportWait.setOnCancelListener(new OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        TeleportTask.this.cancel(true);
      }
    });
    teleportWait.show();

  }

  @Override
  protected void onCancelled(String result) {
    teleportWait.dismiss();
  }

  private void showMessage(String title, String msg) {
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setMessage(msg).setCancelable(false).setTitle(title)
        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int id) {
          }
        });
    AlertDialog alert = builder.create();
    alert.show();
  }

  private String encrypt(String msg, String password) throws Exception {

    Random rnd = new SecureRandom();
    byte[] salt = new byte[SALT_LEN];
    byte[] iv = new byte[IV_LEN];
    for (int i = 0; i < salt.length; i++)
      salt[i] = (byte) (rnd.nextInt(256) - 128);
    for (int i = 0; i < iv.length; i++)
      iv[i] = (byte) (rnd.nextInt(256) - 128);

    SecretKey secret = new SecretKeySpec(PBKDF2.deriveKey(
        password.getBytes("UTF-8"), salt, KEY_ITER, KEY_LEN / 8), "AES");
    Cipher cipher = Cipher.getInstance("AES/CCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, secret, new IvParameterSpec(iv));
    byte[] ciphertext = cipher.doFinal(msg.getBytes("UTF8"));

    // SJCL compatible JSON message parameters

    return "{\"ct\":\"" + Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        + "\",\"iv\":\"" + Base64.encodeToString(iv, Base64.NO_WRAP)
        + "\",\"salt\":\"" + Base64.encodeToString(salt, Base64.NO_WRAP)
        + "\",\"ks\":" + KEY_LEN + ",\"iter\":" + KEY_ITER
        + ",\"ts\":64,\"mode\":\"ccm\",\"adata\":\"\",\"cipher\":\"aes\"}";

  }

  private String upload(String content) throws IOException {

    // XXX It's ridiculous how much code is needed to do this simple POST

    byte[] contentBytes = content.getBytes("UTF-8");
    String requestUrl = context.getString(R.string.config_portal_url);
    URL url = new URL(requestUrl);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setDoOutput(true);
    connection.setDoInput(true);
    connection.setInstanceFollowRedirects(false);
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", "text/html; charset=UTF-8");
    connection.setRequestProperty("Content-Length",
        "" + Integer.toString(contentBytes.length));
    connection.setUseCaches(false);

    DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
    wr.write(contentBytes);
    wr.flush();
    wr.close();
    DataInputStream re = new DataInputStream(connection.getInputStream());
    byte[] buffer = new byte[MAX_PORTAL_ID_LEN];
    int bytesRead = re.read(buffer, 0, buffer.length);
    int totalBytes = 0;
    while (bytesRead > 0 && totalBytes < buffer.length) {
      totalBytes += bytesRead;
      bytesRead = re.read(buffer, totalBytes, buffer.length - totalBytes);
    }
    re.close();
    return new String(buffer, 0, totalBytes, "UTF-8");
  }

  @Override
  protected void onPostExecute(String result) {
    teleportWait.dismiss();
    if (result == null) {
      showMessage(context.getString(R.string.enter_url),
          context.getString(R.string.teleport_failed));
    } else {
      showMessage(context.getString(R.string.enter_key), result);
    }
  }
}
