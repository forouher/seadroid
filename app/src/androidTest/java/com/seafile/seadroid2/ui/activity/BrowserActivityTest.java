package com.seafile.seadroid2.ui.activity;

import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.After;
import org.junit.Before;

public class BrowserActivityTest
        extends ActivityInstrumentationTestCase2<BrowserActivity> {

    private BrowserActivity mBrowserActivity;

    public BrowserActivityTest() {
        super(BrowserActivity.class);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mBrowserActivity = getActivity();
    }

    @SmallTest
    public void testPreconditions() {
        assertNotNull(mBrowserActivity);
    }

}