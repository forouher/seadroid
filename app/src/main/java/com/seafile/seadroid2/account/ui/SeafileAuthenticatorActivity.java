package com.seafile.seadroid2.account.ui;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.seafile.seadroid2.R;
import com.seafile.seadroid2.account.Authenticator;
import com.seafile.seadroid2.cameraupload.CameraUploadManager;
import com.seafile.seadroid2.ui.BaseAuthenticatorActivity;

/**
 * The Authenticator activity.
 *
 * Called by the Authenticator and in charge of identifing the user.
 *
 * It sends back to the Authenticator the result.
 */
public class SeafileAuthenticatorActivity extends BaseAuthenticatorActivity {

    public static final int SEACLOUD_CC = 0;
    public static final int CLOUD_SEAFILE_COM = 1;
    public static final int SHIBBOLETH_LOGIN = 2;
    public static final int OTHER_SERVER = 3;

    public final static String ARG_ACCOUNT_TYPE = "ACCOUNT_TYPE";
    public final static String ARG_ACCOUNT_NAME = "ACCOUNT_NAME";
    public final static String ARG_SERVER_URI = "SERVER_URI";
    public final static String ARG_EDIT_OLD_ACCOUNT_NAME = "EDIT_OLD_ACCOUNT";
    public final static String ARG_EMAIL = "EMAIL";
    public final static String ARG_IS_EDITING = "isEdited";

    private static final int REQ_SIGNUP = 1;

    private final String DEBUG_TAG = this.getClass().getSimpleName();

    private AccountManager mAccountManager;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(DEBUG_TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_create_type_select);

        String[] array = getResources().getStringArray(R.array.choose_server_array);
        ArrayAdapter<String> listAdapter = new ArrayAdapter<>(this, R.layout.list_item_authenticator, array);

        ListView listView = (ListView)findViewById(R.id.account_create_list);
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent;
                switch ((int) id) {
                    case SEACLOUD_CC:
                        intent = new Intent(SeafileAuthenticatorActivity.this, AccountDetailActivity.class);
                        intent.putExtras(getIntent());
                        intent.putExtra(SeafileAuthenticatorActivity.ARG_SERVER_URI, "https://seacloud.cc");
                        startActivityForResult(intent, SeafileAuthenticatorActivity.REQ_SIGNUP);
                        break;
                    case CLOUD_SEAFILE_COM:
                        intent = new Intent(SeafileAuthenticatorActivity.this, AccountDetailActivity.class);
                        intent.putExtras(getIntent());
                        intent.putExtra(SeafileAuthenticatorActivity.ARG_SERVER_URI, "https://cloud.seafile.de");
                        startActivityForResult(intent, SeafileAuthenticatorActivity.REQ_SIGNUP);
                        break;
                    case SHIBBOLETH_LOGIN:
                        intent = new Intent(SeafileAuthenticatorActivity.this, ShibbolethActivity.class);
                        intent.putExtras(getIntent());
                        startActivityForResult(intent, SeafileAuthenticatorActivity.REQ_SIGNUP);
                        break;
                    case OTHER_SERVER:
                        intent = new Intent(SeafileAuthenticatorActivity.this, AccountDetailActivity.class);
                        intent.putExtras(getIntent());
                        startActivityForResult(intent, SeafileAuthenticatorActivity.REQ_SIGNUP);
                        break;
                    default:
                        return;
                }
            }
        });

        mAccountManager = AccountManager.get(getBaseContext());

        if (getIntent().getBooleanExtra(ARG_IS_EDITING, false)) {
            Intent intent = new Intent(this, AccountDetailActivity.class);
            intent.putExtras(getIntent().getExtras());
            startActivityForResult(intent, SeafileAuthenticatorActivity.REQ_SIGNUP);
        }

        Toolbar toolbar = getActionBarToolbar();
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.choose_server);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(DEBUG_TAG, "navigateUpOrBack");
                navigateUpOrBack(SeafileAuthenticatorActivity.this, null);
            }
        });
        Log.d(DEBUG_TAG, "onCreate finished");

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(DEBUG_TAG, "onActivityResult: "+data);
        if (data != null) {
            Log.d(DEBUG_TAG, "onActivityResult data: "+data.getExtras());
        }

        // The sign up activity returned that the user has successfully created an account
        if (requestCode == REQ_SIGNUP && resultCode == RESULT_OK) {
            Log.d(DEBUG_TAG, "onActivityResult: ok");

            finishLogin(data);
        } else {
            Log.d(DEBUG_TAG, "onActivityResult: "+requestCode+"/"+resultCode);
            finish();
        }
    }

    private void finishLogin(Intent intent) {
        Log.d(DEBUG_TAG, "finishLogin");

        String newAccountName = intent.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
        final Account newAccount = new Account(newAccountName, intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE));

        String authtoken = intent.getStringExtra(AccountManager.KEY_AUTHTOKEN);
        String serveruri = intent.getStringExtra(ARG_SERVER_URI);
        String email = intent.getStringExtra(ARG_EMAIL);

        Log.d(DEBUG_TAG, "finishLogin: "+serveruri+"/"+email);

        int cameraIsSyncable = 0;
        boolean cameraSyncAutomatically = true;

        if (intent.getBooleanExtra(ARG_IS_EDITING, false)) {
            Log.d(DEBUG_TAG, "finishLogin: editing existing account");

            String oldAccountName = intent.getStringExtra(ARG_EDIT_OLD_ACCOUNT_NAME);
            final Account oldAccount = new Account(oldAccountName, intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE));

            // serverUri and mail stay the same. so just update the token and exit
            if (oldAccount.equals(newAccount)) {
                Log.d(DEBUG_TAG, "finishLogin: only token changed");

                mAccountManager.setAuthToken(newAccount, Authenticator.AUTHTOKEN_TYPE, authtoken);

                Bundle result = new Bundle();
                result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
                result.putString(AccountManager.KEY_ACCOUNT_NAME, newAccountName);
                setAccountAuthenticatorResult(result);
                setResult(RESULT_OK, intent);
                finish();
                return;
            }

            Log.d(DEBUG_TAG, "removing old account " + oldAccountName);

            cameraIsSyncable = ContentResolver.getIsSyncable(oldAccount, CameraUploadManager.AUTHORITY);
            cameraSyncAutomatically = ContentResolver.getSyncAutomatically(oldAccount, CameraUploadManager.AUTHORITY);

            mAccountManager.removeAccount(oldAccount, null, null);
        }

        Log.d(DEBUG_TAG, "adding new account " + newAccountName);
        boolean res = mAccountManager.addAccountExplicitly(newAccount, null, null);
        Log.d(DEBUG_TAG, "adding new account result="+res);

        Log.d(DEBUG_TAG, "setting auth token");
        mAccountManager.setAuthToken(newAccount, Authenticator.AUTHTOKEN_TYPE, authtoken);
        Log.d(DEBUG_TAG, "setting user data");
        mAccountManager.setUserData(newAccount, Authenticator.KEY_SERVER_URI, serveruri);
        mAccountManager.setUserData(newAccount, Authenticator.KEY_EMAIL, email);

        // set sync settings

        Log.d(DEBUG_TAG, "setting is syncable");
        ContentResolver.setIsSyncable(newAccount, CameraUploadManager.AUTHORITY, cameraIsSyncable);
        Log.d(DEBUG_TAG, "setting sync auto");
        ContentResolver.setSyncAutomatically(newAccount, CameraUploadManager.AUTHORITY, cameraSyncAutomatically);

        Log.d(DEBUG_TAG, "finishing");
        Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
        result.putString(AccountManager.KEY_ACCOUNT_NAME, newAccountName);
        setAccountAuthenticatorResult(result);
        setResult(RESULT_OK, intent);
        finish();
    }
}