package com.seafile.seadroid2.account;

import android.os.Parcel;

import com.seafile.seadroid2.BuildConfig;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class AccountTest {

    @org.junit.Test
    public void testValidToken() throws Exception {
        Account a = new Account("server", "test@example.com", null);
        assertFalse(a.hasValidToken());
    }

    @org.junit.Test
    public void testValidToken2() throws Exception {
        Account a = new Account("server", "test@example.com", "346734068786037");
        assertTrue(a.hasValidToken());
    }

    @org.junit.Test
    public void testEquals() throws Exception {
        Account a = new Account("server", "test@example.com", "346734068786037");
        Account b = new Account("server", "test@example.com", "w5785553");
        assertEquals(a, b);
    }
    @org.junit.Test
    public void testEquals2() throws Exception {
        Account a = new Account("server", "test@example.com", "346734068786037");
        Account b = new Account("server2", "test@example.com", "w5785553");
        assertNotEquals(a, b);
    }

    @org.junit.Test
    public void testEquals3() throws Exception {
        Account a = new Account("server", "test@example.com", "346734068786037");
        Account b = new Account("server", "test2@example.com", "w5785553");
        assertNotEquals(a, b);
    }

    @org.junit.Test
    public void testParcel() throws Exception {
        Account a = new Account("server", "test@example.com", "346734068786037");

        Parcel p1 = Parcel.obtain();
        Parcel p2 = Parcel.obtain();
        byte[] bytes;
        Account b;

        try {
            p1.writeValue(a);
            bytes = p1.marshall();

            p2.unmarshall(bytes, 0, bytes.length);
            p2.setDataPosition(0);
            b = (Account) p2.readValue(Account.class.getClassLoader());

        } finally {
            p1.recycle();
            p2.recycle();
        }

        assertEquals(a, b);
        assertEquals(a.getToken(), b.getToken());
    }

}