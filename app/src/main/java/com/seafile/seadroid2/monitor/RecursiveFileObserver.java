package com.seafile.seadroid2.monitor;

import android.os.FileObserver;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Recursive monitoring of a directory.
 *
 * This works on a best-effort basis. If directories are created while this class is instanciated,
 * they might slip through. However, as the Service, which uses this class, is started early during
 * system boot, this should be safe.
 */
public class RecursiveFileObserver extends FileObserver {

    public interface FileObserverListener {
        void onFileChanged(RecursiveFileObserver observer, File file);
    }

    private static final String DEBUG_TAG = "RecursiveFileObserver";

    private FileObserverListener listener;
    private Map<File, RecursiveFileObserver> children = new HashMap();
    private File directory;
    private RecursiveFileObserver parent;

    /**
     * Events to listen to.
     */
    private final static int MASK = FileObserver.CREATE | FileObserver.DELETE |
            FileObserver.MOVED_FROM | FileObserver.MOVED_TO | FileObserver.MODIFY;

    public RecursiveFileObserver(FileObserverListener listener, File dir) {
        super(dir.getAbsolutePath(), MASK);

        this.listener = listener;
        this.directory = dir;

        startWatching();
    }

    private RecursiveFileObserver(RecursiveFileObserver parent, File dir) {
        super(dir.getAbsolutePath(), MASK);

        this.directory = dir;
        this.parent = parent;

        startWatching();
    }

    @Override
    public void startWatching() {
        if (!directory.isDirectory() || !directory.exists())
            return;

        for (File child: directory.listFiles()) {
            if (child.isDirectory())
                addChildDirectory(child);
        }
        super.startWatching();

        Log.i(DEBUG_TAG, "Started watching " + directory);
    }

    @Override
    public void stopWatching() {
        Log.i(DEBUG_TAG, "Stop watching "+directory);

        super.stopWatching();
        for (RecursiveFileObserver child: children.values()) {
            child.stopWatching();
        }
        children.clear();
    }

    private void addChildDirectory(File dir) {
        RecursiveFileObserver child = new RecursiveFileObserver(this, dir);
        children.put(dir, child);
    }

    private void handleFileEvent(File file) {
        if (parent != null) {
            // hand this event down until it reaches the original observer
            parent.handleFileEvent(file);
        } else {
            listener.onFileChanged(this, file);
        }
    }

    @Override
    public void onEvent(int event, String path) {
        // reset undefined high bits
        event = event & FileObserver.ALL_EVENTS;

        Log.d(DEBUG_TAG, "onEvent: "+path+"; "+event);


        if (path == null)
            return;

        File file = new File(directory, path);

        switch (event) {
            case FileObserver.MOVED_TO:
            case FileObserver.MODIFY:
                if (!file.isDirectory()) {
                    handleFileEvent(file);
                }
                // fall through
            case FileObserver.CREATE:
                if (file.isDirectory()) {
                    addChildDirectory(file);
                }
                break;
            case FileObserver.DELETE:
            case FileObserver.MOVED_FROM:
                if (file.isDirectory()) {
                    RecursiveFileObserver child = children.remove(file);
                    if (child != null)
                        child.stopWatching();
                }
                break;
        }
    }
}
