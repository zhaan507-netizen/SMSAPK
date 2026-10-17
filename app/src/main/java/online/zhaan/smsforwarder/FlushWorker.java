package online.zhaan.smsforwarder;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;

public class FlushWorker extends Worker {
    public FlushWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        List<String[]> items = Store.all(c);
        if (items.isEmpty()) return Result.success();
        boolean failed = false;
        for (String[] item : items) {
            try {
                int code = post(Store.url(c), Store.secret(c), item[1]);
                if (code == 200) {
                    Store.remove(c, item[0]);
                    Store.setLast(c, "sent OK (200)");
                } else {
                    failed = true;
                    Store.setLast(c, "server answered " + code + " - will retry");
                }
            } catch (IOException e) {
                failed = true;
                Store.setLast(c, "no connection (" + e.getClass().getSimpleName() + ") - will retry");
            }
        }
        return failed ? Result.retry() : Result.success();
    }

    private static int post(String base, String secret, String text) throws IOException {
        String sep = base.contains("?") ? "&" : "?";
        URL url = new URL(base + sep + "text=" + URLEncoder.encode(text, "UTF-8"));
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        try {
            con.setRequestMethod("POST");
            con.setConnectTimeout(15000);
            con.setReadTimeout(20000);
            con.setInstanceFollowRedirects(false);
            con.setRequestProperty("X-secret", secret);
            con.setDoOutput(true);
            con.setFixedLengthStreamingMode(0);
            con.getOutputStream().close();
            return con.getResponseCode();
        } finally {
            con.disconnect();
        }
    }
}
