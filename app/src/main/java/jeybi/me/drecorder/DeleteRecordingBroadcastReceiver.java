package jeybi.me.drecorder;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;

import static android.content.Context.NOTIFICATION_SERVICE;

public final class DeleteRecordingBroadcastReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
    notificationManager.cancel(RecordingSession.Companion.getNOTIFICATION_ID());

    final Uri uri = intent.getData();
    final ContentResolver contentResolver = context.getContentResolver();
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(@NonNull Void... none) {
        int rowsDeleted = contentResolver.delete(uri, null, null);
        if (rowsDeleted == 1) {
        } else {
        }
        return null;
      }
    }.execute();
  }
}
