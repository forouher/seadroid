package com.seafile.seadroid2.ui.adapter;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.PagerAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.google.common.collect.Lists;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.nostra13.universalimageloader.core.listener.ImageLoadingProgressListener;
import com.seafile.seadroid2.transfer.DownloadTaskInfo;
import com.seafile.seadroid2.transfer.DownloadTaskManager;
import com.seafile.seadroid2.transfer.TransferManager;
import com.seafile.seadroid2.transfer.TransferService;
import com.seafile.seadroid2.R;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.data.SeafPhoto;
import com.seafile.seadroid2.ui.activity.GalleryActivity;
import com.seafile.seadroid2.util.Utils;
import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gallery Adapter
 */
public class GalleryAdapter extends PagerAdapter {
    public static final String DEBUG_TAG = "GalleryAdapter";

    public Map<Integer, View> task2view = new HashMap();
    private ArrayList<PendingDownloadInfo> pendingDownloads = Lists.newArrayList();

    private GalleryActivity mActivity;
    private List<SeafPhoto> seafPhotos;
    private LayoutInflater inflater;
    private DisplayImageOptions options;
    private Account mAccount;
    private DataManager dm;

    private class PendingDownloadInfo {
        public View view;
        public String repoID;
        public String repoName;
        public String filePath;
    }

    TransferService txService = null;

    ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {

            TransferService.TransferBinder binder = (TransferService.TransferBinder) service;
            txService = binder.getService();
            Log.d(DEBUG_TAG, "connected to TransferService");

            for (PendingDownloadInfo info : pendingDownloads) {
                int taskID = txService.addDownloadTask(mAccount,
                        info.repoName,
                        info.repoID,
                        info.filePath);
                task2view.put(taskID, info.view);
            }
            pendingDownloads.clear();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            // this will run in a foreign thread!
            Log.d(DEBUG_TAG, "disconnected from TransferService");
            txService = null;
        }
    };

    public GalleryAdapter(GalleryActivity context, Account account,
                          List<SeafPhoto> photos, DataManager dataManager) {
        mActivity = context;
        seafPhotos = photos;
        inflater = context.getLayoutInflater();
        options = new DisplayImageOptions.Builder()
                .showImageForEmptyUri(R.drawable.ic_gallery_empty2)
                .showImageOnFail(R.drawable.gallery_loading_failed)
                .cacheInMemory(true)
                .cacheOnDisk(true)
                .considerExifParams(true)
                .extraForDownloader(account)
                .build();
        mAccount = account;
        dm = dataManager;

        Intent bIntent = new Intent(context, TransferService.class);
        context.bindService(bIntent, mConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(context).registerReceiver(transferReceiver,
                new IntentFilter(TransferManager.BROADCAST_ACTION));

    }

    @Override
    public int getCount() {
        return seafPhotos.size();
    }

    public void setItems(List<SeafPhoto> photos) {
        seafPhotos = photos;
    }

    @Override
    public View instantiateItem(ViewGroup container, final int position) {
        View contentView = inflater.inflate(R.layout.gallery_view_item, container, false);
        final PhotoView photoView = (PhotoView) contentView.findViewById(R.id.gallery_photoview);
        final ProgressBar progressBar = (ProgressBar) contentView.findViewById(R.id.gallery_progress_bar);
        final String repoName = seafPhotos.get(position).getRepoName();
        final String repoID = seafPhotos.get(position).getRepoID();
        final String filePath = Utils.pathJoin(seafPhotos.get(position).getDirPath(),
                seafPhotos.get(position).getName());
        final File file = dm.getLocalRepoFile(repoName, repoID, filePath);
        if (file.exists()) {
            ImageLoader.getInstance().displayImage("file://" + file.getAbsolutePath().toString(), photoView, options);
        } else {
            if (txService != null) {
                int taskID = txService.addDownloadTask(mAccount, repoName, repoID, filePath);
                task2view.put(taskID, contentView);
            } else {
                PendingDownloadInfo info = new PendingDownloadInfo();
                info.filePath = filePath;
                info.repoID = repoID;
                info.repoName = repoName;
                info.view = contentView;
                pendingDownloads.add(info);
            }
            progressBar.setVisibility(View.VISIBLE);
        }

        photoView.setOnPhotoTapListener(new PhotoViewAttacher.OnPhotoTapListener() {
            @Override
            public void onPhotoTap(View view, float x, float y) {
                mActivity.hideOrShowToolBar();
            }
        });

        container.addView(contentView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        return contentView;
    }

    /**
     * when you call notifyDataSetChanged(),
     * the view pager will remove all views and reload them all.
     * As so the reload effect is obtained.
     */
    @Override
    public int getItemPosition(Object object) {
        return POSITION_NONE;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View) object);
    }

    private void onFileDownloaded(int taskID) {
        View contentView = task2view.remove(taskID);
        if (contentView == null)
            return;

        final PhotoView photoView = (PhotoView) contentView.findViewById(R.id.gallery_photoview);
        final ProgressBar progressBar = (ProgressBar) contentView.findViewById(R.id.gallery_progress_bar);
        DownloadTaskInfo info = txService.getDownloadTaskInfo(taskID);

        ImageLoader.getInstance().displayImage("file://" + info.localFilePath,
                photoView,
                options,
                new ImageLoadingListener() {
                    @Override
                    public void onLoadingStarted(String s, View view) {
                        //Log.d(DEBUG_TAG, "ImageLoadingListener >> onLoadingStarted");
                        progressBar.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onLoadingFailed(String s, View view, FailReason failReason) {
                        //Log.d(DEBUG_TAG, "ImageLoadingListener >> onLoadingFailed");
                        progressBar.setVisibility(View.INVISIBLE);
                    }

                    @Override
                    public void onLoadingComplete(String s, View view, Bitmap bitmap) {
                        //Log.d(DEBUG_TAG, "ImageLoadingListener >> onLoadingComplete");
                        progressBar.setVisibility(View.INVISIBLE);
                    }

                    @Override
                    public void onLoadingCancelled(String s, View view) {
                        //Log.d(DEBUG_TAG, "ImageLoadingListener >> onLoadingCancelled");
                        progressBar.setVisibility(View.INVISIBLE);

                    }
                }, new ImageLoadingProgressListener() {
                    @Override
                    public void onProgressUpdate(String s, View view, int i, int i1) {
                        // There isn`t any way to get the actual loading bytes and total bytes with which
                        // could show the progress bar with precise percent
                        // see https://github.com/nostra13/Android-Universal-Image-Loader/issues/402 for details
                        progressBar.setVisibility(View.VISIBLE);
                    }
                });
    }

    public void onFileDownloadFailed(int taskID) {
        View contentView = task2view.remove(taskID);
        if (contentView == null)
            return;

        final PhotoView photoView = (PhotoView) contentView.findViewById(R.id.gallery_photoview);
        final ProgressBar progressBar = (ProgressBar) contentView.findViewById(R.id.gallery_progress_bar);

        progressBar.setVisibility(View.INVISIBLE);
        ImageLoader.getInstance().displayImage("drawable://" + R.drawable.gallery_loading_failed, photoView, options);
    }


    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    private BroadcastReceiver transferReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (txService == null) {
                return;
            }

            String type = intent.getStringExtra("type");
            if (type == null) {
                return;
            }

            if (type.equals(DownloadTaskManager.BROADCAST_FILE_DOWNLOAD_SUCCESS)) {
                int taskID = intent.getIntExtra("taskID", 0);
                onFileDownloaded(taskID);
            } else if (type.equals(DownloadTaskManager.BROADCAST_FILE_DOWNLOAD_FAILED)) {
                int taskID = intent.getIntExtra("taskID", 0);
                onFileDownloadFailed(taskID);
            }
        }
    };
}
