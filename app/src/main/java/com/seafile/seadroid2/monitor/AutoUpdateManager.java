package com.seafile.seadroid2.monitor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.os.Handler;
import android.util.Log;

import com.google.common.collect.Sets;
import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.util.ConcurrentAsyncTask;
import com.seafile.seadroid2.SeafException;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.data.SeafCachedFile;
import com.seafile.seadroid2.transfer.TransferService;
import com.seafile.seadroid2.util.Utils;

/**
 * Update modified files, retry until success
 */
public class AutoUpdateManager implements CachedFileChangedListener {
    private static final String DEBUG_TAG = "AutoUpdateManager";

    private TransferService txService;
    private final Handler mHandler = new Handler();

    private Set<AutoUpdateInfo> infos = Sets.newHashSet();
    private MonitorDBHelper db = MonitorDBHelper.getMonitorDBHelper();

    public void onTransferServiceConnected(TransferService txService) {
        this.txService = txService;

        // TODO: this was happening in another thread
        synchronized (infos) {
            infos.addAll(db.getAutoUploadInfos());
        }
    }

    /**
     * This method is called by file monitor, so it would be executed in the file monitor thread
     */
    @Override
    public void onCachedFileChanged(final Account account, final File localFile) {
        Log.d(DEBUG_TAG, "onCachedFileChanged for "+localFile);
        DataManager dataManager = new DataManager(account);
        final SeafCachedFile cachedFile = dataManager.lookupSeafCachedFile(localFile);
        if (cachedFile != null)
            addTask(account, cachedFile, localFile);
        else
            Log.d(DEBUG_TAG, "onCachedFileChanged not found in data base, ignoring file");
    }

    public void addTask(Account account, SeafCachedFile cachedFile, File localFile) {
        AutoUpdateInfo info = new AutoUpdateInfo(account, cachedFile.repoID, cachedFile.repoName,
                Utils.getParentPath(cachedFile.path), localFile.getPath());

        synchronized (infos) {
            if (infos.contains(info)) {
                return;
            }
            infos.add(info);
        }

        db.saveAutoUpdateInfo(info);

        if (!Utils.isNetworkOn() || txService == null) {
            return;
        }

        ArrayList<AutoUpdateInfo> infosList = new ArrayList<AutoUpdateInfo>(1);
        infosList.add(info);
        addAllUploadTasks(infosList);
    }

    private void addAllUploadTasks(final List<AutoUpdateInfo> infos) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (AutoUpdateInfo info : infos) {
                    txService.addUploadTask(info.account, info.repoID, info.repoName,
                            info.parentDir, info.localPath, true, true);
                }
            }
        });
    }

    /**
     * This callback in called in the main thread when the transfer service broadcast is received
     */
    public void onFileUpdateSuccess(Account account, String repoID, String repoName,
            String parentDir, String localPath) {
        // This file has already been updated on server, so we abort auto update task
        if (removeAutoUpdateInfo(account, repoID, repoName, parentDir, localPath)) {
            Log.d(DEBUG_TAG, "auto updated " + localPath);
        }
    }

    public void onFileUpdateFailure(Account account, String repoID, String repoName,
            String parentDir, String localPath, SeafException e) {
        if (e.getCode() / 100 != 4) {
            return;
        }

        // This file has already been removed on server, so we abort the auto update task
        if (removeAutoUpdateInfo(account, repoID, repoName, parentDir, localPath)) {
            Log.d(DEBUG_TAG, String.format("failed to auto update %s, error %s", localPath, e));
        }
    }

    private boolean removeAutoUpdateInfo(Account account, String repoID, String repoName, String parentDir, String localPath) {
        final AutoUpdateInfo info = new AutoUpdateInfo(account, repoID, repoName, parentDir, localPath);
        boolean exist;

        synchronized (infos) {
            exist = infos.remove(info);
        }

        if (exist) {
            ConcurrentAsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    db.removeAutoUpdateInfo(info);
                }
            });
        }
        return exist;
    }
}
