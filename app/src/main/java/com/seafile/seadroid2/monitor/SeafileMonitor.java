package com.seafile.seadroid2.monitor;

import android.os.AsyncTask;
import android.util.Log;

import com.google.common.collect.Maps;
import com.seafile.seadroid2.SeadroidApplication;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.account.AccountManager;
import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.util.ConcurrentAsyncTask;
import com.seafile.seadroid2.util.Utils;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SeafileMonitor implements RecursiveFileObserver.FileObserverListener {

    private static final String DEBUG_TAG = "SeafileMonitor";

    // delay all uploads at least by this period to avoid premature uploads
    // (must be less than RECENT_DOWNLOAD_BLIND_PERIOD)
    private static final int DELAY_UPLOAD_PERIOD = 5 * 1000;

    // after a file was downloaded by seadroid, we have to ignore this file for some seconds
    private static final int RECENT_DOWNLOAD_BLIND_PERIOD = 10 * 1000;

    private Map<RecursiveFileObserver, Account> observers = new HashMap();
    private CachedFileChangedListener listener;

    private final RecentDownloadedFilesWorkAround recentDownloadedFiles =
            new RecentDownloadedFilesWorkAround();


    public SeafileMonitor(CachedFileChangedListener listener) {
        this.listener = listener;
    }

    public synchronized void start() {
        Log.i(DEBUG_TAG, "Start monitoring");
        monitorAllAccounts();
    }

    public synchronized void stop() {
        for (RecursiveFileObserver observer : observers.keySet()) {
            observer.stopWatching();
        }
        observers.clear();
    }

    /**
     * Watch cached files for all accounts
     */
    private synchronized void monitorAllAccounts() {
        List<Account> accounts =
                new AccountManager(SeadroidApplication.getAppContext()).getAccountList();

        for (Account account : accounts) {
            DataManager dm = new DataManager(account);
            File base = new File(dm.getAccountDir());
            base.mkdirs();
            RecursiveFileObserver subDirObserver = new RecursiveFileObserver(this, base);
            observers.put(subDirObserver, account);
        }
    }

    @Override
    public void onFileChanged(RecursiveFileObserver observer, File file) {
        if (recentDownloadedFiles.isRecentDownloadedFiles(file)) {
            Log.d(DEBUG_TAG, "ignore change signal for recent downloaded file " + file);
            return;
        }

        /* If Seadroid has downloaded a new file, this will be called before onFileDownloaded()
         * gets called. So we wait a bit.
         *
         * Also we should avoid uploading files that are still being modified.
         * So only upload files that are at stable for least DELAY_UPLOAD_PERIOD.
         * Otherwise wait a bit.
         */
        if (Math.abs(System.currentTimeMillis() - file.lastModified()) < DELAY_UPLOAD_PERIOD) {
            ConcurrentAsyncTask.execute(new DelayUpload(observer),file);
            return;
        }

        Account account = observers.get(observer);
        listener.onCachedFileChanged(account, file);
    }

    public void onFileDownloaded(File file) {
        Log.d(DEBUG_TAG, "got info that file was recently downloaded: "+file);

        recentDownloadedFiles.addRecentDownloadedFile(file);
    }

    /**
     * When user downloads a file, the outdated file is replaced, so the onFileChange signal would
     * be triggered, which we should not treat it as a modification. This class provides a workaround
     * for this.
     */
    private static class RecentDownloadedFilesWorkAround {
        private final Map<File, Long> recentDownloadedFiles = Maps.newConcurrentMap();

        public boolean isRecentDownloadedFiles(File file) {
            Long timestamp = recentDownloadedFiles.get(file);
            if (timestamp != null) {
                long timeWhenDownloaded = timestamp;
                long now = Utils.now();

                if (now - timeWhenDownloaded < RECENT_DOWNLOAD_BLIND_PERIOD) {
                    return true;
                }
            }

            return false;
        }

        public void addRecentDownloadedFile(File file) {
            recentDownloadedFiles.put(file, Utils.now());
        }
   }

    class DelayUpload extends AsyncTask<File, Void, Void> {

        private RecursiveFileObserver observer;

        public DelayUpload(RecursiveFileObserver observer) {
            this.observer = observer;
        }

        @Override
        protected Void doInBackground(File... params) {
            try {
                Thread.sleep(DELAY_UPLOAD_PERIOD);
            } finally {
                onFileChanged(observer, params[0]);
                return null;
            }
        }
    }
}
