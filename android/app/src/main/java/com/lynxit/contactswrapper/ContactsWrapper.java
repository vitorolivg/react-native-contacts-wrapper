package com.lynxit.contactswrapper;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;

import java.util.HashMap;

public class ContactsWrapper extends ReactContextBaseJavaModule implements ActivityEventListener {

    private static final int CONTACT_REQUEST = 1;
    private static final int EMAIL_REQUEST = 2;
    private static final String E_CONTACT_CANCELLED = "E_CONTACT_CANCELLED";
    private static final String E_CONTACT_NO_DATA = "E_CONTACT_NO_DATA";
    private static final String E_CONTACT_NO_EMAIL = "E_CONTACT_NO_EMAIL";
    private static final String E_CONTACT_EXCEPTION = "E_CONTACT_EXCEPTION";
    private Promise mContactsPromise;
    private Activity mCtx;


    public ContactsWrapper(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "ContactsWrapper";
    }


    @ReactMethod
    public void getContact(Promise contactsPromise) {
        launchPicker(contactsPromise, CONTACT_REQUEST);
    }

    @ReactMethod
    public void getEmail(Promise contactsPromise) {
        launchPicker(contactsPromise, EMAIL_REQUEST);
    }

    /**
     * Lanch the contact picker, with the specified requestCode for returned data.
     *
     * @param contactsPromise - promise passed in from React Native.
     * @param requestCode     - request code to specify what contact data to return
     */
    private void launchPicker(Promise contactsPromise, int requestCode) {
        mContactsPromise = contactsPromise;
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
        mCtx = getCurrentActivity();
        if (intent.resolveActivity(mCtx.getPackageManager()) != null) {
            mCtx.startActivityForResult(intent, requestCode);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (mContactsPromise == null || mCtx == null
                || (requestCode != CONTACT_REQUEST && requestCode != EMAIL_REQUEST)) {
            return;
        }

        String email = null;
        switch (resultCode) {
            case (Activity.RESULT_OK):
                Uri contactUri = intent.getData();
                switch (requestCode) {
                    case (CONTACT_REQUEST):
                        try {
                            /* Retrieve all possible data about contact and return as a JS object */

                            /* Map Any contact data we want returned to the JS object key for React Native */
                            HashMap<String, String> returnKeys = new HashMap<String, String>();
                            returnKeys.put(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, "name");
                            returnKeys.put(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, "phone");
                            returnKeys.put(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, "email");
                            // Requesting contact photo
                            returnKeys.put(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE, "photo");

                            //First get ID
                            String id = null;
                            int idx;
                            WritableMap contactData = Arguments.createMap();
                            Cursor cursor = mCtx.getContentResolver().query(contactUri, null, null, null, null);
                            if (cursor.moveToFirst()) {
                                idx = cursor.getColumnIndex(ContactsContract.Contacts._ID);
                                id = cursor.getString(idx);
                            } else {
                                mContactsPromise.reject(E_CONTACT_NO_DATA, "Contact Data Not Found");
                                return;
                            }

                            // Build the Entity URI.
                            Uri.Builder b = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id).buildUpon();
                            b.appendPath(ContactsContract.Contacts.Entity.CONTENT_DIRECTORY);
                            contactUri = b.build();

                            // Create the projection (SQL fields) and sort order.
                            String[] projection = {
                                    ContactsContract.Contacts.Entity.MIMETYPE,
                                    ContactsContract.Contacts.Entity.DATA1
                            };
                            String sortOrder = ContactsContract.Contacts.Entity.RAW_CONTACT_ID + " ASC";
                            cursor = mCtx.getContentResolver().query(contactUri, projection, null, null, sortOrder);

                            String mime;
                            boolean foundData = false;
                            int dataIdx = cursor.getColumnIndex(ContactsContract.Contacts.Entity.DATA1);
                            int mimeIdx = cursor.getColumnIndex(ContactsContract.Contacts.Entity.MIMETYPE);
                            if (cursor.moveToFirst()) {
                                do {
                                    mime = cursor.getString(mimeIdx);
                                    if (returnKeys.containsKey(mime)) {
                                        contactData.putString((String) returnKeys.get(mime), cursor.getString(dataIdx));
                                        foundData = true;
                                    }
                                } while (cursor.moveToNext());
                            }

                            if (foundData) {
                                mContactsPromise.resolve(contactData);
                                return;
                            } else {
                                mContactsPromise.reject(E_CONTACT_NO_DATA, "No data found for contact");
                                return;
                            }
                        } catch (Exception e) {
                            mContactsPromise.reject(E_CONTACT_EXCEPTION, e.getMessage());
                            return;
                        }
                        /* No need to break as all paths return */
                    case (EMAIL_REQUEST):
                        /* Return contacts first email address, as string */
                        try {


                            // get the contact id from the Uri
                            String id = contactUri.getLastPathSegment();

                            // query for everything email
                            Cursor cursor = mCtx.getContentResolver().query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                                    null, ContactsContract.CommonDataKinds.Email.CONTACT_ID + "=?", new String[]{id},
                                    null);

                            int emailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA);

                            // For now, return only the first email address, as a string
                            if (cursor.moveToFirst()) {
                                email = cursor.getString(emailIdx);
                                mContactsPromise.resolve(email);
                                return;
                            } else {
                                //Contact has no email address stored
                                mContactsPromise.reject(E_CONTACT_NO_EMAIL, "No email found for contact");
                                return;
                            }
                        } catch (Exception e) {
                            mContactsPromise.reject(E_CONTACT_EXCEPTION, e.getMessage());
                            return;
                        }
                        /* No need to break as all paths return */
                    default:
                        //Unexpected return code - shouldn't happen, but catch just in case
                        mContactsPromise.reject(E_CONTACT_EXCEPTION, "Unexpected error in request");
                        return;
                }
            default:
                //Request was cancelled
                mContactsPromise.reject(E_CONTACT_CANCELLED, "Cancelled");
                return;
        }
    }

    public void onNewIntent(Intent intent) {

    }
}
